package org.folio.fqm.migration.strategies;

import java.util.Collection;
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

public abstract class AbstractRegularMigrationStrategy<State> implements MigrationStrategy {

  /**
   * Called before any migration starts; this variable will be retained and passed to each call
   * of the other methods. This is useful if you want to cache values or other state across calls
   * (such as loading values from an external API).
   */
  public State getStartingState() {
    return null;
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
   */
  public SingleFieldMigrationResult<MigratableFqlFieldAndCondition> migrateFql(
    State state,
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
   */
  public SingleFieldMigrationResult<MigratableFqlFieldOnly> migrateFieldName(
    State state,
    MigratableFqlFieldOnly field
  ) {
    return SingleFieldMigrationResult.noop(field);
  }

  /**
   * Perform any additional changes after field and FQL migrations have been applied.
   */
  public MigratableQueryInformation additionalChanges(State state, MigratableQueryInformation query) {
    return query;
  }

  @Override
  public final MigratableQueryInformation apply(MigratableQueryInformation query) {
    State state = getStartingState();

    MigrationResult<String> fqlMigration = MigrationUtils.migrateFql(
      query.entityTypeId(),
      query.fqlQuery(),
      f -> this.migrateFql(state, f)
    );
    MigrationResult<List<String>> fieldsMigration = MigrationUtils.migrateFieldNames(
      query.entityTypeId(),
      query.fields(),
      f -> this.migrateFieldName(state, f)
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
