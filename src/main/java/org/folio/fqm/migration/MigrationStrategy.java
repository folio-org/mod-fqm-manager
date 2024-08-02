package org.folio.fqm.migration;

import jakarta.annotation.Nonnull;
import java.util.function.BiFunction;
import org.folio.fql.service.FqlService;

public interface MigrationStrategy
  extends BiFunction<FqlService, MigratableQueryInformation, MigratableQueryInformation> {
  /** For logging purposes */
  String getLabel();

  /**
   * Determine if a query should be migrated by this strategy (if this strategy "applies" to a query)
   */
  boolean applies(@Nonnull String version);

  /**
   * Migrate the query. This method will be called iff {@link #applies(FqlService, MigratableQueryInformation)} returns true.
   *
   * After this method is called, {@link #applies(FqlService, MigratableQueryInformation)} MUST return false. Otherwise, an infinite
   * loop will occur.
   */
  @Override // respecified to add docblock
  MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation migratableQueryInformation);
}
