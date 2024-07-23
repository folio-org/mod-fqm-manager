package org.folio.fqm.service;

import javax.annotation.CheckForNull;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.model.Fql;
import org.folio.fql.service.FqlService;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationStrategyRepository;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
@AllArgsConstructor(onConstructor_ = @Autowired)
public class MigrationService {

  private static final String CURRENT_VERSION = "1";

  private final FqlService fqlService;
  private final MigrationStrategyRepository migrationStrategyRepository;

  public String getLatestVersion() {
    return CURRENT_VERSION;
  }

  public boolean isMigrationNeeded(@CheckForNull String fqlQuery) {
    if (fqlQuery == null) {
      return true;
    }
    Fql fql = fqlService.getFql(fqlQuery);
    return !this.getLatestVersion().equals(fql._version());
  }

  public boolean isMigrationNeeded(MigratableQueryInformation migratableQueryInformation) {
    return isMigrationNeeded(migratableQueryInformation.fqlQuery());
  }

  public MigratableQueryInformation migrate(MigratableQueryInformation migratableQueryInformation) {
    while (isMigrationNeeded(migratableQueryInformation)) {
      for (MigrationStrategy strategy : migrationStrategyRepository.getMigrationStrategies()) {
        if (strategy.applies(migratableQueryInformation)) {
          log.info("Applying {} to {}", strategy.getLabel(), migratableQueryInformation);
          migratableQueryInformation = strategy.apply(migratableQueryInformation);
        }
      }
    }

    return migratableQueryInformation;
  }
}
