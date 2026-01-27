package org.folio.fqm.migration.strategies.impl;

import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.migration.strategies.AbstractRegularMigrationStrategy;
import org.folio.fqm.migration.types.MigratableFqlFieldAndCondition;
import org.folio.fqm.migration.types.SingleFieldMigrationResult;

/**
 * Version 21 -> 22 migration to remove legacy 'not_contains_all' operator
 * and replace it with ANDed 'ne' operators.
 *
 * This migration handles the transition from the old operator structure to the new one:
 * - $not_contains_all -> $ne[]
 *
 * The migration preserves the array values and simply changes the operator name,
 * since the semantic meaning of "must not equal any of the values" is effectively
 * multiple "not equals" conditions.
 * This is not a breaking change.
 */
@Log4j2
@RequiredArgsConstructor
public class V21NotContainsAllToNeqOperatorMigration extends AbstractRegularMigrationStrategy<Void> {

  @Override
  public String getMaximumApplicableVersion() {
    return "21"; // version 21 -> 22
  }

  @Override
  public String getLabel() {
    return "V21 -> V22 $not_contains_all to $ne operator migration";
  }

  @Override
  public SingleFieldMigrationResult<MigratableFqlFieldAndCondition> migrateFql(
    Void state,
    MigratableFqlFieldAndCondition condition
  ) {
    return switch (condition.operator()) {
      case "$not_contains_all" -> {
        ArrayNode values = (ArrayNode) condition.value();
        yield new SingleFieldMigrationResult<>(
          values.valueStream().map(value -> condition.withOperator("$ne").withValue(value)).toList(),
          List.of(),
          false
        );
      }
      default -> SingleFieldMigrationResult.noop(condition);
    };
  }
}
