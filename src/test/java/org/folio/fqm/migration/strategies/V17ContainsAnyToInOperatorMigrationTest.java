package org.folio.fqm.migration.strategies;

import java.util.List;
import java.util.UUID;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.junit.jupiter.params.provider.Arguments;

class V17ContainsAnyToInOperatorMigrationTest extends TestTemplate {

  private static final UUID TEST_ENTITY_TYPE_ID = UUID.fromString("a9112682-958f-576c-b46c-d851abc62cd1");

  @Override
  public MigrationStrategy getStrategy() {
    return new V17ContainsAnyToInOperatorMigration();
  }

  @Override
  public List<Arguments> getExpectedTransformations() {
    return List.of(
      Arguments.of(
        "Query with no contains_any operators (no-op)",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"field1\": {\"$eq\": \"value1\"}, \"field2\": {\"$in\": [\"value2\", \"value3\"]}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"field1\": {\"$eq\": \"value1\"}, \"field2\": {\"$in\": [\"value2\", \"value3\"]}}")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Simple contains_any to in migration",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"arrayField\": {\"$contains_any\": [\"value1\", \"value2\"]}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"arrayField\": {\"$in\": [\"value1\", \"value2\"]}}")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Simple not_contains_any to nin migration",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"arrayField\": {\"$not_contains_any\": [\"value1\", \"value2\"]}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"arrayField\": {\"$nin\": [\"value1\", \"value2\"]}}")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Field with multiple operators including contains_any",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"arrayField\": {\"$contains_any\": [\"value1\", \"value2\"], \"$ne\": \"exclude\"}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"arrayField\": {\"$in\": [\"value1\", \"value2\"], \"$ne\": \"exclude\"}}")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Complex nested query with both operators",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery(
            """
            {
              "$and": [
                {"tags": {"$contains_any": ["urgent", "high-priority"]}},
                {"excludeTags": {"$not_contains_any": ["archived", "deleted"]}},
                {"status": {"$eq": "active"}}
              ]
            }
            """
          )
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery(
            """
            {
              "$and": [
                {"tags": {"$in": ["urgent", "high-priority"]}},
                {"excludeTags": {"$nin": ["archived", "deleted"]}},
                {"status": {"$eq": "active"}}
              ]
            }
            """
          )
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Multiple fields with legacy operators",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery(
            """
            {
              "field1": {"$contains_any": ["value1", "value2"]},
              "field2": {"$not_contains_any": ["value3", "value4"]},
              "field3": {"$eq": "keep-me"}
            }
            """
          )
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery(
            """
            {
              "field1": {"$in": ["value1", "value2"]},
              "field2": {"$nin": ["value3", "value4"]},
              "field3": {"$eq": "keep-me"}
            }
            """
          )
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Single value array",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"arrayField\": {\"$contains_any\": [\"singleValue\"]}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"arrayField\": {\"$in\": [\"singleValue\"]}}")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Empty query",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{}")
          .fields(List.of())
          .build()
      )
    );
  }
}
