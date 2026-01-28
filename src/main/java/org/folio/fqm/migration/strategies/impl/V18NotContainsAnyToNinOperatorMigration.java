package org.folio.fqm.migration.strategies.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.migration.strategies.AbstractRegularMigrationStrategy;
import org.folio.fqm.migration.types.MigratableFqlFieldAndCondition;
import org.folio.fqm.migration.types.SingleFieldMigrationResult;

/**
 * Version 18 -> 19 migration to map legacy 'not_contains_any' operator to the new 'nin' operator.
 * <p>
 * This migration handles the transition from the old operator structure to the new one:
 * - $not_contains_any -> $nin (direct operator rename)
 * <p>
 * The migration preserves the array values and simply changes the operator name,
 * as the semantic meaning remains the same. This is not a breaking change.
 */
@Log4j2
@RequiredArgsConstructor
public class V18NotContainsAnyToNinOperatorMigration extends AbstractRegularMigrationStrategy<Void> {

  @Override
  public String getMaximumApplicableVersion() {
    return "18"; // Assuming this is for version 18 -> 19 transition
  }

  @Override
  public String getLabel() {
    return "V18 -> V19 $not_contains_any to $nin operator migration";
  }

  @Override
  public SingleFieldMigrationResult<MigratableFqlFieldAndCondition> migrateFql(
    Void state,
    MigratableFqlFieldAndCondition condition
  ) {
    return switch (condition.operator()) {
      case "$not_contains_any" -> {
        log.info("Migrating $not_contains_any to $nin for field: {}", condition.getFullField());
        yield SingleFieldMigrationResult.withField(condition.withOperator("$nin"));
      }
      default -> SingleFieldMigrationResult.noop(condition);
    };
  }
}
