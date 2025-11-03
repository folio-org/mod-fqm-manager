package org.folio.fqm.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
import org.folio.querytool.domain.dto.ObjectType;
import org.folio.querytool.domain.dto.ArrayType;
import org.folio.querytool.domain.dto.JsonbArrayType;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;
import static java.util.Objects.requireNonNullElse;

@Log4j2
@Service
public class EntityTypeFlatteningService {

  private final EntityTypeRepository entityTypeRepository;
  private final LocalizationService localizationService;
  private final FolioExecutionContext executionContext;
  private final UserTenantService userTenantService;

  private final Cache<EntityTypeCacheKey, EntityType> entityTypeCache;

  // Sort columns within a source, based on their label alias (nulls last)
  private static final Comparator<EntityTypeColumn> columnComparator =
    nullsLast(
      comparing(entityTypeColumn ->
          requireNonNullElse(entityTypeColumn.getLabelAlias(), ""),
        String.CASE_INSENSITIVE_ORDER));

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
      Caffeine.newBuilder().expireAfterWrite(cacheDurationSeconds, TimeUnit.SECONDS).build();
  }

  /**
   * Gets a flattened entity type definition, fully localized.
   *
   * @param entityTypeId       The ID of the entity type to flatten
   * @param tenantId           The tenant ID to use for fetching the entity type definition
   * @param preserveAllColumns If true, all columns will be preserved, even if they are marked as non-essential and should be filtered out.
   *                           This is primary for use by methods which build the from clause, since some join-related columns aren't exposed to users.
   * @return The flattened entity type definition, fully localized
   */
  public EntityType getFlattenedEntityType(UUID entityTypeId, String tenantId, boolean preserveAllColumns) {
    return entityTypeCache
      .get(
        new EntityTypeCacheKey(tenantId, entityTypeId, localizationService.getCurrentLocales(), preserveAllColumns),
        k -> getFlattenedEntityType(k.entityTypeId(), null, k.tenantId(), k.preserveAllColumns())
      )
      // ensures we get a fresh copy each time, preventing any changes to the cached entity type from affecting future requests
      .toBuilder()
      .build();
  }

  /**
   * Gets a flattened entity type definition, fully localized, without retrieving it from the cache.
   *
   * This is the same as {@link #getFlattenedEntityType(UUID, String, boolean)}, but does not use the cache, and it uses the provided
   * EntityType, rather than fetching it from the repository.
   */
  public EntityType getFlattenedEntityType(EntityType entityType, String tenantId, boolean preserveAllColumns) {
    return getFlattenedEntityType(entityType, null, tenantId, preserveAllColumns);
  }

  private EntityType getFlattenedEntityType(
    UUID entityTypeId,
    @CheckForNull EntityTypeSourceEntityType sourceFromParent,
    String tenantId,
    boolean preserveAllColumns
  ) {
    EntityType originalEntityType = entityTypeRepository
      .getEntityTypeDefinition(entityTypeId, tenantId)
      .filter(entityType -> !Boolean.TRUE.equals(entityType.getDeleted()))
      .orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId));
    return getFlattenedEntityType(originalEntityType, sourceFromParent, tenantId, preserveAllColumns);
  }

  private EntityType getFlattenedEntityType(
    EntityType originalEntityType,
    @CheckForNull EntityTypeSourceEntityType sourceFromParent,
    String tenantId,
    boolean preserveAllColumns
  ) {
    EntityType flattenedEntityType = new EntityType()
      .id(originalEntityType.getId())
      .name(originalEntityType.getName())
      .description(originalEntityType.getDescription())
      ._private(originalEntityType.getPrivate())
      .defaultSort(originalEntityType.getDefaultSort())
      .idView(originalEntityType.getIdView())
      .customFieldEntityTypeId(originalEntityType.getCustomFieldEntityTypeId())
      .labelAlias(originalEntityType.getLabelAlias())
      .groupByFields(originalEntityType.getGroupByFields())
      .sourceView(originalEntityType.getSourceView())
      .sourceViewExtractor(originalEntityType.getSourceViewExtractor())
      .crossTenantQueriesEnabled(originalEntityType.getCrossTenantQueriesEnabled())
      .filterConditions(originalEntityType.getFilterConditions())
      .additionalEcsConditions(originalEntityType.getAdditionalEcsConditions())
      .usedBy(originalEntityType.getUsedBy())
      .putAdditionalProperty(
        "isCustom",
        Optional.ofNullable(originalEntityType.getAdditionalProperties())
          .map(props -> props.get("isCustom"))
          .orElse(false)
      );

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

    // Sort the sources, so that the resulting groups of columns end up sorted appropriately
    Iterable<EntityTypeSource> orderedSources = originalEntityType.getSources().stream().sorted(getSourceComparator(originalEntityType))::iterator;
    for (EntityTypeSource source : orderedSources) {
      flattenedEntityType.addSourcesItem(SourceUtils.copySource(sourceFromParent, source, renamedAliases));

      if (source instanceof EntityTypeSourceEntityType sourceEt) {
        // Recursively flatten the source and add it to ourselves
        UUID sourceEntityTypeId = sourceEt.getTargetId();
        EntityType flattenedSourceDefinition = getFlattenedEntityType(
          sourceEntityTypeId,
          sourceEt,
          tenantId,
          preserveAllColumns
        );

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

        flattenedSourceDefinition.getFilterConditions().forEach(originalEntityType::addFilterConditionsItem);

        List<EntityTypeColumn> columns = flattenedSourceDefinition
          .getColumns();

        // Add a prefix to each column's name and idColumnName, then add 'em to the flattened entity type
        childColumns.addAll(
          columns.stream()
            .filter(col ->
              preserveAllColumns ||
                !Boolean.TRUE.equals(sourceEt.getEssentialOnly()) ||
                Boolean.TRUE.equals(col.getEssential())
            )
            .filter(col -> !Boolean.TRUE.equals(col.getIsCustomField()) || Boolean.TRUE.equals(sourceEt.getInheritCustomFields()))
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

    if (!CollectionUtils.isEmpty(flattenedEntityType.getFilterConditions())) {
      List<String> newFilterConditions = SourceUtils.injectSourceAliasIntoFilterConditions(
        flattenedEntityType.getFilterConditions(),
        renamedAliases,
        sourceFromParent == null
      );
      flattenedEntityType.filterConditions(newFilterConditions);
    }

    // Copy and localize all of the columns defined directly in the entity type separately, so that they can be sorted
    // by localized label alias before adding them to the flattened entity type
    Stream<EntityTypeColumn> selfColumns = originalEntityType.getColumns().stream()
      .map(column -> localizationService.localizeEntityTypeColumn(originalEntityType, originalEntityType.getSources(), column))
      .sorted(columnComparator);
    Stream<EntityTypeColumn> allColumns = Stream.concat(
      SourceUtils.copyColumns(sourceFromParent, selfColumns, renamedAliases),
      childColumns.stream()
    );

    flattenedEntityType.columns(
      filterEcsColumns(allColumns)
        .map(column -> column.sourceAlias(renamedAliases.getOrDefault(column.getSourceAlias(), null)))
        .toList()
    );
    flattenedEntityType.requiredPermissions(new ArrayList<>(finalPermissions));
    return localizationService.localizeEntityType(flattenedEntityType, originalEntityType.getSources());
  }

  private Stream<EntityTypeColumn> filterEcsColumns(Stream<EntityTypeColumn> unfilteredColumns) {
    boolean ecsEnabled = ecsEnabled();
    return unfilteredColumns
      .filter(column -> ecsEnabled || !Boolean.TRUE.equals(column.getEcsOnly()))
      .map(column ->
        switch (column.getDataType()) {
          case ArrayType arrayType when arrayType.getItemDataType() instanceof ObjectType objectType ->
            column.dataType(arrayType.itemDataType(filterEcsProperties(objectType, ecsEnabled)));
          case JsonbArrayType arrayType when arrayType.getItemDataType() instanceof ObjectType objectType ->
            column.dataType(arrayType.itemDataType(filterEcsProperties(objectType, ecsEnabled)));
          default -> column;
        }
      )
      .map(column ->
        column.getValues() == null ? column : column.values(column.getValues().stream().distinct().toList())
      );
  }

  private static ObjectType filterEcsProperties(ObjectType objectType, boolean ecsEnabled) {
    if (ecsEnabled) {
      return objectType;
    }
    return objectType.toBuilder().properties(
      objectType.getProperties().stream()
        .filter(prop -> !Boolean.TRUE.equals(prop.getEcsOnly()))
        .toList()
    ).build();
  }

  private boolean ecsEnabled() {
    String userTenantsResponse = userTenantService.getUserTenantsResponse(executionContext.getTenantId());
    DocumentContext parsedJson = JsonPath.parse(userTenantsResponse);
    // The value isn't needed here, this just provides an easy way to tell if ECS is enabled
    int totalRecords = parsedJson.read("totalRecords", Integer.class);
    return totalRecords > 0;
  }

  private Comparator<EntityTypeSource> getSourceComparator(EntityType entityType) {
    return comparing((EntityTypeSource source) ->
      source instanceof EntityTypeSourceEntityType etSource
        ? etSource.getOrder()
        : Integer.MAX_VALUE) // Right now this only affects things inherited from ET sources, so just put DB sources last. This doesn't really affect anything, but it reduces the noise when debugging
      .thenComparing(source -> Objects.toString(localizationService.localizeSourceLabel(entityType, source.getAlias(), source))); // Use Objects.toString(), to handle nulls gracefully
  }

  // translations are included as part of flattening, so we must cache based on locales, too
  private record EntityTypeCacheKey(
    String tenantId,
    UUID entityTypeId,
    List<Locale> locales,
    boolean preserveAllColumns
  ) {
    EntityTypeCacheKey {
      if (tenantId == null) {
        throw new IllegalArgumentException("tenantId cannot be null");
      }
    }
  }
}
