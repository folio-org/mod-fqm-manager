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
public class V20ContainsAllToEqOperatorMigration implements MigrationStrategy {

  @Override
  public String getMaximumApplicableVersion() {
    return "20"; // version 20 -> 21
  }

  @Override
  public String getLabel() {
    return "V20 -> V21 $contains_all to $eq operator migration";
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
    String migratedFql = MigrationUtils.migrateAndReshapeFql(
      query.fqlQuery(),
      original -> {
        if ("$contains_all".equals(original.operator())) {
          ArrayNode values = (ArrayNode) original.value();
          return values
            .valueStream()
            .map(value -> new MigrationUtils.FqlFieldAndCondition(original.field(), "$eq", value))
            .toList();
        } else {
          return List.of(original);
        }
      }
    );

    return query.withFqlQuery(migratedFql);
  }
}
