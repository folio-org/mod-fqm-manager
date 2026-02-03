package org.folio.fqm.migration.strategies.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.migration.strategies.AbstractRegularMigrationStrategy;
import org.folio.fqm.migration.types.MigratableFqlFieldAndCondition;
import org.folio.fqm.migration.types.SingleFieldMigrationResult;

/**
 * Version 17 -> 18 migration to map legacy 'contains_any' operator to the new 'in' operator.
 * <p>
 * This migration handles the transition from the old operator structure to the new one:
 * - $contains_any -> $in (direct operator rename)
 * <p>
 * The migration preserves the array values and simply changes the operator name,
 * as the semantic meaning remains the same. This is not a breaking change.
 */
@Log4j2
@RequiredArgsConstructor
public class V17ContainsAnyToInOperatorMigration extends AbstractRegularMigrationStrategy<Void> {

  @Override
  public String getMaximumApplicableVersion() {
    return "17"; // Assuming this is for version 17 -> 18 transition
  }

  @Override
  public String getLabel() {
    return "V17 -> V18 $contains_any to $in operator migration";
  }

  @Override
  public SingleFieldMigrationResult<MigratableFqlFieldAndCondition> migrateFql(
    Void state,
    MigratableFqlFieldAndCondition condition
  ) {
    return switch (condition.operator()) {
      case "$contains_any" -> {
        log.info("Migrating $contains_any to $in for field: {}", condition.getFullField());
        yield SingleFieldMigrationResult.withField(condition.withOperator("$in"));
      }
      default -> SingleFieldMigrationResult.noop(condition);
    };
  }
}
