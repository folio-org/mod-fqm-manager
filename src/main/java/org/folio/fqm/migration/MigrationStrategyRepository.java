package org.folio.fqm.migration;

import java.util.List;
import org.folio.fqm.client.ConfigurationClient;
import org.folio.fqm.migration.strategies.V0POCMigration;
import org.folio.fqm.migration.strategies.V1ModeOfIssuanceConsolidation;
import org.folio.fqm.migration.strategies.V2ResourceTypeConsolidation;
import org.folio.fqm.migration.strategies.V3RamsonsFieldCleanup;
import org.folio.fqm.migration.strategies.V4DateFieldTimezoneAddition;
import org.springframework.stereotype.Component;

@Component
public class MigrationStrategyRepository {

  private final List<MigrationStrategy> migrationStrategies;

  public MigrationStrategyRepository(ConfigurationClient configurationClient) {
    this.migrationStrategies =
      List.of(
        new V0POCMigration(),
        new V1ModeOfIssuanceConsolidation(),
        new V2ResourceTypeConsolidation(),
        new V3RamsonsFieldCleanup(),
        new V4DateFieldTimezoneAddition(configurationClient)
      );
  }

  public List<MigrationStrategy> getMigrationStrategies() {
    return this.migrationStrategies;
  }
}
