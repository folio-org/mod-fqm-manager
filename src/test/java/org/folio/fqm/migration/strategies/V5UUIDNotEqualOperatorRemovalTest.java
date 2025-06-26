package org.folio.fqm.migration.strategies;

import java.util.List;
import java.util.UUID;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.warnings.OperatorBreakingWarning;
import org.junit.jupiter.params.provider.Arguments;

class V5UUIDNotEqualOperatorRemovalTest extends TestTemplate {

  @Override
  public MigrationStrategy getStrategy() {
    return new V5UUIDNotEqualOperatorRemoval();
  }

  @Override
  public List<Arguments> getExpectedTransformations() {
    return List.of(
      Arguments.of(
        "Query with non-matching entity type",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("a9112682-958f-576c-b46c-d851abc62cd1"))
          .fqlQuery("{\"users.id\": {\"$ne\": \"cf1baaa9-a55c-5ac7-bcbd-d27fbc477303\"}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("a9112682-958f-576c-b46c-d851abc62cd1"))
          .fqlQuery("{\"users.id\": {\"$ne\": \"cf1baaa9-a55c-5ac7-bcbd-d27fbc477303\"}}")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Query with no impacted fields",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery("{\"users.username\": {\"$ne\": \"admin\"}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery("{\"users.username\": {\"$ne\": \"admin\"}}")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Query with one condition, impacted",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery("{\"users.id\": {\"$ne\": \"cf1baaa9-a55c-5ac7-bcbd-d27fbc477303\"}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery("{}")
          .fields(List.of())
          .warning(
            OperatorBreakingWarning
              .builder()
              .field("users.id")
              .operator("$ne")
              .fql("{\n  \"$ne\" : \"cf1baaa9-a55c-5ac7-bcbd-d27fbc477303\"\n}")
              .build()
          )
          .build()
      ),
      Arguments.of(
        "Query with multiple conditions on one field, one condition impacted",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery(
            """
            {
              "users.id": {
                "$ne": "cf1baaa9-a55c-5ac7-bcbd-d27fbc477303",
                "$le": "ffffffff-a55c-5ac7-bcbd-d27fbc477303"
              }
            }
            """
          )
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery("{\"users.id\": {\"$le\": \"ffffffff-a55c-5ac7-bcbd-d27fbc477303\"}}")
          .fields(List.of())
          .warning(
            OperatorBreakingWarning
              .builder()
              .field("users.id")
              .operator("$ne")
              .fql("{\n  \"$ne\" : \"cf1baaa9-a55c-5ac7-bcbd-d27fbc477303\"\n}")
              .build()
          )
          .build()
      ),
      Arguments.of(
        "Query with multiple conditions on multiple fields, one entire field impacted",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery(
            """
            {
              "users.id": {
                "$ne": "cf1baaa9-a55c-5ac7-bcbd-d27fbc477303"
              },
              "groups.id": {
                "$eq": "cf1baaa9-a55c-5ac7-bcbd-d27fbc477303"
              },
              "users.username": {
                "$ne": "admin"
              }
            }
            """
          )
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery(
            """
            {
              "groups.id": {
                "$eq": "cf1baaa9-a55c-5ac7-bcbd-d27fbc477303"
              },
              "users.username": {
                "$ne": "admin"
              }
            }
            """)
          .fields(List.of())
          .warning(
            OperatorBreakingWarning
              .builder()
              .field("users.id")
              .operator("$ne")
              .fql("{\n  \"$ne\" : \"cf1baaa9-a55c-5ac7-bcbd-d27fbc477303\"\n}")
              .build()
          )
          .build()
      )
    );
  }
}
