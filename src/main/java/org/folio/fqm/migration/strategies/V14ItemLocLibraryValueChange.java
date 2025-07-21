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
 * Version 14 -> 15, handles a change in the effective library name and code fields in the items entity type.
 * <p>
 * Originally, values for this field were stored as the library's ID, however, this was changed to use the
 * name/code itself. As such, we need to update queries to map the original IDs to their corresponding values.
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-884 for adding this migration
 */
@Log4j2
@RequiredArgsConstructor
public class V14ItemLocLibraryValueChange implements MigrationStrategy {

    public static final String SOURCE_VERSION = "14";
    public static final String TARGET_VERSION = "15";

    private static final UUID ITEM_ENTITY_TYPE_ID = UUID.fromString("d0213d22-32cf-490f-9196-d81c3c66e53f");
    private static final List<String> FIELD_NAMES = List.of("loclibrary.code", "loclibrary.name");

    private final LocationUnitsClient locationUnitsClient;

    @Override
    public String getLabel() {
        return "V14 -> V15 item effective library name/code value transformation (MODFQMMGR-884)";
    }

    @Override
    public boolean applies(String version) {
        return SOURCE_VERSION.equals(version);
    }

    @Override
    public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
        List<Warning> warnings = new ArrayList<>(query.warnings());
        AtomicReference<List<LibraryLocation>> records = new AtomicReference<>();

        return query.withFqlQuery(
                MigrationUtils.migrateFqlValues(
                    query.fqlQuery(),
                    originalVersion -> TARGET_VERSION,
                    key -> ITEM_ENTITY_TYPE_ID.equals(query.entityTypeId()) && FIELD_NAMES.contains(key),
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
                                    // Some values may already be correct, if name/code and ID were previously mapped to same field.
                                    boolean existsAsValue = records.get().stream().anyMatch(r ->
                                            (key.contains(".name") && r.name().equals(value)) ||
                                                    (key.contains(".code") && r.code().equals(value))
                                    );

                                    if (existsAsValue) {
                                        return value;
                                    } else {
                                        warnings.add(ValueBreakingWarning.builder()
                                                .field(key)
                                                .value(value)
                                                .fql(fql.get())
                                                .build());
                                        return null;
                                    }
                                });
                    }
                )
        ).withWarnings(warnings);
    }
}
