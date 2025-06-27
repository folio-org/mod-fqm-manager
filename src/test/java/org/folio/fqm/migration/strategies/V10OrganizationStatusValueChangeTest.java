package org.folio.fqm.migration.strategies;

import java.util.List;
import java.util.UUID;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.junit.jupiter.params.provider.Arguments;

class V10OrganizationStatusValueChangeTest extends TestTemplate {

  @Override
  public MigrationStrategy getStrategy() {
    return new V10OrganizationStatusValueChange();
  }

  @Override
  public List<Arguments> getExpectedTransformations() {
    return List.of(
      Arguments.of(
        "Query with non-matching entity type",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("a9112682-958f-576c-b46c-d851abc62cd1"))
          .fqlQuery("{\"status\": {\"$ne\": \"active\"}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("a9112682-958f-576c-b46c-d851abc62cd1"))
          .fqlQuery("{\"status\": {\"$ne\": \"active\"}}")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Query with all possible cases",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("b5ffa2e9-8080-471a-8003-a8c5a1274503"))
          .fqlQuery(
            """
            {"$and": [
              { "status": { "$eq": "active" }},
              { "status": { "$eq": "what" }},
              { "status": { "$in": ["active", "inactive", "pending"] }},
              { "other_field": { "$eq": "active" }}
            ]}
            """
          )
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("b5ffa2e9-8080-471a-8003-a8c5a1274503"))
          .fqlQuery(
            """
            {
              "$and": [
                { "status": { "$eq": "Active" }},
                { "status": { "$eq": "what" }},
                { "status": { "$in": ["Active", "Inactive", "Pending"] }},
                { "other_field": { "$eq": "active" }}
              ]
            }
            """
          )
          .fields(List.of())
          .build()
      )
    );
  }
}
