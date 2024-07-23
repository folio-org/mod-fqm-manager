package org.folio.fqm.migration;

import org.springframework.stereotype.Component;

@Component
public class MigrationStrategyRepository {

  public MigrationStrategy[] getMigrationStrategies() {
    return new MigrationStrategy[] {};
  }
}
