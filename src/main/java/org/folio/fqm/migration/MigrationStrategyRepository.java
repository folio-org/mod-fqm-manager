package org.folio.fqm.migration;

import org.folio.fqm.migration.strategies.V0POCMigration;
import org.springframework.stereotype.Component;

@Component
public class MigrationStrategyRepository {

  public MigrationStrategy[] getMigrationStrategies() {
    return new MigrationStrategy[] { new V0POCMigration() };
  }
}
