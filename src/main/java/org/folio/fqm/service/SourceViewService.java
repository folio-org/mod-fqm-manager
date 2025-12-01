package org.folio.fqm.service;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
import org.folio.fqm.repository.SourceViewRepository;
import org.folio.fqm.utils.JSON5ObjectMapperFactory;
import org.folio.spring.FolioExecutionContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.exception.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

/**
 * Service class for interacting with source views at a higher level.
 *
 * The main operating principle here is that each view database object gets a corresponding {@link SourceViewRecord}
 * in the source_views table. Installation is handled by {@link #installAvailableSourceViews(boolean)}
 * (with the option to forcibly recreate all existing ones).
 *
 * These records should not get ouf sync with the actual underlying database objects in normal usage,
 * however, some FSE operations (such as data migration or module (re)installation) can cause this to happen.
 * In cases where our records are suspected to be out of sync, {@link #verifyAll()} should be used
 * to ensure consistency. If the discrepancies are suspected to be limited to a single source view,
 * the {@link #attemptToHealSourceView(String)} method may be used instead.
 */
@Log4j2
@Service
public class SourceViewService {

  protected static final String MOD_FINANCE_AVAILABILITY_INDICATOR_VIEW =
    "_mod_finance_storage_exchange_rate_availability_indicator";

  private final SourceViewRepository sourceViewRepository;

  private final FolioExecutionContext folioExecutionContext;

  private final ObjectMapper objectMapper;
  private final ResourcePatternResolver resourceResolver;

  private final DSLContext jooqContext;

  @Autowired
  public SourceViewService(
    SourceViewRepository sourceViewRepository,
    FolioExecutionContext folioExecutionContext,
    ResourcePatternResolver resourceResolver,
    DSLContext jooqContext
  ) {
    this.sourceViewRepository = sourceViewRepository;
    this.folioExecutionContext = folioExecutionContext;
    this.resourceResolver = resourceResolver;
    this.jooqContext = jooqContext;

    this.objectMapper = JSON5ObjectMapperFactory.create();
  }

  /** Check if a source view is available per our installation records */
  public boolean doesSourceViewExist(String viewName) {
    if (viewName.contains("(")) {
      return true;
    }

    return sourceViewRepository.existsById(viewName);
  }

  /** Get all installed source views (per our installation records) */
  public Set<String> getInstalledSourceViews() {
    return sourceViewRepository.findAll().stream().map(SourceViewRecord::getName).collect(Collectors.toSet());
  }

  /**
   * Check if mod-finance was available (at the time of last entity type installation)
   *
   * <strong>Note:</strong> this does not check mod-finance itself, but mod-finance-storage.
   * See the liquibase changelog for more details on what this does and why it exists.
   */
  public boolean isModFinanceInstalled() {
    return doesSourceViewExist(MOD_FINANCE_AVAILABILITY_INDICATOR_VIEW);
  }

  /** Get all definitions from the filesystem */
  protected List<SourceViewDefinition> getAllDefinitions() throws IOException {
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
                .replace("${tenant_id}", folioExecutionContext.getTenantId()),
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

  /** Get all available source view definitions based on if the underlying DB tables exist */
  protected Map<String, SourceViewDefinition> getAvailableDefinitions() throws IOException {
    List<SourceViewDefinition> allDefinitions = getAllDefinitions();
    Set<SourceViewDependency> availableDependencies = getAvailableDependencies();

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
   * Install all available source views based on the presence of their dependencies. If {@code forceUpdate} is,
   * true existing views will be recreated to ensure their definitions are up to date. This may be useful if the
   * underlying view's definition has changed due to an external force (for example, the source table being
   * renamed (this is common in data migrations)).
   *
   * This may result in multiple install cycles, depending on dependencies between views themselves.
   *
   * Once complete, the source_views table will be updated to reflect the current state of installed views.
   */
  public Set<String> installAvailableSourceViews(boolean forceUpdate) throws IOException {
    Map<String, SourceViewDefinition> availableDefinitions = this.getAvailableDefinitions();
    int originalAvailableCount = availableDefinitions.size();
    boolean shouldForceUpdateOnThisIteration = forceUpdate;

    // simple traversal to handle inter-view dependencies. This hierarchy should be incredibly shallow
    // (likely no more than one level), so simply checking if more are available after an install
    // is sufficient.
    do {
      this.installSourceViews(availableDefinitions, shouldForceUpdateOnThisIteration);

      originalAvailableCount = availableDefinitions.size();
      availableDefinitions = this.getAvailableDefinitions();
      // we've reinstalled once. all in future cycles will be new, so no need to force update again
      shouldForceUpdateOnThisIteration = false;
      if (originalAvailableCount != availableDefinitions.size()) {
        log.info(
          "{} additional source views are now available after installation cycle; rerunning",
          availableDefinitions.size() - originalAvailableCount
        );
      }
    } while (originalAvailableCount != availableDefinitions.size());

    return availableDefinitions.keySet();
  }

  /**
   * Install source views. If {@code forceUpdate} is true, existing views will be recreated to ensure their
   * definitions are up to date. This may be useful if the underlying view's definition has changed due to
   * an external force (for example, the source table being renamed (this is common in data migrations)).
   *
   * Once complete, the source_views table will be updated to reflect the current state of installed views.
   */
  protected void installSourceViews(Map<String, SourceViewDefinition> toInstall, boolean forceUpdate)
    throws IOException {
    Map<String, SourceViewRecord> installedDefinitions = sourceViewRepository
      .findAll()
      .stream()
      .collect(Collectors.toMap(SourceViewRecord::getName, Function.identity()));

    Collection<String> definitionsToInstall = CollectionUtils.subtract(
      toInstall.keySet(),
      installedDefinitions.keySet()
    );
    Collection<String> definitionsToUpdate = CollectionUtils
      .intersection(toInstall.keySet(), installedDefinitions.keySet())
      .stream()
      .filter(k -> forceUpdate || !toInstall.get(k).sql().equals(installedDefinitions.get(k).getDefinition()))
      .toList();
    Collection<String> definitionsToRemove = CollectionUtils.subtract(
      installedDefinitions.keySet(),
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

    if (!definitionsToInstall.isEmpty()) {
      jooqContext.transaction(transaction -> {
        definitionsToRemove.forEach(k -> transaction.dsl().dropViewIfExists(k).execute());
        Stream
          .concat(definitionsToInstall.stream(), definitionsToUpdate.stream())
          .forEach(k -> transaction.dsl().createOrReplaceView(k).as(toInstall.get(k).sql()).execute());
      });
    }

    log.info("All done, updating source_views table...");

    sourceViewRepository.deleteAllById(definitionsToRemove);
    sourceViewRepository.deleteAllById(definitionsToUpdate);
    sourceViewRepository.saveAll(
      Stream
        .concat(definitionsToInstall.stream(), definitionsToUpdate.stream())
        .map(name -> {
          SourceViewDefinition def = toInstall.get(name);
          return SourceViewRecord
            .builder()
            .name(def.name())
            .definition(def.sql())
            .sourceFile(def.sourceFilePath())
            .lastUpdated(Instant.now())
            .build();
        })
        .toList()
    );

    log.info("Source views table updated successfully");
  }

  /** Check if the underlying source view exists in the database */
  protected boolean doesSourceViewExistInDatabase(String viewName) {
    return (
      jooqContext
        .selectOne()
        .from(table(name("information_schema", "tables")))
        .where(
          List.of(
            field("table_schema").eq("%s_mod_fqm_manager".formatted(folioExecutionContext.getTenantId())),
            field("table_name").eq(viewName)
          )
        )
        .fetchOne() !=
      null
    );
  }

  /** Get the list of views present in our module; used to reconcile and ensure source_views is accurate */
  protected Set<String> getInstalledSourceViewsFromDatabase() {
    return jooqContext
      .select(field("table_name", String.class))
      .from(table(name("information_schema", "tables")))
      .where(
        List.of(
          field("table_schema").eq("%s_mod_fqm_manager".formatted(folioExecutionContext.getTenantId())),
          field("table_type").eq("VIEW")
        )
      )
      .fetchSet(field("table_name", String.class));
  }

  protected Set<SourceViewDependency> getAvailableDependencies() {
    // in testing, this yields <2500 tables for a full FOLIO install. This is not the most efficient way to do this,
    // however, it's a lot better than sending hundreds of individual queries to check for existence one at a time,
    // and a lot more readable than crafting a massive dynamic query with hundreds of OR clauses.
    Result<Record> result = jooqContext
      .select(List.of(field("table_schema", String.class), field("table_name", String.class)))
      .from(table(name("information_schema", "tables")))
      .where(field("table_schema").startsWith(folioExecutionContext.getTenantId() + "_mod_"))
      .fetch();

    log.info("Discovered {} available dependency tables in the database", result.size());

    return result
      .stream()
      .map(r ->
        new SourceViewDependency(r.get(field("table_schema", String.class)), r.get(field("table_name", String.class)))
      )
      .collect(Collectors.toSet());
  }

  /** Get all present materialized views. We want to remove these legacy ones if encountered. */
  protected List<String> getMaterializedViewsFromDatabase() {
    return jooqContext
      .select(field("matviewname", String.class))
      .from(table(name("pg_matviews")))
      .where(field("schemaname").eq("%s_mod_fqm_manager".formatted(folioExecutionContext.getTenantId())))
      .fetch(field("matviewname", String.class));
  }

  /**
   * Verify that all source views should exist actually do. Performs the same as {@link #installAvailableSourceViews}
   * with the bonus of ensuring that any views missing from the database are re-created.
   * @throws IOException
   */
  public void verifyAll() throws IOException {
    Set<String> realViews = getInstalledSourceViewsFromDatabase();
    List<String> expectedViews = sourceViewRepository.findAll().stream().map(SourceViewRecord::getName).toList();

    Collection<String> missingViews = CollectionUtils.subtract(expectedViews, realViews);
    if (!missingViews.isEmpty()) {
      log.error(
        "The following source views were previously created but are now missing from the database: {}",
        missingViews
      );
      log.error("Removing these from source_views table and triggering recreation...");
      sourceViewRepository.deleteAllById(missingViews);
    }

    Collection<String> unexpectedViews = CollectionUtils.subtract(realViews, expectedViews);
    if (!unexpectedViews.isEmpty()) {
      log.warn("The following unexpected source views exist in the database: {}", unexpectedViews);
      log.warn("These are likely from a previous installation where views were created by Liquibase.");
      log.warn("Removing {} views: {}", unexpectedViews.size(), unexpectedViews);
      jooqContext.transaction(transaction ->
        unexpectedViews.forEach(viewName -> transaction.dsl().dropView(viewName).execute())
      );
    }

    List<String> materializedViews = getMaterializedViewsFromDatabase();
    if (!materializedViews.isEmpty()) {
      log.warn("The following legacy materialized views exist in the database: {}", materializedViews);
      log.warn("Removing {} materialized views: {}", materializedViews.size(), materializedViews);
      jooqContext.transaction(transaction ->
        materializedViews.forEach(viewName -> transaction.dsl().dropMaterializedView(viewName).execute())
      );
    }

    this.installAvailableSourceViews(false);
  }

  /**
   * Attempt to heal a single source view. Will return if the view exists after the method call.
   *
   * If the view is missing, we will attempt to install it (subject to its dependencies being available).
   */
  public boolean attemptToHealSourceView(String viewName) {
    if (viewName.contains("(")) {
      log.warn("√ Source `{}` is a complex expression and cannot be checked, so we assume it exists", viewName);
      return true;
    }

    try {
      boolean installationRecordExists = this.doesSourceViewExist(viewName);
      if (installationRecordExists != this.doesSourceViewExistInDatabase(viewName)) {
        log.warn(
          "X Source view `{}` existence is out of sync between source_views table and actual database",
          viewName
        );
        log.warn("→ Running full verification procedure...");
        this.verifyAll();
        this.installAvailableSourceViews(false);
        return this.doesSourceViewExist(viewName);
      }

      if (installationRecordExists) {
        log.info("√ Source view `{}` exists as expected", viewName);
        return true;
      }

      Optional<SourceViewDefinition> definitionOptional = getAllDefinitions()
        .stream()
        .filter(def -> def.name().equals(viewName))
        .findFirst();

      if (definitionOptional.isEmpty()) {
        log.warn("X Source view `{}` is not known", viewName);
        return false;
      }

      SourceViewDefinition definition = definitionOptional.get();

      if (!definition.isAvailable(getAvailableDependencies())) {
        log.warn("X Source view `{}` cannot be created due to missing dependencies", viewName);
        return false;
      }

      log.info("→ Attempting to create source view `{}`", viewName);
      jooqContext.transaction(transaction ->
        transaction.dsl().createOrReplaceView(viewName).as(definition.sql()).execute()
      );

      SourceViewRecord newRecord = SourceViewRecord
        .builder()
        .name(definition.name())
        .definition(definition.sql())
        .sourceFile(definition.sourceFilePath())
        .lastUpdated(Instant.now())
        .build();
      sourceViewRepository.save(newRecord);

      log.info("√ Successfully created source view `{}`", viewName);
      return true;
    } catch (DataAccessException | IOException | UncheckedIOException e) {
      log.error("X Failed to create source view `{}` due to unexpected error", viewName, e);
      return false;
    }
  }
}
