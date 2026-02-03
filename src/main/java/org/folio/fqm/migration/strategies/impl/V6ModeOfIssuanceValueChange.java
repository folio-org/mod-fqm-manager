package org.folio.fqm.migration.strategies.impl;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.ModesOfIssuanceClient;
import org.folio.fqm.client.ModesOfIssuanceClient.ModeOfIssuance;
import org.folio.fqm.migration.MigrationUtils;
import org.folio.fqm.migration.strategies.AbstractRegularMigrationStrategy;
import org.folio.fqm.migration.types.MigratableFqlFieldAndCondition;
import org.folio.fqm.migration.types.MigrationResult;
import org.folio.fqm.migration.types.SingleFieldMigrationResult;
import org.folio.fqm.migration.warnings.ValueBreakingWarning;

/**
 * Version 6 -> 7, handles a change in the `mode_of_issuance_name` field in the `instance` entity type.
 *
 * Originally, values for this field were stored as the mode of issuance's ID, however, this was changed
 * in MODFQMMGR-427 to use the name itself. As such, we need to update queries to map the original IDs to
 * their corresponding names.
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-427 for the field changes
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-602 for adding this migration
 */
@Log4j2
@RequiredArgsConstructor
public class V6ModeOfIssuanceValueChange
  extends AbstractRegularMigrationStrategy<AtomicReference<List<ModeOfIssuance>>> {

  private static final UUID INSTANCES_ENTITY_TYPE_ID = UUID.fromString("6b08439b-4f8e-4468-8046-ea620f5cfb74");
  private static final String FIELD_NAME = "instance.mode_of_issuance_name";

  private final ModesOfIssuanceClient modesOfIssuanceClient;

  @Override
  public String getMaximumApplicableVersion() {
    return "6";
  }

  @Override
  public String getLabel() {
    return "V6 -> V7 mode of issuance value transformation (MODFQMMGR-602)";
  }

  @Override
  public AtomicReference<List<ModeOfIssuance>> getStartingState() {
    return new AtomicReference<>();
  }

  @Override
  public SingleFieldMigrationResult<MigratableFqlFieldAndCondition> migrateFql(
    AtomicReference<List<ModeOfIssuance>> state,
    MigratableFqlFieldAndCondition cond
  ) {
    return MigrationUtils.migrateFqlValues(
      cond,
      condition -> INSTANCES_ENTITY_TYPE_ID.equals(condition.entityTypeId()) && FIELD_NAME.equals(condition.field()),
      (MigratableFqlFieldAndCondition condition, String value, Supplier<String> fql) -> {
        if (state.get() == null) {
          state.set(modesOfIssuanceClient.getModesOfIssuance().issuanceModes());

          log.info("Fetched {} records from API", state.get().size());
        }

        return state
          .get()
          .stream()
          .filter(r -> r.id().equals(value))
          .findFirst()
          .map(ModeOfIssuance::name)
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
    );
  }
}
