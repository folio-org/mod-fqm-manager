package org.folio.fqm.migration.strategies;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import org.folio.fqm.migration.AbstractSimpleMigrationStrategy;
import org.folio.fqm.migration.warnings.FieldWarning;
import org.folio.fqm.migration.warnings.RemovedFieldWarning;

/**
 * Version 3 -> 4, handles some cleanup of entity types completed since the original V0POCMigration
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-588
 */
public class V3RamsonsFieldCleanup extends AbstractSimpleMigrationStrategy {

  public static final UUID COMPOSITE_LOAN_DETAILS = UUID.fromString("d6729885-f2fb-4dc7-b7d0-a865a7f461e4");

  public static final UUID COMPOSITE_INSTANCES = UUID.fromString("6b08439b-4f8e-4468-8046-ea620f5cfb74");
  public static final Map<String, String> INSTANCES_FIELD_CHANGES = Map.ofEntries(
    Map.entry("instance.status_id", "inst_stat.id")
  );

  public static final UUID COMPOSITE_USER_DETAILS = UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf");

  @Override
  public String getLabel() {
    return "V3 -> V4 Ramsons field cleanup (MODFQMMGR-588)";
  }

  @Override
  public String getMaximumApplicableVersion() {
    return "3";
  }

  @Override
  public Map<UUID, Map<String, String>> getFieldChanges() {
    return Map.ofEntries(Map.entry(COMPOSITE_INSTANCES, INSTANCES_FIELD_CHANGES));
  }

  @Override
  public Map<UUID, Map<String, BiFunction<String, String, FieldWarning>>> getFieldWarnings() {
    return Map.ofEntries(
      Map.entry(
        COMPOSITE_LOAN_DETAILS,
        Map.ofEntries(
          Map.entry("users.first_name", RemovedFieldWarning.withAlternative("users.last_name_first_name")),
          Map.entry("users.last_name", RemovedFieldWarning.withAlternative("users.last_name_first_name"))
        )
      ),
      Map.entry(
        COMPOSITE_INSTANCES,
        Map.ofEntries(Map.entry("instance.mode_of_issuance_id", RemovedFieldWarning.withoutAlternative()))
      ),
      Map.entry(
        COMPOSITE_USER_DETAILS,
        Map.ofEntries(Map.entry("users.addresses[*]->address_id", RemovedFieldWarning.withoutAlternative()))
      )
    );
  }
}
