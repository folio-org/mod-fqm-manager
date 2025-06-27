package org.folio.fqm.migration.strategies;

import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.UUID;
import org.folio.fqm.client.OrganizationsClient;
import org.folio.fqm.client.OrganizationsClient.Organization;
import org.folio.fqm.client.OrganizationsClient.OrganizationsResponse;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.warnings.OperatorBreakingWarning;
import org.folio.fqm.migration.warnings.ValueBreakingWarning;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V11OrganizationCodeOperatorChangeTest extends TestTemplate {

  @Mock
  OrganizationsClient organizationsClient;

  @BeforeEach
  public void setup() {
    lenient()
      .when(organizationsClient.getOrganizations())
      .thenReturn(
        new OrganizationsResponse(
          List.of(new Organization("id1", "code1", "name1"), new Organization("id2", "code2", "name2"))
        )
      );
  }

  @Override
  public MigrationStrategy getStrategy() {
    return new V11OrganizationNameCodeOperatorChange(organizationsClient);
  }

  @Override
  public List<Arguments> getExpectedTransformations() {
    return List.of(
      Arguments.of(
        "Query with non-matching entity type",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("a9112682-958f-576c-b46c-d851abc62cd1"))
          .fqlQuery("{\"code\": {\"$ne\": \"active\"}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("a9112682-958f-576c-b46c-d851abc62cd1"))
          .fqlQuery("{\"code\": {\"$ne\": \"active\"}}")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Query with found $eq",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("b5ffa2e9-8080-471a-8003-a8c5a1274503"))
          .fqlQuery("""
            { "code": { "$eq": "code1" }, "name": { "$eq": "name1" } }
            """)
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("b5ffa2e9-8080-471a-8003-a8c5a1274503"))
          .fqlQuery(
            """
            { "code": { "$eq": "id1" }, "name": { "$eq": "id1" } }
            """
          )
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "Query with case-insensitive found $ne, not found $eq, and extra operators to ignore",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("b5ffa2e9-8080-471a-8003-a8c5a1274503"))
          .fqlQuery(
            """
            { "code": { "$eq": "invalid", "$ne": "CoDe1", "$empty": true, "$unknown": 1234 } }
            """
          )
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("b5ffa2e9-8080-471a-8003-a8c5a1274503"))
          .fqlQuery(
            """
            { "code": { "$ne": "id1", "$empty": true, "$unknown": 1234 } }
            """
          )
          .fields(List.of())
          .warning(
            ValueBreakingWarning.builder().field("code").value("invalid").fql("{\n  \"$eq\" : \"invalid\"\n}").build()
          )
          .build()
      ),
      Arguments.of(
        "Query with removed $regex",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("b5ffa2e9-8080-471a-8003-a8c5a1274503"))
          .fqlQuery("""
            { "ignore_me": { "$eq": "z" }, "code": { "$regex": "^foo" } }
            """)
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("b5ffa2e9-8080-471a-8003-a8c5a1274503"))
          .fqlQuery("""
            { "ignore_me": { "$eq": "z" } }
            """)
          .fields(List.of())
          .warning(
            OperatorBreakingWarning
              .builder()
              .field("code")
              .operator("$regex")
              .fql("{\n  \"$regex\" : \"^foo\"\n}")
              .build()
          )
          .build()
      )
    );
  }
}
