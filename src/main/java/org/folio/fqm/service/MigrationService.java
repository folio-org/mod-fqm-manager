package org.folio.fqm.service;

import java.util.List;
import org.apache.commons.lang3.NotImplementedException;

public class MigrationService {

  public String getLatestVersion() {
    // return "1";
    throw new NotImplementedException();
  }

  public boolean isMigrationNeeded(String fqlQuery) {
    // return true;
    throw new NotImplementedException();
  }

  public boolean isMigrationNeeded(MigratableQueryInformation migratableQueryInformation) {
    return isMigrationNeeded(migratableQueryInformation.fqlQuery());
  }

  public MigratableQueryInformation migrate(MigratableQueryInformation migratableQueryInformation) {
    // return migratableQueryInformation;
    throw new NotImplementedException();
  }

  public record MigratableQueryInformation(String entityTypeId, String fqlQuery, List<String> fields) {}
}
