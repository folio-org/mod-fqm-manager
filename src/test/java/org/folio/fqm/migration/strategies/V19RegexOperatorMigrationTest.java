package org.folio.fqm.migration.strategies;

import java.util.List;
import java.util.UUID;

import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.junit.jupiter.params.provider.Arguments;

public class V19RegexOperatorMigrationTest extends TestTemplate {

  private static final UUID TEST_ENTITY_TYPE_ID = UUID.fromString("b9112682-958f-576c-b46c-d851abc62cd2");

  @Override
  public MigrationStrategy getStrategy() {
    return new V19RegexOperatorMigration();
  }

  @Override
  public List<Arguments> getExpectedTransformations() {
    return List.of(
      Arguments.of(
        "No regex operator (no-op)",
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
        "Simple regex to contains migration",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"field1\": {\"$regex\": \"abc\"}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"field1\": {\"$contains\": \"abc\"}}")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Regex with ^ to starts_with migration",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"field1\": {\"$regex\": \"^abc\"}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"field1\": {\"$starts_with\": \"abc\"}}")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Regex containing ^",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"field1\": {\"$regex\": \"foo^bar\"}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{\"field1\": {\"$contains\": \"foo^bar\"}}")
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
      ),
      Arguments.of(
        "Regex operator inside $and",
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{ \"$and\": [ { \"field1\": { \"$regex\": \"^foo\" } }, { \"field2\": { \"$regex\": \"bar\" } } ] }")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(TEST_ENTITY_TYPE_ID)
          .fqlQuery("{ \"$and\": [ { \"field1\": { \"$starts_with\": \"foo\" } }, { \"field2\": { \"$contains\": \"bar\" } } ] }")
          .fields(List.of())
          .build()
      )
    );
  }
}
