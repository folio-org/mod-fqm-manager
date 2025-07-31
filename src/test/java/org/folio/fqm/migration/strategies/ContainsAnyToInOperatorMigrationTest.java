package org.folio.fqm.migration.strategies;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.folio.fql.service.FqlService;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.warnings.OperatorBreakingWarning;
import org.folio.fqm.migration.warnings.Warning;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContainsAnyToInOperatorMigrationTest {

  private ContainsAnyToInOperatorMigration migration;
  private FqlService fqlService;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    migration = new ContainsAnyToInOperatorMigration();
    fqlService = new FqlService();
    objectMapper = new ObjectMapper();
  }

  @Test
  void shouldMigrateContainsAnyToIn() throws JsonProcessingException {
    String originalQuery = """
      {
        "arrayField": {"$contains_any": ["value1", "value2"]},
        "otherField": {"$eq": "test"}
      }
      """;

    String expectedQuery = """
      {
        "arrayField": {"$in": ["value1", "value2"]},
        "otherField": {"$eq": "test"}
      }
      """;

    MigratableQueryInformation input = MigratableQueryInformation.builder()
      .entityTypeId(UUID.randomUUID())
      .fqlQuery(originalQuery)
      .warnings(List.of())
      .hadBreakingChanges(false)
      .build();

    MigratableQueryInformation result = migration.apply(fqlService, input);

    assertEquals(
      objectMapper.readTree(expectedQuery),
      ((ObjectNode) objectMapper.readTree(result.fqlQuery())).without("_version")
    );

    assertTrue(result.hadBreakingChanges());
    assertEquals(1, result.warnings().size());
    assertTrue(result.warnings().get(0) instanceof OperatorBreakingWarning);
  }

  @Test
  void shouldMigrateNotContainsAnyToNin() throws JsonProcessingException {
    String originalQuery = """
      {
        "arrayField": {"$not_contains_any": ["value1", "value2"]},
        "otherField": {"$ne": "test"}
      }
      """;

    String expectedQuery = """
      {
        "arrayField": {"$nin": ["value1", "value2"]},
        "otherField": {"$ne": "test"}
      }
      """;

    MigratableQueryInformation input = MigratableQueryInformation.builder()
      .entityTypeId(UUID.randomUUID())
      .fqlQuery(originalQuery)
      .warnings(List.of())
      .hadBreakingChanges(false)
      .build();

    MigratableQueryInformation result = migration.apply(fqlService, input);

    assertEquals(
      objectMapper.readTree(expectedQuery),
      ((ObjectNode) objectMapper.readTree(result.fqlQuery())).without("_version")
    );

    assertTrue(result.hadBreakingChanges());
    assertEquals(1, result.warnings().size());
    assertTrue(result.warnings().get(0) instanceof OperatorBreakingWarning);
  }

  @Test
  void shouldMigrateBothContainsAnyOperators() throws JsonProcessingException {
    String originalQuery = """
      {
        "arrayField1": {"$contains_any": ["value1", "value2"]},
        "arrayField2": {"$not_contains_any": ["value3", "value4"]},
        "normalField": {"$eq": "keep-me"}
      }
      """;

    String expectedQuery = """
      {
        "arrayField1": {"$in": ["value1", "value2"]},
        "arrayField2": {"$nin": ["value3", "value4"]},
        "normalField": {"$eq": "keep-me"}
      }
      """;

    MigratableQueryInformation input = MigratableQueryInformation.builder()
      .entityTypeId(UUID.randomUUID())
      .fqlQuery(originalQuery)
      .warnings(List.of())
      .hadBreakingChanges(false)
      .build();

    MigratableQueryInformation result = migration.apply(fqlService, input);

    assertEquals(
      objectMapper.readTree(expectedQuery),
      ((ObjectNode) objectMapper.readTree(result.fqlQuery())).without("_version")
    );

    assertTrue(result.hadBreakingChanges());
    assertEquals(2, result.warnings().size());
    assertTrue(result.warnings().stream().allMatch(w -> w instanceof OperatorBreakingWarning));
  }

  @Test
  void shouldHandleComplexNestedQuery() throws JsonProcessingException {
    String originalQuery = """
      {
        "$and": [
          {"field1": {"$eq": "some value"}},
          {"field2": {"$contains_any": ["value1", "value2"]}},
          {"field3": {"$not_contains_any": ["value3"]}},
          {"field4": {"$gt": 10}}
        ]
      }
      """;

    String expectedQuery = """
      {
        "$and": [
          {"field1": {"$eq": "some value"}},
          {"field2": {"$in": ["value1", "value2"]}},
          {"field3": {"$nin": ["value3"]}},
          {"field4": {"$gt": 10}}
        ]
      }
      """;

    MigratableQueryInformation input = MigratableQueryInformation.builder()
      .entityTypeId(UUID.randomUUID())
      .fqlQuery(originalQuery)
      .warnings(List.of())
      .hadBreakingChanges(false)
      .build();

    MigratableQueryInformation result = migration.apply(fqlService, input);

    assertEquals(
      objectMapper.readTree(expectedQuery),
      ((ObjectNode) objectMapper.readTree(result.fqlQuery())).without("_version")
    );

    assertTrue(result.hadBreakingChanges());
    assertEquals(2, result.warnings().size());
  }

  @Test
  void shouldHandleFieldWithMultipleOperators() throws JsonProcessingException {
    String originalQuery = """
      {
        "arrayField": {
          "$contains_any": ["value1", "value2"],
          "$ne": "test"
        }
      }
      """;

    String expectedQuery = """
      {
        "arrayField": {
          "$in": ["value1", "value2"],
          "$ne": "test"
        }
      }
      """;

    MigratableQueryInformation input = MigratableQueryInformation.builder()
      .entityTypeId(UUID.randomUUID())
      .fqlQuery(originalQuery)
      .warnings(List.of())
      .hadBreakingChanges(false)
      .build();

    MigratableQueryInformation result = migration.apply(fqlService, input);

    assertEquals(
      objectMapper.readTree(expectedQuery),
      ((ObjectNode) objectMapper.readTree(result.fqlQuery())).without("_version")
    );

    assertTrue(result.hadBreakingChanges());
    assertEquals(1, result.warnings().size());
  }

  @Test
  void shouldNotMigrateQueriesWithoutLegacyOperators() throws JsonProcessingException {
    String originalQuery = """
      {
        "field1": {"$eq": "value1"},
        "field2": {"$in": ["value2", "value3"]},
        "field3": {"$nin": ["value4"]}
      }
      """;

    MigratableQueryInformation input = MigratableQueryInformation.builder()
      .entityTypeId(UUID.randomUUID())
      .fqlQuery(originalQuery)
      .warnings(List.of())
      .hadBreakingChanges(false)
      .build();

    MigratableQueryInformation result = migration.apply(fqlService, input);

    assertEquals(
      objectMapper.readTree(originalQuery),
      ((ObjectNode) objectMapper.readTree(result.fqlQuery())).without("_version")
    );

    assertFalse(result.hadBreakingChanges());
    assertEquals(0, result.warnings().size());
  }

  @Test
  void shouldPreserveExistingWarnings() throws JsonProcessingException {
    String originalQuery = """
      {
        "arrayField": {"$contains_any": ["value1", "value2"]}
      }
      """;

    Warning existingWarning = OperatorBreakingWarning.builder()
      .field("someOtherField")
      .operator("$regex")
      .fql("existing warning")
      .build();

    MigratableQueryInformation input = MigratableQueryInformation.builder()
      .entityTypeId(UUID.randomUUID())
      .fqlQuery(originalQuery)
      .warnings(List.of(existingWarning))
      .hadBreakingChanges(true)
      .build();

    MigratableQueryInformation result = migration.apply(fqlService, input);

    assertTrue(result.hadBreakingChanges());
    assertEquals(2, result.warnings().size());
    assertTrue(result.warnings().contains(existingWarning));
  }

  @Test
  void shouldReturnCorrectVersionAndLabel() {
    assertEquals("12", migration.getMaximumApplicableVersion());
    assertEquals("V12 -> V13 Contains Any to In operator migration (POC)", migration.getLabel());
  }

  @Test
  void shouldHandleEmptyQuery() throws JsonProcessingException {
    String originalQuery = "{}";

    MigratableQueryInformation input = MigratableQueryInformation.builder()
      .entityTypeId(UUID.randomUUID())
      .fqlQuery(originalQuery)
      .warnings(List.of())
      .hadBreakingChanges(false)
      .build();

    MigratableQueryInformation result = migration.apply(fqlService, input);

    assertEquals(
      objectMapper.readTree(originalQuery),
      ((ObjectNode) objectMapper.readTree(result.fqlQuery())).without("_version")
    );

    assertFalse(result.hadBreakingChanges());
    assertEquals(0, result.warnings().size());
  }

  @Test
  void shouldHandleSingleValueArrays() throws JsonProcessingException {
    String originalQuery = """
      {
        "arrayField": {"$contains_any": ["singleValue"]}
      }
      """;

    String expectedQuery = """
      {
        "arrayField": {"$in": ["singleValue"]}
      }
      """;

    MigratableQueryInformation input = MigratableQueryInformation.builder()
      .entityTypeId(UUID.randomUUID())
      .fqlQuery(originalQuery)
      .warnings(List.of())
      .hadBreakingChanges(false)
      .build();

    MigratableQueryInformation result = migration.apply(fqlService, input);

    assertEquals(
      objectMapper.readTree(expectedQuery),
      ((ObjectNode) objectMapper.readTree(result.fqlQuery())).without("_version")
    );

    assertTrue(result.hadBreakingChanges());
    assertEquals(1, result.warnings().size());
  }
}
