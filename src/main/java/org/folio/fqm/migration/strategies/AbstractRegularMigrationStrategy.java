package org.folio.fqm.migration.strategies;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationUtils;
import org.folio.fqm.migration.types.MigratableFqlFieldAndCondition;
import org.folio.fqm.migration.types.MigratableFqlFieldOnly;
import org.folio.fqm.migration.types.MigrationResult;
import org.folio.fqm.migration.types.SingleFieldMigrationResult;
import org.springframework.data.util.StreamUtils;

public abstract class AbstractRegularMigrationStrategy<S> implements MigrationStrategy {

  /**
   * Called before any migration starts; this variable will be retained and passed to each call
   * of the other methods. This is useful if you want to cache values or other state across calls
   * (such as loading values from an external API).
   */
  public S getStartingState() {
    return null;
  }

  /**
   * Defines the relationship of composite entities to the entity being migrated here. For example,
   * a migration which alters `simple_instance_status` would need to define that
   * `composite_instances` and `composite_item_details` inherited it. This must be provided in the
   * migration as, without this, we cannot guarantee that a future `composite_instances` will refer
   * to `simple_instance_status` in the same way.
   *
   * Note that this map needs to only contain relevant sources; it is not necessary to define every
   * other source/composite.
   *
   * To define these relationships, return a map like:
   * @example
   * Map.of(
   *  COMPOSITE_INSTANCES_ID, Map.of("inst_stat", SIMPLE_INSTANCE_STATUS_ID),
   *  COMPOSITE_ITEM_DETAILS_ID, Map.of("instance_status", SIMPLE_INSTANCE_STATUS_ID)
   * )
   */
  public Map<UUID, Map<String, UUID>> getEntityTypeSourceMaps() {
    return Map.of();
  }

  /**
   * Perform changes to fields and conditions within the FQL. This enables settings values for
   * the field's name, operator, and value together. Note that renaming fields must be done both
   * here and in {@link #migrateFieldName(MigratableFqlFieldOnly)}.
   *
   * @example
   * if ("gone".equals(condition.field())) {
   *   return SingleFieldMigrationResult.removed();
   * } else {
   *   return SingleFieldMigrationResult.noop(condition);
   * }
   *
   * @param state The state of type {@link S}, shared by all method calls for this query
   * @param condition The {@link MigratableFqlFieldAndCondition} to migrate
   * @return The migration result
   */
  public SingleFieldMigrationResult<MigratableFqlFieldAndCondition> migrateFql(
    S state,
    MigratableFqlFieldAndCondition condition
  ) {
    return SingleFieldMigrationResult.noop(condition);
  }

  /**
   * Perform changes to field names from the query's field list. Note that renaming fields must be
   * done both here and in {@link #migrateFql(MigratableFqlFieldAndCondition)}.
   *
   * @example
   * if ("moved".equals(field.field())) {
   *   return SingleFieldMigrationResult.withField(field.withField("new_name"));
   * } else {
   *   return SingleFieldMigrationResult.noop(field);
   * }
   *
   * @param state The state of type {@link S}, shared by all method calls for this query
   * @param field The {@link MigratableFqlFieldOnly} to migrate
   * @return The migration result
   */
  public SingleFieldMigrationResult<MigratableFqlFieldOnly> migrateFieldName(S state, MigratableFqlFieldOnly field) {
    return SingleFieldMigrationResult.noop(field);
  }

  /**
   * Perform any additional changes after field and FQL migrations have been applied.
   *
   * @param state The state of type {@link S}, shared by all method calls for this query
   * @param query The {@link MigratableQueryInformation} after the rest of the migrations have been applied
   * @return The migration result to be returned
   */
  public MigratableQueryInformation additionalChanges(S state, MigratableQueryInformation query) {
    return query;
  }

  @Override
  public final MigratableQueryInformation apply(
    MigratableQueryInformation query,
    Map<UUID, Map<String, UUID>> customEntityTypeMappings
  ) {
    S state = getStartingState();

    Map<UUID, Map<String, UUID>> sourceMappings = new HashMap<>(getEntityTypeSourceMaps());
    sourceMappings.putAll(customEntityTypeMappings);

    MigrationResult<String> fqlMigration = MigrationUtils.migrateFql(
      query.entityTypeId(),
      query.fqlQuery(),
      f -> this.migrateFql(state, f),
      sourceMappings
    );
    MigrationResult<List<String>> fieldsMigration = MigrationUtils.migrateFieldNames(
      query.entityTypeId(),
      query.fields(),
      f -> this.migrateFieldName(state, f),
      sourceMappings
    );

    return additionalChanges(
      state,
      query
        .withFields(fieldsMigration.result())
        .withFqlQuery(fqlMigration.result())
        .withWarnings(
          // we do this magic to ensure uniqueness as some warnings can be duplicated
          // (e.g. deprecated field in both the FQL and field list)
          Stream
            .concat(
              StreamUtils.fromNullable(query.warnings()).flatMap(Collection::stream),
              Stream.concat(fqlMigration.warnings().stream(), fieldsMigration.warnings().stream())
            )
            .distinct()
            .filter(Objects::nonNull)
            .toList()
        )
        .withHadBreakingChanges(
          query.hadBreakingChanges() || fqlMigration.hadBreakingChange() || fieldsMigration.hadBreakingChange()
        )
    );
  }
}
