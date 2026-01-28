package org.folio.fqm.migration.strategies.impl;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.PatronGroupsClient;
import org.folio.fqm.client.PatronGroupsClient.PatronGroup;
import org.folio.fqm.migration.MigrationUtils;
import org.folio.fqm.migration.strategies.AbstractRegularMigrationStrategy;
import org.folio.fqm.migration.types.MigratableFqlFieldAndCondition;
import org.folio.fqm.migration.types.MigrationResult;
import org.folio.fqm.migration.types.SingleFieldMigrationResult;
import org.folio.fqm.migration.warnings.ValueBreakingWarning;

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
public class V7PatronGroupsValueChange extends AbstractRegularMigrationStrategy<AtomicReference<List<PatronGroup>>> {

  private static final UUID LOANS_ENTITY_TYPE_ID = UUID.fromString("d6729885-f2fb-4dc7-b7d0-a865a7f461e4");
  private static final UUID USERS_ENTITY_TYPE_ID = UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf");
  private static final String FIELD_NAME = "groups.group";

  private final PatronGroupsClient patronGroupsClient;

  @Override
  public String getMaximumApplicableVersion() {
    return "7";
  }

  @Override
  public String getLabel() {
    return "V7 -> V8 patron group name value transformation (MODFQMMGR-602)";
  }

  @Override
  public AtomicReference<List<PatronGroup>> getStartingState() {
    return new AtomicReference<>();
  }

  @Override
  public SingleFieldMigrationResult<MigratableFqlFieldAndCondition> migrateFql(
    AtomicReference<List<PatronGroup>> state,
    MigratableFqlFieldAndCondition cond
  ) {
    return MigrationUtils.migrateFqlValues(
      cond,
      condition ->
        (
          LOANS_ENTITY_TYPE_ID.equals(condition.entityTypeId()) || USERS_ENTITY_TYPE_ID.equals(condition.entityTypeId())
        ) &&
        FIELD_NAME.equals(condition.field()),
      (MigratableFqlFieldAndCondition condition, String value, Supplier<String> fql) -> {
        if (state.get() == null) {
          state.set(patronGroupsClient.getGroups().usergroups());

          log.info("Fetched {} records from API", state.get().size());
        }

        return state
          .get()
          .stream()
          .filter(r -> r.id().equals(value))
          .findFirst()
          .map(PatronGroup::group)
          .map(MigrationResult::withResult)
          .orElseGet(() -> {
            // some of these may already be the correct value, as both the name and ID fields
            // got mapped to the same place. If the name is already being used, we want to make
            // sure not to discard it
            boolean existsAsName = state.get().stream().anyMatch(r -> r.group().equals(value));

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
    );
  }
}
