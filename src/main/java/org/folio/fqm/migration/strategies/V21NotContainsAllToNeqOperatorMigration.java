package org.folio.fqm.migration.strategies;

import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.service.FqlService;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationUtils;

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
public class V21NotContainsAllToNeqOperatorMigration implements MigrationStrategy {

  @Override
  public String getMaximumApplicableVersion() {
    return "21"; // version 21 -> 22
  }

  @Override
  public String getLabel() {
    return "V21 -> V22 $not_contains_all to $ne operator migration";
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
    String migratedFql = MigrationUtils.migrateAndReshapeFql(
      query.fqlQuery(),
      original -> {
        if ("$not_contains_all".equals(original.operator())) {
          ArrayNode values = (ArrayNode) original.value();
          return values
            .valueStream()
            .map(value -> new MigrationUtils.FqlFieldAndCondition(original.field(), "$ne", value))
            .toList();
        } else {
          return List.of(original);
        }
      }
    );

    return query.withFqlQuery(migratedFql);
  }
}

