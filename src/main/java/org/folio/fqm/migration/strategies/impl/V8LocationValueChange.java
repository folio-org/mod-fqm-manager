package org.folio.fqm.migration.strategies.impl;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.LocationsClient;
import org.folio.fqm.client.LocationsClient.Location;
import org.folio.fqm.migration.MigrationUtils;
import org.folio.fqm.migration.strategies.AbstractRegularMigrationStrategy;
import org.folio.fqm.migration.types.MigratableFqlFieldAndCondition;
import org.folio.fqm.migration.types.MigrationResult;
import org.folio.fqm.migration.types.SingleFieldMigrationResult;
import org.folio.fqm.migration.warnings.ValueBreakingWarning;

/**
 * Version 8 -> 9, handles a change in the temporary/permanent/effective location name fields in the items and holdings entity types.
 *
 * Originally, values for this field were stored as the locations's ID, however, this was changed to use the
 * name itself. As such, we need to update queries to map the original IDs to their corresponding names.
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-602 for adding this migration
 */
@Log4j2
@RequiredArgsConstructor
public class V8LocationValueChange extends AbstractRegularMigrationStrategy<AtomicReference<List<Location>>> {

  private static final UUID HOLDINGS_ENTITY_TYPE_ID = UUID.fromString("8418e512-feac-4a6a-a56d-9006aab31e33");
  private static final UUID ITEMS_ENTITY_TYPE_ID = UUID.fromString("d0213d22-32cf-490f-9196-d81c3c66e53f");
  private static final List<String> FIELD_NAMES = List.of(
    "effective_location.name",
    "permanent_location.name",
    "temporary_location.name"
  );

  private final LocationsClient locationsClient;

  @Override
  public String getMaximumApplicableVersion() {
    return "8";
  }

  @Override
  public String getLabel() {
    return "V8 -> V9 item and holdings location name value transformation (MODFQMMGR-602)";
  }

  @Override
  public AtomicReference<List<Location>> getStartingState() {
    return new AtomicReference<>();
  }

  @Override
  public SingleFieldMigrationResult<MigratableFqlFieldAndCondition> migrateFql(
    AtomicReference<List<Location>> state,
    MigratableFqlFieldAndCondition cond
  ) {
    return MigrationUtils
      .migrateFqlValues(
        condition ->
          (
            HOLDINGS_ENTITY_TYPE_ID.equals(condition.entityTypeId()) ||
            ITEMS_ENTITY_TYPE_ID.equals(condition.entityTypeId())
          ) &&
          FIELD_NAMES.contains(condition.field()),
        (MigratableFqlFieldAndCondition condition, String value, Supplier<String> fql) -> {
          if (state.get() == null) {
            state.set(locationsClient.getLocations().locations());

            log.info("Fetched {} records from API", state.get().size());
          }

          return state
            .get()
            .stream()
            .filter(r -> r.id().equals(value))
            .findFirst()
            .map(Location::name)
            .map(MigrationResult::withResult)
            .orElseGet(() -> {
              // some of these may already be the correct value, as both the name and ID fields
              // got mapped to the same place. If the name is already being used, we want to make
              // sure not to discard it
              boolean existsAsName = state.get().stream().anyMatch(r -> r.name().equals(value));

              if (existsAsName) {
                return MigrationResult.withResult(value);
              } else {
                return MigrationResult
                  .<String>removed()
                  .withWarnings(
                    List.of(
                      ValueBreakingWarning.builder().field(condition.getFullField()).value(value).fql(fql.get()).build()
                    )
                  )
                  .withHadBreakingChange(true);
              }
            });
        }
      )
      .apply(cond);
  }
}
