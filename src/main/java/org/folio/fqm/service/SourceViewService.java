package org.folio.fqm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.fqm.domain.SourceViewDefinition;
import org.folio.fqm.domain.SourceViewDefinition.SourceViewDependency;
import org.folio.fqm.domain.SourceViewRecord;
import org.folio.fqm.repository.SourceViewDatabaseObjectRepository;
import org.folio.fqm.repository.SourceViewRecordRepository;
import org.folio.fqm.utils.JSON5ObjectMapperFactory;
import org.folio.spring.FolioExecutionContext;
import org.jooq.exception.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

/**
 * Service class for interacting with source views at a higher level.
 *
 * The main operating principle here is that each view database object gets a corresponding {@link SourceViewRecord}
 * in the source_views table. Installation is handled by {@link #installAvailableSourceViews(String, boolean)}
 * (with the option to forcibly recreate all existing ones).
 *
 * These records should not get out of sync with the actual underlying database objects in normal usage,
 * however, some system operations (such as data migration or module (re)installation) can cause this to happen.
 * In cases where our records are suspected to be out of sync, {@link #verifyAll(String)} should be used
 * to ensure consistency. If the discrepancies are suspected to be limited to a single source view,
 * the {@link #attemptToHealSourceView(String, String)} method may be used instead.
 */
@Log4j2
@Service
public class SourceViewService {

  protected static final String MOD_FINANCE_AVAILABILITY_INDICATOR_VIEW =
    "_mod_finance_storage_exchange_rate_availability_indicator";

  private final SourceViewDatabaseObjectRepository sourceViewDatabaseObjectRepository;
  private final SourceViewRecordRepository sourceViewRecordRepository;

  private final FolioExecutionContext folioExecutionContext;

  private final ObjectMapper objectMapper;
  private final ResourcePatternResolver resourceResolver;

  @Autowired
  public SourceViewService(
    SourceViewDatabaseObjectRepository sourceViewDatabaseObjectRepository,
    SourceViewRecordRepository sourceViewRecordRepository,
    FolioExecutionContext folioExecutionContext,
    ResourcePatternResolver resourceResolver
  ) {
    this.sourceViewDatabaseObjectRepository = sourceViewDatabaseObjectRepository;
    this.sourceViewRecordRepository = sourceViewRecordRepository;
    this.folioExecutionContext = folioExecutionContext;
    this.resourceResolver = resourceResolver;

    this.objectMapper = JSON5ObjectMapperFactory.create();
  }

  /**
   * Check if a source view is available per our installation records.
   *
   * If a complex expression is provided (tested by presence of parentheses), we assume it exists,
   * rather than attempting to parse the SQL expression. This is necessary for some special ETs like
   * composite invoice lines/fund distributions, which self-reference other sources like:
   * `jsonb_array_elements("invoice_line_fund_distribution.invoice_line.invoice_lines"...)`.
   *
   * This is safe to do as such expressions must be referencing a view pulled in via another source,
   * and that other source will be checked, so no risk of missing a dependency here.
   */
  public boolean doesSourceViewExist(String viewName) {
    if (viewName.contains("(")) {
      return true;
    }

    return sourceViewRecordRepository.existsById(viewName);
  }

  /** Get all installed source views (per our installation records) */
  public Set<String> getInstalledSourceViews() {
    return sourceViewRecordRepository.findAll().stream().map(SourceViewRecord::getName).collect(Collectors.toSet());
  }

  /**
   * Check if mod-finance was available (at the time of last entity type installation)
   *
   * <strong>Note:</strong> this does not check mod-finance itself, but mod-finance-storage.
   * See the source view definition for more details on what this does and why it exists.
   *
   * @see /src/main/resources/db/source-views/bundled/modules/_mod_finance_storage_exchange_rate_availability_indicator.json5
   */
  public boolean isModFinanceInstalled() {
    return doesSourceViewExist(MOD_FINANCE_AVAILABILITY_INDICATOR_VIEW);
  }

  /** Get all definitions from the packaged resources */
  protected List<SourceViewDefinition> getAllDefinitions(String safeCentralTenantId) throws IOException {
    return Stream
      .concat(
        Arrays.stream(resourceResolver.getResources("classpath:/db/source-views/**/*.json")),
        Arrays.stream(resourceResolver.getResources("classpath:/db/source-views/**/*.json5"))
      )
      .filter(Resource::isReadable)
      .map(resource -> {
        try {
          return objectMapper
            .readValue(
              resource
                .getContentAsString(StandardCharsets.UTF_8)
                .replace("${tenant_id}", folioExecutionContext.getTenantId())
                .replace("${safe_central_tenant_id}", safeCentralTenantId),
              SourceViewDefinition.class
            )
            .withSourceFilePath(resource.getURI().toString());
        } catch (IOException e) {
          log.error("Unable to read view definition from resource: {}", resource.getDescription(), e);
          throw new UncheckedIOException(e);
        }
      })
      .toList();
  }

  /**
   * Get all available source view definitions based on if the underlying DB tables exists.
   *
   * @return Map of {@link SourceViewDefinition}s keyed by view name
   */
  protected Map<String, SourceViewDefinition> getAvailableDefinitions(String safeCentralTenantId) throws IOException {
    List<SourceViewDefinition> allDefinitions = getAllDefinitions(safeCentralTenantId);
    Set<SourceViewDependency> availableDependencies = sourceViewDatabaseObjectRepository.getAvailableSourceViewDependencies(safeCentralTenantId);

    Map<String, SourceViewDefinition> availableDefinitions = allDefinitions
      .stream()
      .filter(definition -> definition.isAvailable(availableDependencies))
      .collect(Collectors.toMap(SourceViewDefinition::name, Function.identity()));

    log.info(
      "Out of {} total source view definitions, {} are available based on existing database tables",
      allDefinitions.size(),
      availableDefinitions.size()
    );

    return availableDefinitions;
  }

  /**
   * Install all available source views based on the presence of their dependencies. If {@code forceUpdate} is
   * true, existing views will be recreated to ensure their definitions are up to date. This may be useful if the * underlying view's definition has changed due to an external force (for example, the source table being
   * renamed (this is common in data migrations)).
   *
   * This may result in multiple install cycles, depending on dependencies between views themselves.
   *
   * Once complete, the source_views table will be updated to reflect the current state of installed views.
   *
   * @return a set of names of source views that have been installed
   */
  public Set<String> installAvailableSourceViews(String safeCentralTenantId, boolean forceUpdate) throws IOException {
    Map<String, SourceViewDefinition> availableDefinitions = this.getAvailableDefinitions(safeCentralTenantId);
    int originalAvailableCount = -1;
    boolean shouldForceUpdateOnThisIteration = forceUpdate;

    // simple traversal to handle inter-view dependencies. This hierarchy should be incredibly shallow
    // (likely no more than one level), so simply checking if more are available after an install
    // is sufficient.
    //
    // This will not infinitely loop as each additional iteration must have more available views
    // than the last, and we will eventually run out of views to install.
    while (originalAvailableCount != availableDefinitions.size()) {
      this.installSourceViews(availableDefinitions, shouldForceUpdateOnThisIteration);

      originalAvailableCount = availableDefinitions.size();
      availableDefinitions = this.getAvailableDefinitions(safeCentralTenantId);
      // we've reinstalled once. all in future cycles will be new, so no need to force update again
      shouldForceUpdateOnThisIteration = false;
      if (originalAvailableCount != availableDefinitions.size()) {
        log.info(
          "{} additional source views are now available after installation cycle; rerunning",
          availableDefinitions.size() - originalAvailableCount
        );
      }
    }

    return availableDefinitions.keySet();
  }

  /**
   * Install source views. If {@code forceUpdate} is true, existing views will be recreated to ensure their
   * definitions are up to date. This may be useful if the underlying view's definition has changed due to
   * an external force (for example, the source table being renamed (this is common in data migrations)).
   *
   * Once complete, the source_views table will be updated to reflect the current state of installed views.
   *
   * @param toInstall   Map of {@link SourceViewDefinition}s keyed by view name, typically obtained from
   *                    {@link #getAvailableDefinitions(String)}
   * @param forceUpdate whether to forcibly recreate existing views
   */
  protected void installSourceViews(Map<String, SourceViewDefinition> toInstall, boolean forceUpdate)
    throws IOException {
    Map<String, SourceViewRecord> installedDefinitionsByName = sourceViewRecordRepository
      .findAll()
      .stream()
      .collect(Collectors.toMap(SourceViewRecord::getName, Function.identity()));

    Collection<String> definitionsToInstall = CollectionUtils.subtract(
      toInstall.keySet(),
      installedDefinitionsByName.keySet()
    );
    Collection<String> definitionsToUpdate = CollectionUtils
      .intersection(toInstall.keySet(), installedDefinitionsByName.keySet())
      .stream()
      .filter(k -> forceUpdate || !toInstall.get(k).sql().equals(installedDefinitionsByName.get(k).getDefinition()))
      .toList();
    Collection<String> definitionsToRemove = CollectionUtils.subtract(
      installedDefinitionsByName.keySet(),
      toInstall.keySet()
    );

    log.info(
      "Installing {} new views, updating {} views, removing {} views",
      definitionsToInstall.size(),
      definitionsToUpdate.size(),
      definitionsToRemove.size()
    );
    log.info("To create: {}", definitionsToInstall);
    log.info("To update: {}", definitionsToUpdate);
    log.info("To remove: {}", definitionsToRemove);

    sourceViewDatabaseObjectRepository.persistSourceViews(
      toInstall,
      definitionsToInstall,
      definitionsToUpdate,
      definitionsToRemove
    );
  }

  /**
   * Verify that all source views should exist actually do. Performs the same as {@link #installAvailableSourceViews}
   * with the bonus of ensuring that any views missing from the database are re-created.
   * @throws IOException
   */
  public void verifyAll(String safeCentralTenantId) throws IOException {
    sourceViewDatabaseObjectRepository.verifySourceViewRecordsMatchesDatabase();
    sourceViewDatabaseObjectRepository.purgeMaterializedViewsIfPresent();
    this.installAvailableSourceViews(safeCentralTenantId, false);
  }

  /**
   * Attempt to heal a single source view. If the view is missing, we will attempt to install it (subject to its
   * dependencies being available).
   *
   * @return if the view was able to be created
   */
  public boolean attemptToHealSourceView(String viewName, String safeCentralTenantId) {
    if (viewName.contains("(")) {
      log.warn("√ Source `{}` is a complex expression and cannot be checked, so we assume it exists", viewName);
      return true;
    }

    try {
      boolean installationRecordExists = this.doesSourceViewExist(viewName);
      if (installationRecordExists != sourceViewDatabaseObjectRepository.doesSourceViewExistInDatabase(viewName)) {
        log.warn(
          "X Source view `{}` existence is out of sync between source_views table and actual database. Running full verification procedure...",
          viewName
        );
        this.verifyAll(safeCentralTenantId);
        return this.doesSourceViewExist(viewName);
      }

      if (installationRecordExists) {
        log.info("√ Source view `{}` exists as expected", viewName);
        return true;
      }

      Optional<SourceViewDefinition> definitionOptional = getAllDefinitions(safeCentralTenantId)
        .stream()
        .filter(def -> def.name().equals(viewName))
        .findFirst();

      if (definitionOptional.isEmpty()) {
        log.warn("X Source view `{}` is not known", viewName);
        return false;
      }

      SourceViewDefinition definition = definitionOptional.get();

      if (!definition.isAvailable(sourceViewDatabaseObjectRepository.getAvailableSourceViewDependencies(safeCentralTenantId))) {
        log.warn("X Source view `{}` cannot be created due to missing dependencies", viewName);
        return false;
      }

      log.info("→ Attempting to create source view `{}`", viewName);
      sourceViewDatabaseObjectRepository.installSingleSourceView(definition);

      log.info("√ Successfully created source view `{}`", viewName);
      return true;
    } catch (DataAccessException | IOException | UncheckedIOException e) {
      log.error("X Failed to create source view `{}` due to unexpected error", viewName, e);
      return false;
    }
  }
}
