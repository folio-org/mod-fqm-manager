package org.folio.fqm.migration;

import java.util.Collections;
import java.util.List;
import org.folio.fqm.migration.strategies.V0POCMigration;
import org.folio.fqm.migration.strategies.V1ModeOfIssuanceConsolidation;
import org.springframework.stereotype.Component;

@Component
public class MigrationStrategyRepository {

  // prevent re-initialization on each call
  private static final List<MigrationStrategy> MIGRATION_STRATEGIES = Collections.unmodifiableList(
    List.of(new V0POCMigration(), new V1ModeOfIssuanceConsolidation())
  );

  public List<MigrationStrategy> getMigrationStrategies() {
    return MIGRATION_STRATEGIES;
  }
}
