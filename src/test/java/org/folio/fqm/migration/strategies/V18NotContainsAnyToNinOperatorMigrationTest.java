package org.folio.fqm.migration.strategies;

import java.util.List;
import java.util.UUID;

import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.junit.jupiter.params.provider.Arguments;

public class V18NotContainsAnyToNinOperatorMigrationTest extends TestTemplate {

  private static final UUID TEST_ENTITY_TYPE_ID = UUID.fromString("a9112682-958f-576c-b46c-d851abc62cd1");

  @Override
  public MigrationStrategy getStrategy() {
    return new V18NotContainsAnyToNinOperatorMigration();
  }

  @Override
  public List<Arguments> getExpectedTransformations() {
    return List.of(
      Arguments.of(
        "Query with no not_contains_any operators (no-op)",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"field1\": {\"$eq\": \"value1\"}, \"field2\": {\"$nin\": [\"value2\", \"value3\"]}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"field1\": {\"$eq\": \"value1\"}, \"field2\": {\"$nin\": [\"value2\", \"value3\"]}}")
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
        "Field with multiple operators including not_contains_any",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{ \"$and\": [ { \"arrayField\": { \"$not_contains_any\": [\"value1\", \"value2\"] } }, { \"arrayField\": { \"$ne\": \"exclude\" } } ] }")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{ \"$and\": [ { \"arrayField\": { \"$nin\": [\"value1\", \"value2\"] } }, { \"arrayField\": { \"$ne\": \"exclude\" } } ] }")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Single value array",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"arrayField\": {\"$not_contains_any\": [\"singleValue\"]}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"arrayField\": {\"$nin\": [\"singleValue\"]}}")
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
