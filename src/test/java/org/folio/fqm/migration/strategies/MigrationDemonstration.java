package org.folio.fqm.migration.strategies;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.folio.fql.service.FqlService;
import org.folio.fqm.migration.MigratableQueryInformation;

import java.util.List;
import java.util.UUID;

/**
 * Demonstration class showing how the ContainsAnyToInOperatorMigration works
 * This is a POC to show the migration approach for mapping old operators to new ones.
 */
public class MigrationDemonstration {

  public static void main(String[] args) throws JsonProcessingException {
    // Initialize the migration strategy
    ContainsAnyToInOperatorMigration migration = new ContainsAnyToInOperatorMigration();
    FqlService fqlService = new FqlService();
    ObjectMapper objectMapper = new ObjectMapper();

    // Example 1: Simple contains_any migration
    System.out.println("=== Example 1: Simple contains_any to in migration ===");
    String originalQuery1 = """
      {
        "arrayField": {"$contains_any": ["value1", "value2"]},
        "statusField": {"$eq": "active"}
      }
      """;

    MigratableQueryInformation input1 = MigratableQueryInformation.builder()
      .entityTypeId(UUID.randomUUID())
      .fqlQuery(originalQuery1)
      .warnings(List.of())
      .hadBreakingChanges(false)
      .build();

    MigratableQueryInformation result1 = migration.apply(fqlService, input1);

    System.out.println("Original Query:");
    System.out.println(prettyPrint(objectMapper, originalQuery1));
    System.out.println("\nMigrated Query:");
    System.out.println(prettyPrint(objectMapper, result1.fqlQuery()));
    System.out.println("Breaking Changes: " + result1.hadBreakingChanges());
    System.out.println("Warnings: " + result1.warnings().size());

    // Example 2: Complex query with both operators
    System.out.println("\n\n=== Example 2: Complex query with both contains_any operators ===");
    String originalQuery2 = """
      {
        "$and": [
          {"tags": {"$contains_any": ["urgent", "high-priority"]}},
          {"excludeTags": {"$not_contains_any": ["archived", "deleted"]}},
          {"status": {"$eq": "active"}},
          {"createdDate": {"$gte": "2023-01-01"}}
        ]
      }
      """;

    MigratableQueryInformation input2 = MigratableQueryInformation.builder()
      .entityTypeId(UUID.randomUUID())
      .fqlQuery(originalQuery2)
      .warnings(List.of())
      .hadBreakingChanges(false)
      .build();

    MigratableQueryInformation result2 = migration.apply(fqlService, input2);

    System.out.println("Original Query:");
    System.out.println(prettyPrint(objectMapper, originalQuery2));
    System.out.println("\nMigrated Query:");
    System.out.println(prettyPrint(objectMapper, result2.fqlQuery()));
    System.out.println("Breaking Changes: " + result2.hadBreakingChanges());
    System.out.println("Warnings: " + result2.warnings().size());

    // Example 3: Query with no legacy operators (should remain unchanged)
    System.out.println("\n\n=== Example 3: Query with no legacy operators ===");
    String originalQuery3 = """
      {
        "title": {"$eq": "Test Document"},
        "categories": {"$in": ["category1", "category2"]},
        "excludeCategories": {"$nin": ["hidden"]}
      }
      """;

    MigratableQueryInformation input3 = MigratableQueryInformation.builder()
      .entityTypeId(UUID.randomUUID())
      .fqlQuery(originalQuery3)
      .warnings(List.of())
      .hadBreakingChanges(false)
      .build();

    MigratableQueryInformation result3 = migration.apply(fqlService, input3);

    System.out.println("Original Query:");
    System.out.println(prettyPrint(objectMapper, originalQuery3));
    System.out.println("\nMigrated Query:");
    System.out.println(prettyPrint(objectMapper, result3.fqlQuery()));
    System.out.println("Breaking Changes: " + result3.hadBreakingChanges());
    System.out.println("Warnings: " + result3.warnings().size());

    System.out.println("\n\n=== Migration Summary ===");
    System.out.println("Migration Strategy: " + migration.getLabel());
    System.out.println("Maximum Applicable Version: " + migration.getMaximumApplicableVersion());
    System.out.println("\nOperator Mappings:");
    System.out.println("- $contains_any → $in");
    System.out.println("- $not_contains_any → $nin");
  }

  private static String prettyPrint(ObjectMapper objectMapper, String json) throws JsonProcessingException {
    ObjectNode node = (ObjectNode) objectMapper.readTree(json);
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node.without("_version"));
  }
}
