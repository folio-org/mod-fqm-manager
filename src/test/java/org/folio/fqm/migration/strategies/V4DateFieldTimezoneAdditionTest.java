package org.folio.fqm.migration.strategies;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.folio.fqm.client.SettingsClient;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V4DateFieldTimezoneAdditionTest extends TestTemplate {

  @Mock
  SettingsClient settingsClient;

  @BeforeEach
  public void setup() {
    lenient().when(settingsClient.getTenantTimezone()).thenReturn(ZoneId.of("America/New_York"));
  }

  @Override
  public MigrationStrategy getStrategy() {
    return new V4DateFieldTimezoneAddition(settingsClient);
  }

  @Override
  public List<Arguments> getExpectedTransformations() {
    return List.of(
      Arguments.of(
        "Query with no dates should not hit configuration API",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("3ad4b672-a69c-5a80-b483-bbc77c29cbfd"))
          .fqlQuery("{\"name\": {\"$eq\":\"foo\"}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("3ad4b672-a69c-5a80-b483-bbc77c29cbfd"))
          .fqlQuery("{\"name\": {\"$eq\":\"foo\"}}")
          .fields(List.of())
          .build(),
        (Consumer<MigratableQueryInformation>) transformed -> verifyNoInteractions(settingsClient)
      ),
      Arguments.of(
        "Query with dates",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("3ad4b672-a69c-5a80-b483-bbc77c29cbfd"))
          .fqlQuery(
            """
            {
              "items.updated_date": {
                "$ne": "2024-05-01T12:34:56.000",
                "$eq": "2024-07-01",
                "$leq": "2024-11-01",
                "$empty": false
              },
              "items.last_check_in_date_time": {
                "$eq": "2024-01-01"
              },
              "items.created_date": {
                "$eq": "invalid"
              },
              "unrelated.field": {
                "$contains": "2024-07-01"
              }
            }
            """
          )
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("3ad4b672-a69c-5a80-b483-bbc77c29cbfd"))
          .fqlQuery(
            """
            {
              "items.updated_date": {
                "$ne": "2024-05-01T12:34:56.000",
                "$eq": "2024-07-01T04:00:00.000",
                "$leq": "2024-11-01T04:00:00.000",
                "$empty": false
              },
              "items.last_check_in_date_time": {
                "$eq": "2024-01-01T05:00:00.000"
              },
              "items.created_date": {
                "$eq": "invalid"
              },
              "unrelated.field": {
                "$contains": "2024-07-01"
              }
            }
            """
          )
          .fields(List.of())
          .build(),
        (Consumer<MigratableQueryInformation>) (
          transformed -> verify(settingsClient, times(1)).getTenantTimezone()
        )
      )
    );
  }
}
