package org.folio.fqm.migration.strategies;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
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

/**
 * Version 9 -> 10, handles a change in the effective library name and code fields in the holdings entity type.
 *
 * Originally, values for this field were stored as the library's ID, however, this was changed to use the
 * name/code itself. As such, we need to update queries to map the original IDs to their corresponding values.
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-602 for adding this migration
 */
@Log4j2
@RequiredArgsConstructor
public class V9LocLibraryValueChange implements MigrationStrategy {

  private static final UUID HOLDINGS_ENTITY_TYPE_ID = UUID.fromString("8418e512-feac-4a6a-a56d-9006aab31e33");
  private static final List<String> FIELD_NAMES = List.of("effective_library.code", "effective_library.name");

  private final LocationUnitsClient locationUnitsClient;

  @Override
  public String getMaximumApplicableVersion() {
    return "9";
  }

  @Override
  public String getLabel() {
    return "V9 -> V10 holdings effective library name/code value transformation (MODFQMMGR-602)";
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
    List<Warning> warnings = new ArrayList<>(query.warnings());

    AtomicReference<List<LibraryLocation>> records = new AtomicReference<>();

    return query
      .withFqlQuery(
        MigrationUtils.migrateFqlValues(
          query.fqlQuery(),
          key -> HOLDINGS_ENTITY_TYPE_ID.equals(query.entityTypeId()) && FIELD_NAMES.contains(key),
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
