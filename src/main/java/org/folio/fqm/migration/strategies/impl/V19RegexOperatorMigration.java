package org.folio.fqm.migration.strategies.impl;

import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.migration.strategies.AbstractRegularMigrationStrategy;
import org.folio.fqm.migration.types.MigratableFqlFieldAndCondition;
import org.folio.fqm.migration.types.SingleFieldMigrationResult;

/**
 * Version 19 -> 20 migration to map legacy regex operator to the new 'contains' / 'starts_with' operators.
 */
@Log4j2
@RequiredArgsConstructor
public class V19RegexOperatorMigration extends AbstractRegularMigrationStrategy<Void> {

  @Override
  public String getMaximumApplicableVersion() {
    return "19";
  }

  @Override
  public String getLabel() {
    return "V19 -> V20 $regex operator migration";
  }

  @Override
  public SingleFieldMigrationResult<MigratableFqlFieldAndCondition> migrateFql(
    Void state,
    MigratableFqlFieldAndCondition condition
  ) {
    return switch (condition.operator()) {
      case "$regex" -> {
        if (condition.value().isTextual() && condition.value().asText().startsWith("^")) {
          log.info("Migrating $regex to $starts_with for field: {}", condition.getFullField());
          yield SingleFieldMigrationResult.withField(
            condition.withOperator("$starts_with").withValue(new TextNode(condition.value().asText().substring(1)))
          );
        } else {
          log.info("Migrating $regex to $contains for field: {}", condition.getFullField());
          yield SingleFieldMigrationResult.withField(condition.withOperator("$contains"));
        }
      }
      default -> SingleFieldMigrationResult.noop(condition);
    };
  }
}
