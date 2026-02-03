package org.folio.fqm.migration.strategies.impl;

import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.migration.strategies.AbstractRegularMigrationStrategy;
import org.folio.fqm.migration.types.MigratableFqlFieldAndCondition;
import org.folio.fqm.migration.types.SingleFieldMigrationResult;

/**
 * Version 20 -> 21 migration to remove legacy 'contains_all' operator to ANDed 'eq' operator.
 *
 * This migration handles the transition from the old operator structure to the new one:
 * - $contains_all -> $eq[]
 *
 * The migration preserves the array values and simply changes the operator name,
 * since the semantic meaning of "must equal all values" is effectively "equals".
 * This is not a breaking change.
 */
@Log4j2
@RequiredArgsConstructor
public class V20ContainsAllToEqOperatorMigration extends AbstractRegularMigrationStrategy<Void> {

  @Override
  public String getMaximumApplicableVersion() {
    return "20"; // version 20 -> 21
  }

  @Override
  public String getLabel() {
    return "V20 -> V21 $contains_all to $eq operator migration";
  }

  @Override
  public SingleFieldMigrationResult<MigratableFqlFieldAndCondition> migrateFql(
    Void state,
    MigratableFqlFieldAndCondition condition
  ) {
    return switch (condition.operator()) {
      case "$contains_all" -> {
        ArrayNode values = (ArrayNode) condition.value();
        yield new SingleFieldMigrationResult<>(
          values.valueStream().map(value -> condition.withOperator("$eq").withValue(value)).toList(),
          List.of(),
          false
        );
      }
      default -> SingleFieldMigrationResult.noop(condition);
    };
  }
}
