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
import org.folio.fqm.migration.warnings.OperatorBreakingWarning;
import org.folio.fqm.migration.warnings.Warning;

import java.util.ArrayList;
import java.util.List;

/**
 * Version 17 -> 18 migration to map legacy 'contains_any' operator to the new 'in' operator.
 * 
 * This migration handles the transition from the old operator structure to the new one:
 * - $contains_any -> $in (direct operator rename)
 * - $not_contains_any -> $nin (direct operator rename)
 * 
 * The migration preserves the array values and simply changes the operator name,
 * as the semantic meaning remains the same. This is not a breaking change.
 */
@Log4j2
@RequiredArgsConstructor
public class V17ContainsAnyToInOperatorMigration implements MigrationStrategy {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String getMaximumApplicableVersion() {
    return "17"; // Migrate from version 17 to 18
  }

  @Override
  public String getLabel() {
    return "V17 -> V18 Contains Any to In operator migration";
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
    List<Warning> warnings = new ArrayList<>(query.warnings());

    String migratedFql = MigrationUtils.migrateFql(
      query.fqlQuery(),      (result, fieldName, fieldConditions) -> {
        ObjectNode conditions = (ObjectNode) fieldConditions;
        ObjectNode migratedConditions = objectMapper.createObjectNode();
        // Track if any changes were made for this field
        final boolean[] hasChanges = {false};

        // Iterate through all operators for this field
        conditions.properties().forEach(operatorEntry -> {
          String operator = operatorEntry.getKey();
          JsonNode operatorValue = operatorEntry.getValue();

          switch (operator) {
            case "$contains_any" -> {
              // Migrate $contains_any to $in
              migratedConditions.set("$in", operatorValue);
              hasChanges[0] = true;
              log.info("Migrated $contains_any to $in for field: {}", fieldName);
            }
            case "$not_contains_any" -> {
              // Migrate $not_contains_any to $nin
              migratedConditions.set("$nin", operatorValue);
              hasChanges[0] = true;
              log.info("Migrated $not_contains_any to $nin for field: {}", fieldName);
            }
            default -> {
              // Keep all other operators as-is
              migratedConditions.set(operator, operatorValue);
            }
          }
        });

        // Only add the field if it has conditions after migration
        if (migratedConditions.size() > 0) {
          result.set(fieldName, migratedConditions);
        }

        // Add informational warning if changes were made
        if (hasChanges[0]) {
          warnings.add(
            OperatorBreakingWarning.builder()
              .field(fieldName)
              .operator("$contains_any/$not_contains_any")
              .fql(fieldConditions.toPrettyString())
              .build()
          );
        }
      }
    );

    return query
      .withFqlQuery(migratedFql)
      .withHadBreakingChanges(query.hadBreakingChanges() || !warnings.isEmpty())
      .withWarnings(warnings);
  }
}
