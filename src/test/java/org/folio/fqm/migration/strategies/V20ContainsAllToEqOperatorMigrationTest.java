package org.folio.fqm.migration.strategies;
import java.util.List;
import java.util.UUID;

import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.junit.jupiter.params.provider.Arguments;
public class V20ContainsAllToEqOperatorMigrationTest extends TestTemplate {

  private static final UUID TEST_ENTITY_TYPE_ID = UUID.fromString("a9112682-958f-576c-b46c-d851abc62cd1");

  @Override
  public MigrationStrategy getStrategy() {
    return new V20ContainsAllToEqOperatorMigration();
  }

  @Override
  public List<Arguments> getExpectedTransformations() {
    return List.of(
      Arguments.of(
        "Query with no contains_all operators (no-op)",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"field1\": {\"$eq\": \"value1\"}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"field1\": {\"$eq\": \"value1\"}}")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Simple contains_all to $and of eq migration",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"arrayField\": {\"$contains_all\": [\"value1\", \"value2\"]}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"$and\": [ {\"arrayField\": {\"$eq\": \"value1\"}}, {\"arrayField\": {\"$eq\": \"value2\"}} ]}")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Field with multiple operators including contains_all",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{ \"$and\": [ { \"arrayField\": { \"$contains_all\": [\"value1\", \"value2\"] } }, { \"arrayField\": { \"$ne\": \"exclude\" } } ] }")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{ \"$and\": [ { \"arrayField\": { \"$eq\": \"value1\" } }, { \"arrayField\": { \"$eq\": \"value2\" } }, { \"arrayField\": { \"$ne\": \"exclude\" } } ] }")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Single value array",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"arrayField\": {\"$contains_all\": [\"singleValue\"]}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"arrayField\": {\"$eq\": \"singleValue\"}}")
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
