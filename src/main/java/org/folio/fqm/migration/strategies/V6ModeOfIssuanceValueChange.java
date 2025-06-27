package org.folio.fqm.migration.strategies;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.service.FqlService;
import org.folio.fqm.client.ModesOfIssuanceClient;
import org.folio.fqm.client.ModesOfIssuanceClient.ModeOfIssuance;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationUtils;
import org.folio.fqm.migration.warnings.ValueBreakingWarning;
import org.folio.fqm.migration.warnings.Warning;

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
public class V6ModeOfIssuanceValueChange implements MigrationStrategy {

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
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
    List<Warning> warnings = new ArrayList<>(query.warnings());

    AtomicReference<List<ModeOfIssuance>> modesOfIssuance = new AtomicReference<>();

    return query
      .withFqlQuery(
        MigrationUtils.migrateFqlValues(
          query.fqlQuery(),
          key -> INSTANCES_ENTITY_TYPE_ID.equals(query.entityTypeId()) && FIELD_NAME.equals(key),
          (String key, String value, Supplier<String> fql) -> {
            if (modesOfIssuance.get() == null) {
              modesOfIssuance.set(modesOfIssuanceClient.getModesOfIssuance().issuanceModes());

              log.info("Fetched {} modes of issuance from API", modesOfIssuance.get().size());
            }

            return modesOfIssuance
              .get()
              .stream()
              .filter(mode -> mode.id().equals(value))
              .findFirst()
              .map(ModeOfIssuance::name)
              .orElseGet(() -> {
                // some of these may already be the correct value, as both `mode_of_issuance_id` and
                // `mode_of_issuance` got mapped to `mode_of_issuance_name`. If the name is being used,
                // we want to make sure not to discard it
                boolean existsAsName = modesOfIssuance.get().stream().anyMatch(mode -> mode.name().equals(value));

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
      .withWarnings(warnings)
      .withHadBreakingChanges(query.hadBreakingChanges() || !warnings.isEmpty());
  }
}
