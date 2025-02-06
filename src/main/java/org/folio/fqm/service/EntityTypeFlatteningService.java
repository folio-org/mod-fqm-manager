package org.folio.fqm.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.utils.flattening.SourceUtils;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class EntityTypeFlatteningService {

  private final EntityTypeRepository entityTypeRepository;
  private final LocalizationService localizationService;
  private final FolioExecutionContext executionContext;
  private final UserTenantService userTenantService;

  private final Cache<EntityTypeCacheKey, EntityType> entityTypeCache;

  @Autowired
  public EntityTypeFlatteningService(
    EntityTypeRepository entityTypeRepository,
    LocalizationService localizationService,
    FolioExecutionContext executionContext,
    UserTenantService userTenantService,
    @Value("${mod-fqm-manager.entity-type-cache-timeout-seconds:300}") long cacheDurationSeconds
  ) {
    this.entityTypeRepository = entityTypeRepository;
    this.localizationService = localizationService;
    this.executionContext = executionContext;
    this.userTenantService = userTenantService;

    this.entityTypeCache =
      Caffeine.newBuilder().expireAfterWrite(cacheDurationSeconds, java.util.concurrent.TimeUnit.SECONDS).build();
  }

  public EntityType getFlattenedEntityType(UUID entityTypeId, String tenantId) {
    return getFlattenedEntityType(entityTypeId, tenantId, false);
  }

  /**
   * Gets a flattened entity type definition, fully localized.
   *
   * @param entityTypeId The ID of the entity type to flatten
   * @param tenantId The tenant ID to use for fetching the entity type definition
   * @param preserveAllColumns If true, all columns will be preserved, even if they are marked as non-essential and should be filtered out.
   *                             This is primary for use by methods which build the from clause, since some join-related columns aren't exposed to users.
   * @return
   */
  public EntityType getFlattenedEntityType(UUID entityTypeId, String tenantId, boolean preserveAllColumns) {
    return entityTypeCache.get(
      new EntityTypeCacheKey(tenantId, entityTypeId, localizationService.getCurrentLocales(), preserveAllColumns),
      k -> getFlattenedEntityType(k.entityTypeId(), null, k.tenantId(), k.preserveAllColumns())
    );
  }

  private EntityType getFlattenedEntityType(
    UUID entityTypeId,
    @CheckForNull EntityTypeSourceEntityType sourceFromParent,
    String tenantId,
    boolean preserveAllColumns
  ) {
    EntityType originalEntityType = entityTypeRepository
      .getEntityTypeDefinition(entityTypeId, tenantId)
      .orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId));

    EntityType flattenedEntityType = new EntityType()
      .id(originalEntityType.getId())
      .name(originalEntityType.getName())
      ._private(originalEntityType.getPrivate())
      .defaultSort(originalEntityType.getDefaultSort())
      .idView(originalEntityType.getIdView())
      .customFieldEntityTypeId(originalEntityType.getCustomFieldEntityTypeId())
      .labelAlias(originalEntityType.getLabelAlias())
      .root(originalEntityType.getRoot())
      .groupByFields(originalEntityType.getGroupByFields())
      .sourceView(originalEntityType.getSourceView())
      .sourceViewExtractor(originalEntityType.getSourceViewExtractor())
      .crossTenantQueriesEnabled(originalEntityType.getCrossTenantQueriesEnabled())
      .additionalEcsConditions(originalEntityType.getAdditionalEcsConditions());

    // If we have a parent, this will be `parent.`, otherwise empty
    String aliasPrefix = Optional.ofNullable(sourceFromParent).map(s -> s.getAlias() + ".").orElse("");

    Map<String, String> renamedAliases = new LinkedHashMap<>(); // <oldName, newName>
    for (EntityTypeSource source : originalEntityType.getSources()) {
      // Update alias
      String oldAlias = source.getAlias();
      String newAlias = aliasPrefix + oldAlias;
      renamedAliases.put(oldAlias, newAlias);
    }

    Set<String> finalPermissions = new HashSet<>(originalEntityType.getRequiredPermissions());
    List<EntityTypeColumn> childColumns = new ArrayList<>();

    for (EntityTypeSource source : originalEntityType.getSources()) {
      EntityTypeSource newSource = SourceUtils.copySource(sourceFromParent, source, renamedAliases);
      flattenedEntityType.addSourcesItem(newSource);

      if (source instanceof EntityTypeSourceEntityType sourceEt) {
        // Recursively flatten the source and add it to ourselves
        UUID sourceEntityTypeId = sourceEt.getTargetId();
        EntityType flattenedSourceDefinition = getFlattenedEntityType(sourceEntityTypeId, sourceEt, tenantId, preserveAllColumns);

        // If the original entity type already supports cross-tenant queries, we can skip this. Otherwise, copy the nested source's setting
        // This effectively means that if any nested source supports cross-tenant queries, the flattened entity type will too
        if (!Boolean.TRUE.equals(flattenedEntityType.getCrossTenantQueriesEnabled())) {
          flattenedEntityType.crossTenantQueriesEnabled(flattenedSourceDefinition.getCrossTenantQueriesEnabled());
        }
        finalPermissions.addAll(flattenedSourceDefinition.getRequiredPermissions());

        // we must pre-populate this to ensure all sub-sources are added to the map before their parents are processed
        // otherwise, targetFields may get out of sync from higher order composites (target `sub_et.db.field` won't know what to do
        // if it isn't aware of how to map `sub_et` yet)
        flattenedSourceDefinition
          .getSources()
          .forEach(subSource -> renamedAliases.put(subSource.getAlias(), aliasPrefix + subSource.getAlias()));
        // Copy each sub-source into the flattened entity type
        flattenedSourceDefinition
          .getSources()
          .forEach(subSource ->
            flattenedEntityType.addSourcesItem(SourceUtils.copySource(sourceEt, subSource, renamedAliases))
          );

        // Add a prefix to each column's name and idColumnName, then add 'em to the flattened entity type
        childColumns.addAll(
          flattenedSourceDefinition
            .getColumns()
            .stream()
            .filter(col -> preserveAllColumns || !Boolean.TRUE.equals(sourceEt.getEssentialOnly()) || Boolean.TRUE.equals(col.getEssential()))
            // Don't use aliasPrefix here, since the prefix is already appropriately baked into the source alias in flattenedSourceDefinition
            .map(col ->
              SourceUtils.injectSourceAlias(
                col
                  .name(sourceEt.getAlias() + '.' + col.getName())
                  .idColumnName(
                    Optional
                      .ofNullable(col.getIdColumnName())
                      .map(idColumnName -> sourceEt.getAlias() + '.' + idColumnName)
                      .orElse(null)
                  )
                  .originalEntityTypeId(Optional.ofNullable(col.getOriginalEntityTypeId()).orElse(sourceEntityTypeId)),
                renamedAliases,
                col.getSourceAlias(),
                sourceFromParent == null
              )
            )
            .toList()
        );
      }
    }

    if (flattenedEntityType.getSourceViewExtractor() != null) {
      flattenedEntityType.sourceViewExtractor(
        SourceUtils.injectSourceAliasIntoViewExtractor(flattenedEntityType.getSourceViewExtractor(), renamedAliases)
      );
    }
    Stream<EntityTypeColumn> allColumns = Stream.concat(
      SourceUtils.copyColumns(sourceFromParent, originalEntityType, renamedAliases),
      childColumns.stream()
    );

    flattenedEntityType.columns(filterEcsColumns(allColumns).toList());
    flattenedEntityType.requiredPermissions(new ArrayList<>(finalPermissions));
    return localizationService.localizeEntityType(flattenedEntityType);
  }

  private Stream<EntityTypeColumn> filterEcsColumns(Stream<EntityTypeColumn> unfilteredColumns) {
    boolean ecsEnabled = ecsEnabled();
    return unfilteredColumns
      .filter(column -> ecsEnabled || !Boolean.TRUE.equals(column.getEcsOnly()))
      .map(column ->
        column.getValues() == null ? column : column.values(column.getValues().stream().distinct().toList())
      );
  }

  private boolean ecsEnabled() {
    String userTenantsResponse = userTenantService.getUserTenantsResponse(executionContext.getTenantId());
    DocumentContext parsedJson = JsonPath.parse(userTenantsResponse);
    // The value isn't needed here, this just provides an easy way to tell if ECS is enabled
    int totalRecords = parsedJson.read("totalRecords", Integer.class);
    return totalRecords > 0;
  }

  // translations are included as part of flattening, so we must cache based on locales, too
  private record EntityTypeCacheKey(
    String tenantId,
    UUID entityTypeId,
    List<Locale> locales,
    boolean preserveAllColumns
  ) {}
}
