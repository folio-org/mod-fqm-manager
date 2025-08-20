package org.folio.fqm.migration.strategies;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.service.FqlService;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationUtils;

/**
 * Version 20 -> 21 migration to map legacy 'contains_all' operator to the new 'eq' operator.
 * <p>
 * This migration handles the transition from the old operator structure to the new one:
 * - $contains_all -> $eq (direct operator rename)
 * <p>
 * The migration preserves the array values and simply changes the operator name,
 * since the semantic meaning of "must equal all values" is effectively "equals".
 * This is not a breaking change.
 */
@Log4j2
@RequiredArgsConstructor
public class V20ContainsAllToEqOperatorMigration implements MigrationStrategy {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String getMaximumApplicableVersion() {
    return "20"; // version 20 -> 21
  }

  @Override
  public String getLabel() {
    return "V20 -> V21 Contains All to Eq operator migration";
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
    String migratedFql = MigrationUtils.migrateFql(
      query.fqlQuery(),
      (result, fieldName, fieldConditions) -> {
        ObjectNode conditions = (ObjectNode) fieldConditions;

        if (conditions.has("$contains_all")) {
          JsonNode values = conditions.get("$contains_all");
          if (values.isArray()) {
            ArrayNode andConditions = objectMapper.createArrayNode();

            values.forEach(value -> {
              ObjectNode eqCondition = objectMapper.createObjectNode();
              ObjectNode fieldEq = objectMapper.createObjectNode();
              fieldEq.set("$eq", value);
              eqCondition.set(fieldName, fieldEq);
              andConditions.add(eqCondition);
            });

            // Replace whole field condition with $and
            ObjectNode andWrapper = objectMapper.createObjectNode();
            andWrapper.set("$and", andConditions);
            result.setAll(andWrapper);

            log.info("Migrated $contains_all to $and of $eq for field: {}", fieldName);
          }
        } else {
          // If no $contains_all, just copy over unchanged
          result.set(fieldName, conditions);
        }
      }
    );

    return query.withFqlQuery(migratedFql);
  }
}
