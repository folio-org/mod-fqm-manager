package org.folio.fqm.migration.strategies;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.LocationUnitsClient;
import org.folio.fqm.client.LocationUnitsClient.LibraryLocation;
import org.folio.fqm.migration.MigrationUtils;
import org.folio.fqm.migration.types.MigratableFqlFieldAndCondition;
import org.folio.fqm.migration.types.MigrationResult;
import org.folio.fqm.migration.types.SingleFieldMigrationResult;
import org.folio.fqm.migration.warnings.ValueBreakingWarning;

/**
 * Base class for library value change migrations.
 * Handles transformation of library ID values to their corresponding names/codes.
 */
@Log4j2
@RequiredArgsConstructor
public abstract class AbstractLibraryValueChangeMigration
  extends AbstractRegularMigrationStrategy<AtomicReference<List<LibraryLocation>>> {

  private final LocationUnitsClient locationUnitsClient;

  protected abstract UUID getEntityTypeId();

  protected abstract List<String> getFieldNames();

  @Override
  public AtomicReference<List<LibraryLocation>> getStartingState() {
    return new AtomicReference<>();
  }

  @Override
  public SingleFieldMigrationResult<MigratableFqlFieldAndCondition> migrateFql(
    AtomicReference<List<LibraryLocation>> state,
    MigratableFqlFieldAndCondition cond
  ) {
    return MigrationUtils.migrateFqlValues(
      cond,
      condition -> getEntityTypeId().equals(condition.entityTypeId()) && getFieldNames().contains(condition.field()),
      (MigratableFqlFieldAndCondition condition, String value, Supplier<String> fql) -> {
        if (state.get() == null) {
          state.set(locationUnitsClient.getLibraries().loclibs());

          log.info("Fetched {} records from API", state.get().size());
        }

        return state
          .get()
          .stream()
          .filter(r -> r.id().equals(value))
          .findFirst()
          .map(loclib -> {
            if (condition.field().contains(".name")) {
              return loclib.name();
            } else {
              return loclib.code();
            }
          })
          .map(MigrationResult::withResult)
          .orElseGet(() -> {
            // some of these may already be the correct value, as both the name and ID fields
            // got mapped to the same place. If the name is already being used, we want to make
            // sure not to discard it
            boolean existsAsValue = state
              .get()
              .stream()
              .anyMatch(r ->
                (condition.field().contains(".name") && r.name().equals(value)) ||
                (condition.field().contains(".code") && r.code().equals(value))
              );

            if (existsAsValue) {
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
    );
  }
}
