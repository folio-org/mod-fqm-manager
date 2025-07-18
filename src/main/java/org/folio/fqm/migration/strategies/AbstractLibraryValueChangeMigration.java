package org.folio.fqm.migration.strategies;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.service.FqlService;
import org.folio.fqm.client.LocationUnitsClient;
import org.folio.fqm.client.LocationUnitsClient.LibraryLocation;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationUtils;
import org.folio.fqm.migration.warnings.ValueBreakingWarning;
import org.folio.fqm.migration.warnings.Warning;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Base class for library value change migrations.
 * Handles transformation of library ID values to their corresponding names/codes.
 */
@Log4j2
@RequiredArgsConstructor
public abstract class AbstractLibraryValueChangeMigration implements MigrationStrategy {

  private final LocationUnitsClient locationUnitsClient;

  protected abstract UUID getEntityTypeId();
  protected abstract List<String> getFieldNames();

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
    List<Warning> warnings = new ArrayList<>(query.warnings());

    AtomicReference<List<LibraryLocation>> records = new AtomicReference<>();

    return query
      .withFqlQuery(
        MigrationUtils.migrateFqlValues(
          query.fqlQuery(),
          key -> getEntityTypeId().equals(query.entityTypeId()) && getFieldNames().contains(key),
          (String key, String value, Supplier<String> fql) -> {
            if (records.get() == null) {
              records.set(locationUnitsClient.getLibraries().loclibs());

              log.info("Fetched {} records from API", records.get().size());
            }

            return records
              .get()
              .stream()
              .filter(r -> r.id().equals(value))
              .findFirst()
              .map(loclib -> {
                if (key.contains(".name")) {
                  return loclib.name();
                } else {
                  return loclib.code();
                }
              })
              .orElseGet(() -> {
                // some of these may already be the correct value, as both the name and ID fields
                // got mapped to the same place. If the name is already being used, we want to make
                // sure not to discard it
                boolean existsAsValue = records
                  .get()
                  .stream()
                  .anyMatch(r ->
                    (key.contains(".name") && r.name().equals(value)) ||
                    (key.contains(".code") && r.code().equals(value))
                  );

                if (existsAsValue) {
                  return value;
                } else {
                  warnings.add(ValueBreakingWarning.builder().field(key).value(value).fql(fql.get()).build());
                  return null;
                }
              });
          }
        )
      )
      .withHadBreakingChanges(query.hadBreakingChanges() || !warnings.isEmpty())
      .withWarnings(warnings);
  }
}

