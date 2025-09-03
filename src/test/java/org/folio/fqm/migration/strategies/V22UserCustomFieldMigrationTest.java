package org.folio.fqm.migration.strategies;

import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.junit.jupiter.params.provider.Arguments;

import java.util.*;

public class V22UserCustomFieldMigrationTest extends TestTemplate {

    private static final UUID COMPOSITE_USERS_ID = UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf");
    private static final UUID OTHER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

  @Override
  public MigrationStrategy getStrategy() {
    return new V22UserCustomFieldMigration();
  }

  @Override
  public List<Arguments> getExpectedTransformations() {
    return List.of(
      Arguments.of(
        "Query with no custom fields (no-op)",
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_USERS_ID)
          .fqlQuery("{\"field1\": {\"$eq\": \"value1\"}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_USERS_ID)
          .fqlQuery("{\"field1\": {\"$eq\": \"value1\"}}")
          .fields(List.of())
          .build()
      ),

      Arguments.of(
        "Query on an unrelated entity type (no-op)",
        MigratableQueryInformation
          .builder()
          .entityTypeId(OTHER_ID)
          .fqlQuery("{\"_custom_field_1\": {\"$eq\": \"value1\"}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(OTHER_ID)
          .fqlQuery("{\"_custom_field_1\": {\"$eq\": \"value1\"}}")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Query with custom and non-custom fields (custom field migrated)",
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_USERS_ID)
          .fqlQuery("{\"$and\": [ {\"_custom_field_1\": {\"$ne\": \"value1\"}}, {\"regular_field\": {\"$ne\": \"value2\"}} ]}")
          .fields(List.of("_custom_field_1", "regular_field", "_custom_field_2"))
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_USERS_ID)
          .fqlQuery("{\"$and\": [ {\"users._custom_field_1\": {\"$ne\": \"value1\"}}, {\"regular_field\": {\"$ne\": \"value2\"}} ]}")
          .fields(List.of("users._custom_field_1", "regular_field", "users._custom_field_2"))
          .build()
      )
    );
  }
}
