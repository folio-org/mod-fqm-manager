package org.folio.fqm.migration.strategies;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.service.FqlService;
import org.folio.fqm.client.PatronGroupsClient;
import org.folio.fqm.client.PatronGroupsClient.PatronGroup;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationUtils;
import org.folio.fqm.migration.warnings.ValueBreakingWarning;
import org.folio.fqm.migration.warnings.Warning;

/**
 * Version 7 -> 8, handles a change in the `groups.group` field in the users and loans entity types.
 *
 * Originally, values for this field were stored as the group's ID, however, this was changed to use the
 * name itself. As such, we need to update queries to map the original IDs to their corresponding names.
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-602 for adding this migration
 */
@Log4j2
@RequiredArgsConstructor
public class V7PatronGroupsValueChange implements MigrationStrategy {

  public static final String SOURCE_VERSION = "7";
  public static final String TARGET_VERSION = "8";

  private static final UUID LOANS_ENTITY_TYPE_ID = UUID.fromString("d6729885-f2fb-4dc7-b7d0-a865a7f461e4");
  private static final UUID USERS_ENTITY_TYPE_ID = UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf");
  private static final String FIELD_NAME = "groups.group";

  private final PatronGroupsClient patronGroupsClient;

  @Override
  public String getLabel() {
    return "V7 -> V8 patron group name value transformation (MODFQMMGR-602)";
  }

  @Override
  public boolean applies(String version) {
    return SOURCE_VERSION.equals(version);
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
    List<Warning> warnings = new ArrayList<>(query.warnings());

    AtomicReference<List<PatronGroup>> records = new AtomicReference<>();

    return query
      .withFqlQuery(
        MigrationUtils.migrateFqlValues(
          query.fqlQuery(),
          originalVersion -> TARGET_VERSION,
          key ->
            (LOANS_ENTITY_TYPE_ID.equals(query.entityTypeId()) || USERS_ENTITY_TYPE_ID.equals(query.entityTypeId())) &&
            FIELD_NAME.equals(key),
          (String key, String value, Supplier<String> fql) -> {
            if (records.get() == null) {
              records.set(patronGroupsClient.getGroups().usergroups());

              log.info("Fetched {} records from API", records.get().size());
            }

            return records
              .get()
              .stream()
              .filter(r -> r.id().equals(value))
              .findFirst()
              .map(PatronGroup::group)
              .orElseGet(() -> {
                // some of these may already be the correct value, as both the name and ID fields
                // got mapped to the same place. If the name is already being used, we want to make
                // sure not to discard it
                boolean existsAsName = records.get().stream().anyMatch(r -> r.group().equals(value));

                if (existsAsName) {
                  return value;
                } else {
                  warnings.add(ValueBreakingWarning.builder().field(key).value(value).fql(fql.get()).build());
                  return null;
                }
              });
          }
        )
      )
      .withWarnings(warnings);
  }
}
