package org.folio.fqm.migration.strategies;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.folio.fqm.client.ModesOfIssuanceClient;
import org.folio.fqm.client.ModesOfIssuanceClient.ModeOfIssuance;
import org.folio.fqm.client.ModesOfIssuanceClient.ModesOfIssuanceResponse;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.warnings.ValueBreakingWarning;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V6ModeOfIssuanceValueChangeTest extends TestTemplate {

  @Mock
  ModesOfIssuanceClient modesOfIssuanceClient;

  @BeforeEach
  public void setup() {
    lenient()
      .when(modesOfIssuanceClient.getModesOfIssuance())
      .thenReturn(
        new ModesOfIssuanceResponse(List.of(new ModeOfIssuance("id1", "name1"), new ModeOfIssuance("id2", "name2")))
      );
  }

  @Override
  public MigrationStrategy getStrategy() {
    return new V6ModeOfIssuanceValueChange(modesOfIssuanceClient);
  }

  @Override
  public List<Arguments> getExpectedTransformations() {
    return List.of(
      Arguments.of(
        "Query with non-matching entity type",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("a9112682-958f-576c-b46c-d851abc62cd1"))
          .fqlQuery("{\"instance.mode_of_issuance_name\": {\"$ne\": \"invalid\"}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("a9112682-958f-576c-b46c-d851abc62cd1"))
          .fqlQuery("{\"instance.mode_of_issuance_name\": {\"$ne\": \"invalid\"}, \"_version\":\"7\"}")
          .fields(List.of())
          .build(),
        (Consumer<MigratableQueryInformation>) (transformed -> verifyNoInteractions(modesOfIssuanceClient))
      ),
      Arguments.of(
        "Query with valid MOI",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("6b08439b-4f8e-4468-8046-ea620f5cfb74"))
          .fqlQuery(
            """
            {
              "instance.mode_of_issuance_name": {"$ne": "id1"},
              "not_a_relevant_field": {"$ne": "id1"}
            }
            """
          )
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("6b08439b-4f8e-4468-8046-ea620f5cfb74"))
          .fqlQuery(
            """
            {
              "instance.mode_of_issuance_name": {"$ne": "name1"},
              "not_a_relevant_field": {"$ne": "id1"},
              "_version": "7"
            }
            """
          )
          .fields(List.of())
          .build(),
        (Consumer<MigratableQueryInformation>) (
          transformed -> verify(modesOfIssuanceClient, times(1)).getModesOfIssuance()
        )
      ),
      Arguments.of(
        "Query with multiple valid MOIs",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("6b08439b-4f8e-4468-8046-ea620f5cfb74"))
          .fqlQuery("{\"instance.mode_of_issuance_name\": {\"$ne\": \"id1\", \"$in\": [\"id2\", \"invalid\"] }}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("6b08439b-4f8e-4468-8046-ea620f5cfb74"))
          .fqlQuery(
            "{\"instance.mode_of_issuance_name\": {\"$ne\": \"name1\", \"$in\": [\"name2\"]}, \"_version\":\"7\"}"
          )
          .fields(List.of())
          .warning(
            ValueBreakingWarning
              .builder()
              .field("instance.mode_of_issuance_name")
              .value("invalid")
              .fql("{\n  \"$in\" : [ \"id2\", \"invalid\" ]\n}")
              .build()
          )
          .build(),
        (Consumer<MigratableQueryInformation>) (
          transformed -> verify(modesOfIssuanceClient, times(1)).getModesOfIssuance()
        )
      )
    );
  }
}
