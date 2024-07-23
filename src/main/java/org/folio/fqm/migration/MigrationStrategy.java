package org.folio.fqm.migration;

import java.util.function.BiFunction;
import org.folio.fql.service.FqlService;

public interface MigrationStrategy
  extends BiFunction<FqlService, MigratableQueryInformation, MigratableQueryInformation> {
  /** For logging purposes */
  String getLabel();
  boolean applies(FqlService fqlService, MigratableQueryInformation migratableQueryInformation);
}
