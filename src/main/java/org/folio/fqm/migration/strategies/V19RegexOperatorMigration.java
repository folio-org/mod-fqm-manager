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
 * Version 19 -> 20 migration to map legacy regex operator to the new 'contains' / 'starts_with' operators.
 */
@Log4j2
@RequiredArgsConstructor
public class V19RegexOperatorMigration implements MigrationStrategy {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String getMaximumApplicableVersion() {
    return "19";
  }

  @Override
  public String getLabel() {
    return "V19 -> V20 Regex operator migration";
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
    String migratedFql = MigrationUtils.migrateFql(
      query.fqlQuery(),
      (result, fieldName, fieldConditions) -> {
        ObjectNode conditions = (ObjectNode) fieldConditions;
        ObjectNode migratedConditions = objectMapper.createObjectNode();

        conditions.properties().forEach(operatorEntry -> {
          String operator = operatorEntry.getKey();
          JsonNode operatorValue = operatorEntry.getValue();

          String newOperator = operator;
          if ("$regex".equals(operator)) {
            if (operatorValue.isTextual() && operatorValue.asText().startsWith("^")) {
              newOperator = "$starts_with";
              // Remove the leading ^ character for starts_with
              operatorValue = objectMapper.convertValue(operatorValue.asText().substring(1), JsonNode.class);
            } else {
              newOperator = "$contains";
            }
          }
          migratedConditions.set(newOperator, operatorValue);

          if (!operator.equals(newOperator)) {
            log.info("Migrated {} to {} for field: {}", operator, newOperator, fieldName);
          }
        });

        result.set(fieldName, migratedConditions);
      }
    );

    return query.withFqlQuery(migratedFql);
  }
}

