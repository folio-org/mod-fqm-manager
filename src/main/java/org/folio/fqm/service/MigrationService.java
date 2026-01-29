package org.folio.fqm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.config.MigrationConfiguration;
import org.folio.fqm.exception.MigrationQueryChangedException;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategyRepository;
import org.folio.fqm.migration.MigrationUtils;
import org.folio.fqm.migration.strategies.MigrationStrategy;
import org.folio.fqm.migration.warnings.RemovedFieldWarning;
import org.folio.fqm.migration.warnings.Warning;
import org.folio.fqm.repository.CustomEntityTypeMigrationMappingRepository;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@AllArgsConstructor
public class MigrationService {

  private final CustomEntityTypeMigrationMappingRepository customEntityTypeMigrationMappingRepository;
  private final EntityTypeRepository entityTypeRepository;
  private final FolioExecutionContext folioExecutionContext;
  private final MigrationConfiguration migrationConfiguration;
  private final MigrationStrategyRepository migrationStrategyRepository;
  private final ObjectMapper objectMapper;

  public String getLatestVersion() {
    return migrationConfiguration.getCurrentVersion();
  }

  public boolean isMigrationNeeded(@CheckForNull String fqlQuery) {
    return !this.getLatestVersion().equals(getVersion(fqlQuery));
  }

  public boolean isMigrationNeeded(MigratableQueryInformation migratableQueryInformation) {
    return isMigrationNeeded(migratableQueryInformation.fqlQuery());
  }

  public MigratableQueryInformation migrate(MigratableQueryInformation migratableQueryInformation) {
    String currentVersion = getVersion(migratableQueryInformation.fqlQuery());
    if (currentVersion == null) {
      currentVersion = migrationConfiguration.getDefaultVersion();
    }

    Map<UUID, Map<String, UUID>> customEntityTypeMappings = customEntityTypeMigrationMappingRepository.getMappings();

    boolean hadBreakingChanges = false;
    if (isMigrationNeeded(migratableQueryInformation)) {
      for (MigrationStrategy strategy : migrationStrategyRepository.getMigrationStrategies()) {
        if (MigrationUtils.compareVersions(currentVersion, strategy.getMaximumApplicableVersion()) <= 0) {
          log.info("Applying {} to {}", strategy.getLabel(), migratableQueryInformation);
          migratableQueryInformation = strategy.apply(migratableQueryInformation, customEntityTypeMappings);
          hadBreakingChanges |= migratableQueryInformation.hadBreakingChanges();
          currentVersion = strategy.getMaximumApplicableVersion();
        }
      }
    }

    try {
      ObjectNode fql = (ObjectNode) objectMapper.readTree(migratableQueryInformation.fqlQuery());
      fql.set(MigrationConfiguration.VERSION_KEY, objectMapper.valueToTree(migrationConfiguration.getCurrentVersion()));
      migratableQueryInformation = migratableQueryInformation.withFqlQuery(objectMapper.writeValueAsString(fql));
    } catch (JsonProcessingException e) {
      log.error("Unable to process JSON", e);
      throw new UncheckedIOException(e);
    }

    return migratableQueryInformation.withHadBreakingChanges(hadBreakingChanges);
  }

  public String getVersion(@CheckForNull String fqlQuery) {
    if (fqlQuery == null) {
      return migrationConfiguration.getDefaultVersion();
    }
    try {
      return Optional
        .ofNullable(((ObjectNode) objectMapper.readTree(fqlQuery)).get(MigrationConfiguration.VERSION_KEY))
        .map(JsonNode::asText)
        .orElse(migrationConfiguration.getDefaultVersion());
    } catch (JsonProcessingException e) {
      return migrationConfiguration.getDefaultVersion();
    }
  }

  /**
   * Migrates the query information and verifies that only the version has changed.
   * If anything other than the version changes, throws a MigrationQueryChangedException.
   *
   * @param migratableQueryInformation the query information to migrate
   * @throws MigrationQueryChangedException if anything other than the version changes
   */
  public void throwExceptionIfQueryNeedsMigration(MigratableQueryInformation migratableQueryInformation) {
    MigratableQueryInformation migratedQueryInformation = migrate(migratableQueryInformation);

    // If the query doesn't need migration, return early
    if (!isMigrationNeeded(migratableQueryInformation)) {
      return;
    }

    if (migratedQueryInformation.hadBreakingChanges()) {
      throw new MigrationQueryChangedException(migratedQueryInformation);
    }
  }

  /**
   * This recomputes the mappings stored in the custom_entity_type_migration_mapping table,
   * creating a snapshot of each custom entity type's IDs and their sources. This is used
   * during migration to allow custom entity types to be mapped correctly, however, it's
   * relatively expensive so we only update this when necessary.
   *
   * This should be called anytime entity type definitions are added/removed/changed.
   */
  public void updateCustomEntityMigrationMappings() {
    Map<UUID, Map<String, UUID>> map = getCurrentCustomEntityTypeMappings();

    log.info("Updating custom entity type migration mappings with source maps from {} entities", map.size());
    customEntityTypeMigrationMappingRepository.saveMappings(map);
  }

  protected Map<UUID, Map<String, UUID>> getCurrentCustomEntityTypeMappings() {
    return entityTypeRepository
      .getEntityTypeDefinitions(null, folioExecutionContext.getTenantId())
      .filter(et -> Boolean.TRUE.equals(et.getAdditionalProperty("isCustom")))
      .filter(et -> et.getSources() != null)
      .collect(Collectors.toMap(et -> UUID.fromString(et.getId()), EntityTypeUtils::getEntityTypeSourceAliasMap));
  }

  /**
   * Attempts to migrate custom entity types. This includes (as applicable):
   * - Updating source entity type IDs
   * - Updating source join fields
   * - Updating groupByFields
   * - Updating defaultSort
   *
   * Custom entity types are migrated from the outermost-in; see the source comment
   * for why this is necessary.
   */
  public void migrateCustomEntityTypes() {
    SortedMap<UUID, CustomEntityType> toMigrate = entityTypeRepository
      .getEntityTypeDefinitions(null, folioExecutionContext.getTenantId())
      .filter(et -> Boolean.TRUE.equals(et.getAdditionalProperty("isCustom")))
      .map(EntityType::getId)
      .map(UUID::fromString)
      .map(entityTypeRepository::getCustomEntityType)
      .filter(et -> !Boolean.TRUE.equals(et.getDeleted()))
      .filter(et -> !this.getLatestVersion().equals(et.getVersion()))
      .collect(
        Collectors.toMap(
          et -> UUID.fromString(et.getId()),
          Function.identity(),
          (a, b) -> {
            throw log.throwing(new IllegalStateException("Somehow found two custom entities with the same ID??"));
          },
          TreeMap::new
        )
      );

    /**
     * We must traverse the custom entity types from the outermost-in, to ensure source
     * references work as we traverse. If A -> B -> simple_instance, both A and B may refer
     * to fields inside simple_instance. However, if simple_instance changes (e.g. entity ID change)
     * and B is migrated before A, B's migration will change how simple_instance is referred to,
     * breaking the ability for A's migration to "know" that simple_instance is being referred to.
     *
     * By doing this from the outermost entities inwards, we can ensure that earlier migrations will
     * not break future ones.
     *
     * Traversal as a DFS is a bit easier, though, so we do that to build a queue inside-out;
     * since we know children appear before parents in this queue, we can also know parents will
     * appear after children. Flip it et voilà, we've got a parent-first queue without too much complexity!
     */
    Deque<CustomEntityType> migrationOrder = new LinkedList<>();
    while (!toMigrate.isEmpty()) {
      CustomEntityType nextInnermost = getInnermostCustomEntityTypeToMigrate(toMigrate, toMigrate.firstKey());
      migrationOrder.addFirst(nextInnermost);
    }

    Map<UUID, Map<String, UUID>> currentMappings = getCurrentCustomEntityTypeMappings();

    migrationOrder
      .stream()
      .forEach(et -> {
        CustomEntityType migrated = migrateCustomEntityType(et, currentMappings);
        toMigrate.remove(UUID.fromString(migrated.getId()));
        currentMappings.put(UUID.fromString(migrated.getId()), EntityTypeUtils.getEntityTypeSourceAliasMap(migrated));
      });

    updateCustomEntityMigrationMappings();
  }

  /**
   * Find the innermost entity type to migrate based on the provided candidate.
   * If the provided candidate does not need to be migrated, `null` is returned.
   * If the candidate depends on other entity types that need to be migrated first,
   * those are recursively checked until an entity type that has no dependence is found.
   * If all of the candidate's sources have been migrated (or did not need to be), the
   * provided candidate is returned.
   */
  protected CustomEntityType getInnermostCustomEntityTypeToMigrate(
    SortedMap<UUID, CustomEntityType> toMigrate,
    UUID candidateId
  ) {
    if (!toMigrate.containsKey(candidateId)) {
      return null;
    }

    CustomEntityType candidateEntity = toMigrate.get(candidateId);

    if (candidateEntity.getSources() == null) {
      return candidateEntity;
    }

    return candidateEntity
      .getSources()
      .stream()
      .filter(EntityTypeSourceEntityType.class::isInstance)
      .map(EntityTypeSourceEntityType.class::cast)
      .map(EntityTypeSourceEntityType::getTargetId)
      .map(id -> getInnermostCustomEntityTypeToMigrate(toMigrate, id))
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(candidateEntity);
  }

  protected CustomEntityType migrateCustomEntityType(
    CustomEntityType et,
    Map<UUID, Map<String, UUID>> currentCustomEntityTypeMappings
  ) {
    log.info(
      "Migrating custom entity type {} ({}) from V{} to V{}",
      et.getName(),
      et.getId(),
      et.getVersion(),
      this.getLatestVersion()
    );

    List<Warning> warnings = new ArrayList<>();

    for (MigrationStrategy strategy : migrationStrategyRepository.getMigrationStrategies()) {
      if (MigrationUtils.compareVersions(et.getVersion(), strategy.getMaximumApplicableVersion()) <= 0) {
        log.info("Applying {}", strategy.getLabel());

        // these may change between strategy executions, so we re-compute ours each time
        currentCustomEntityTypeMappings.put(
          UUID.fromString(et.getId()),
          EntityTypeUtils.getEntityTypeSourceAliasMap(et)
        );

        // the order of these don't matter too much as they won't change each other
        // (any necessary hierarchy data is stored in the above derived map)
        migrateEntitySources(et, strategy, currentCustomEntityTypeMappings, warnings);
        migrateEntityGroupByFields(et, strategy, currentCustomEntityTypeMappings, warnings);
        migrateEntityDefaultSort(et, strategy, currentCustomEntityTypeMappings, warnings);
      }
    }

    et.setVersion(this.getLatestVersion());

    entityTypeRepository.updateEntityType(et);

    return et;
  }

  /**
   * Migrate an entity's `defaultSort` fields. If the migration results in ≠1 field per starting
   * field, that field is removed from the default sort.
   */
  protected void migrateEntityDefaultSort(
    EntityType entity,
    MigrationStrategy strategy,
    Map<UUID, Map<String, UUID>> mappings,
    List<Warning> warnings
  ) {
    if (entity.getDefaultSort() == null) {
      return;
    }

    entity.setDefaultSort(
      entity
        .getDefaultSort()
        .stream()
        .map(sortEntry -> {
          MigratableQueryInformation result = strategy.apply(
            getMigratableQueryInformationForFields(UUID.fromString(entity.getId()), sortEntry.getColumnName()),
            mappings
          );

          warnings.addAll(result.warnings());
          if (result.fields().size() != 1) {
            // just in case; redundant ones will be stripped later
            warnings.add(RemovedFieldWarning.builder().field(sortEntry.getColumnName()).build());
            return null;
          }

          sortEntry.setColumnName(result.fields().get(0));
          return sortEntry;
        })
        .filter(Objects::nonNull)
        .toList()
    );
  }

  /** Migrate an entity's `groupByFields`. */
  protected void migrateEntityGroupByFields(
    EntityType entity,
    MigrationStrategy strategy,
    Map<UUID, Map<String, UUID>> mappings,
    List<Warning> warnings
  ) {
    if (entity.getGroupByFields() == null) {
      return;
    }

    MigratableQueryInformation result = strategy.apply(
      getMigratableQueryInformationForFields(
        UUID.fromString(entity.getId()),
        entity.getGroupByFields().toArray(String[]::new)
      ),
      mappings
    );

    warnings.addAll(result.warnings());

    entity.setGroupByFields(result.fields());
  }

  /** Migrate source `targetId`, `targetField`, and `sourceField` as applicable. */
  protected void migrateEntitySources(
    EntityType entity,
    MigrationStrategy strategy,
    Map<UUID, Map<String, UUID>> mappings,
    List<Warning> warnings
  ) {
    if (entity.getSources() == null) {
      return;
    }

    entity
      .getSources()
      .stream()
      .filter(EntityTypeSourceEntityType.class::isInstance)
      .map(EntityTypeSourceEntityType.class::cast)
      // migrate the targetId and targetField
      .map(source -> {
        MigratableQueryInformation toMigrate = getMigratableQueryInformationForFields(source.getTargetId());
        if (source.getTargetField() != null) {
          toMigrate = toMigrate.withFields(List.of(source.getTargetField()));
        }
        MigratableQueryInformation result = strategy.apply(toMigrate, mappings);
        warnings.addAll(result.warnings());
        source.setTargetId(result.entityTypeId());
        if (result.fields().size() == 1) {
          source.setTargetField(result.fields().get(0));
        }
        return source;
      })
      // migrate the sourceField, if applicable (this is ran in the context of the outer entity,
      // as we must resolve between our sources)
      .filter(source -> source.getSourceField() != null)
      .forEach(source -> {
        MigratableQueryInformation result = strategy.apply(
          getMigratableQueryInformationForFields(UUID.fromString(entity.getId()), source.getSourceField()),
          mappings
        );
        warnings.addAll(result.warnings());
        if (result.fields().size() == 1) {
          source.setSourceField(result.fields().get(0));
        }
      });
  }

  protected static MigratableQueryInformation getMigratableQueryInformationForFields(
    UUID entityTypeId,
    String... fields
  ) {
    return MigratableQueryInformation
      .builder()
      .entityTypeId(entityTypeId)
      .fqlQuery("{}")
      .fields(Arrays.asList(fields))
      .warnings(List.of())
      .hadBreakingChanges(false)
      .build();
  }
}
