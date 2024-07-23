package org.folio.fqm.migration;

import java.util.function.BiFunction;
import org.folio.fql.service.FqlService;

public interface MigrationStrategy
  extends BiFunction<FqlService, MigratableQueryInformation, MigratableQueryInformation> {
  /** For logging purposes */
  String getLabel();

  boolean applies(FqlService fqlService, MigratableQueryInformation migratableQueryInformation);

  // respecified to add docblock
  /**
   * Migrate the query. This method will be called iff {@link #applies(FqlService, MigratableQueryInformation)} returns true.
   *
   * After this method is called, {@link #applies(FqlService, MigratableQueryInformation)} MUST return false. Otherwise, an infinite
   * loop will occur.
   */
  MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation migratableQueryInformation);
}
