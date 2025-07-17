package org.folio.fqm.migration.strategies;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.folio.fql.service.FqlService;
import org.folio.fqm.client.LocationUnitsClient;
import org.folio.fqm.client.LocationUnitsClient.LibraryLocation;
import org.folio.fqm.client.LocationUnitsClient.LibraryLocationsResponse;
import org.folio.fqm.client.LocationsClient;
import org.folio.fqm.client.LocationsClient.Location;
import org.folio.fqm.client.LocationsClient.LocationsResponse;
import org.folio.fqm.client.ModesOfIssuanceClient;
import org.folio.fqm.client.ModesOfIssuanceClient.ModeOfIssuance;
import org.folio.fqm.client.ModesOfIssuanceClient.ModesOfIssuanceResponse;
import org.folio.fqm.client.PatronGroupsClient;
import org.folio.fqm.client.PatronGroupsClient.PatronGroup;
import org.folio.fqm.client.PatronGroupsClient.PatronGroupsResponse;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.warnings.ValueBreakingWarning;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
// tests all five together, as they're all very similar
class V6V7V8V9V14ValueChangeConsolidatedTest extends TestTemplate {

  @Mock
  LocationsClient locationsClient;

  @Mock
  LocationUnitsClient locationUnitsClient;

  @Mock
  ModesOfIssuanceClient modesOfIssuanceClient;

  @Mock
  PatronGroupsClient patronGroupsClient;

  @BeforeEach
  void setup() {
    lenient()
      .when(locationsClient.getLocations())
      .thenReturn(new LocationsResponse(List.of(new Location("id1", "name1"), new Location("id2", "name2"))));
    lenient()
      .when(locationUnitsClient.getLibraries())
      .thenReturn(
        new LibraryLocationsResponse(
          List.of(new LibraryLocation("id1", "name1", "name1"), new LibraryLocation("id2", "name2", "name2"))
        )
      );
    lenient()
      .when(modesOfIssuanceClient.getModesOfIssuance())
      .thenReturn(
        new ModesOfIssuanceResponse(List.of(new ModeOfIssuance("id1", "name1"), new ModeOfIssuance("id2", "name2")))
      );
    lenient()
      .when(patronGroupsClient.getGroups())
      .thenReturn(new PatronGroupsResponse(List.of(new PatronGroup("id1", "name1"), new PatronGroup("id2", "name2"))));
  }

  @Override
  public MigrationStrategy getStrategy() {
    List<MigrationStrategy> strategies = List.of(
      new V6ModeOfIssuanceValueChange(modesOfIssuanceClient),
      new V7PatronGroupsValueChange(patronGroupsClient),
      new V8LocationValueChange(locationsClient),
      new V9LocLibraryValueChange(locationUnitsClient),
      new V14ItemLocLibraryValueChange(locationUnitsClient)
    );

    return new MigrationStrategy() {
      @Override
      public String getLabel() {
        throw new UnsupportedOperationException("Unimplemented method 'getLabel'");
      }

      @Override
      public String getMaximumApplicableVersion() {
        return "source";
      }

      @Override
      public boolean applies(@NotNull MigratableQueryInformation queryInformation) {
        throw new UnsupportedOperationException("Unimplemented method 'applies'");
      }

      @Override
      public MigratableQueryInformation apply(
        FqlService fqlService,
        MigratableQueryInformation migratableQueryInformation
      ) {
        for (MigrationStrategy strategy : strategies) {
          migratableQueryInformation = strategy.apply(fqlService, migratableQueryInformation);
        }
        return migratableQueryInformation;
      }
    };
  }

  @Override
  public List<Arguments> getExpectedTransformations() {
    List<Pair<String, String>> nonMatchingFields = List.of(
      Pair.of("a9112682-958f-576c-b46c-d851abc62cd1", "instance.mode_of_issuance_name"),
      Pair.of("a9112682-958f-576c-b46c-d851abc62cd1", "groups.group"),
      Pair.of("d6ad5e4e-a72b-5d10-bb54-e230d2fa8435", "not_a_relevant_field")
    );

    List<Triple<String, String, Runnable>> matchingFields = List.of(
      Triple.of(
        "6b08439b-4f8e-4468-8046-ea620f5cfb74",
        "instance.mode_of_issuance_name",
        () -> verify(modesOfIssuanceClient, times(1)).getModesOfIssuance()
      ),
      Triple.of(
        "d6729885-f2fb-4dc7-b7d0-a865a7f461e4",
        "groups.group",
        () -> verify(patronGroupsClient, times(1)).getGroups()
      ),
      Triple.of(
        "ddc93926-d15a-4a45-9d9c-93eadc3d9bbf",
        "groups.group",
        () -> verify(patronGroupsClient, times(1)).getGroups()
      ),
      Triple.of(
        "d0213d22-32cf-490f-9196-d81c3c66e53f",
        "effective_location.name",
        () -> verify(locationsClient, times(1)).getLocations()
      ),
      Triple.of(
        "d0213d22-32cf-490f-9196-d81c3c66e53f",
        "permanent_location.name",
        () -> verify(locationsClient, times(1)).getLocations()
      ),
      Triple.of(
        "d0213d22-32cf-490f-9196-d81c3c66e53f",
        "temporary_location.name",
        () -> verify(locationsClient, times(1)).getLocations()
      ),
      Triple.of(
        "d0213d22-32cf-490f-9196-d81c3c66e53f",
        "loclibrary.name",
        () -> verify(locationUnitsClient, times(1)).getLibraries()
      ),
      Triple.of(
        "d0213d22-32cf-490f-9196-d81c3c66e53f",
        "loclibrary.code",
        () -> verify(locationUnitsClient, times(1)).getLibraries()
      ),
      Triple.of(
        "8418e512-feac-4a6a-a56d-9006aab31e33",
        "permanent_location.name",
        () -> verify(locationsClient, times(1)).getLocations()
      ),
      Triple.of(
        "8418e512-feac-4a6a-a56d-9006aab31e33",
        "temporary_location.name",
        () -> verify(locationsClient, times(1)).getLocations()
      ),
      Triple.of(
        "8418e512-feac-4a6a-a56d-9006aab31e33",
        "effective_library.name",
        () -> verify(locationUnitsClient, times(1)).getLibraries()
      ),
      Triple.of(
        "8418e512-feac-4a6a-a56d-9006aab31e33",
        "effective_library.code",
        () -> verify(locationUnitsClient, times(1)).getLibraries()
      )
    );

    return Stream
      .concat(
        nonMatchingFields
          .stream()
          .map(pair ->
            Arguments.of(
              "Query with non-matching entity type",
              MigratableQueryInformation
                .builder()
                .entityTypeId(UUID.fromString(pair.getLeft()))
                .fqlQuery("{\"" + pair.getRight() + "\": {\"$ne\": \"invalid\"}}")
                .fields(List.of())
                .build(),
              MigratableQueryInformation
                .builder()
                .entityTypeId(UUID.fromString(pair.getLeft()))
                .fqlQuery("{\"" + pair.getRight() + "\": {\"$ne\": \"invalid\"}}")
                .fields(List.of())
                .build(),
              (Consumer<MigratableQueryInformation>) (
                transformed -> {
                  verifyNoInteractions(modesOfIssuanceClient);
                  verifyNoInteractions(patronGroupsClient);
                }
              )
            )
          ),
        matchingFields
          .stream()
          .flatMap(triple ->
            List
              .of(
                Arguments.of(
                  "Query with valid ID",
                  MigratableQueryInformation
                    .builder()
                    .entityTypeId(UUID.fromString(triple.getLeft()))
                    .fqlQuery(
                      """
                      {
                        "%s": {"$ne": "id1"},
                        "not_a_relevant_field": {"$ne": "id1"}
                      }
                      """.formatted(
                          triple.getMiddle()
                        )
                    )
                    .fields(List.of())
                    .build(),
                  MigratableQueryInformation
                    .builder()
                    .entityTypeId(UUID.fromString(triple.getLeft()))
                    .fqlQuery(
                      """
                      {
                        "%s": {"$ne": "name1"},
                        "not_a_relevant_field": {"$ne": "id1"}
                      }
                      """.formatted(
                          triple.getMiddle()
                        )
                    )
                    .fields(List.of())
                    .build(),
                  (Consumer<MigratableQueryInformation>) (transformed -> triple.getRight().run())
                ),
                Arguments.of(
                  "Query with multiple valid MOIs",
                  MigratableQueryInformation
                    .builder()
                    .entityTypeId(UUID.fromString(triple.getLeft()))
                    .fqlQuery(
                      "{\"%s\": {\"$eq\": \"name1\", \"$ne\": \"id1\", \"$in\": [\"id2\", \"invalid\"] }}".formatted(
                          triple.getMiddle()
                        )
                    )
                    .fields(List.of())
                    .build(),
                  MigratableQueryInformation
                    .builder()
                    .entityTypeId(UUID.fromString(triple.getLeft()))
                    .fqlQuery(
                      "{\"%s\": {\"$eq\": \"name1\", \"$ne\": \"name1\", \"$in\": [\"name2\"]}}".formatted(
                          triple.getMiddle()
                        )
                    )
                    .fields(List.of())
                    .warning(
                      ValueBreakingWarning
                        .builder()
                        .field(triple.getMiddle())
                        .value("invalid")
                        .fql("{\n  \"$in\" : [ \"id2\", \"invalid\" ]\n}")
                        .build()
                    )
                    .build(),
                  (Consumer<MigratableQueryInformation>) (transformed -> triple.getRight().run())
                )
              )
              .stream()
          )
      )
      .toList();
  }
}
