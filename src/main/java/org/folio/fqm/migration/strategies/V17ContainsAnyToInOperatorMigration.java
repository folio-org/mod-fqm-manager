package org.folio.fqm.migration.strategies;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.service.FqlService;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationUtils;

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
public class V17ContainsAnyToInOperatorMigration implements MigrationStrategy {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String getMaximumApplicableVersion() {
    return "17"; // Assuming this is for version 17 -> 18 transition
  }

  @Override
  public String getLabel() {
    return "V17 -> V18 Contains Any to In operator migration";
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
    String migratedFql = MigrationUtils.migrateFql(
      query.fqlQuery(),
      (result, fieldName, fieldConditions) -> {
        ObjectNode conditions = (ObjectNode) fieldConditions;
        ObjectNode migratedConditions = objectMapper.createObjectNode();

        // Iterate through all operators for this field
        conditions.properties().forEach(operatorEntry -> {
          String operator = operatorEntry.getKey();
          JsonNode operatorValue = operatorEntry.getValue();

          String newOperator;
          if ("$contains_any".equals(operator)) {
            newOperator = "$in";
          } else {
            newOperator = operator;
          }
          migratedConditions.set(newOperator, operatorValue);

          if (!operator.equals(newOperator)) {
            log.info("Migrated {} to {} for field: {}", operator, newOperator, fieldName);
          }
        });

        // Only add the field if it has conditions after migration
        if (migratedConditions.size() > 0) {
          result.set(fieldName, migratedConditions);
        }
      }
    );

    return query.withFqlQuery(migratedFql);
  }
}
