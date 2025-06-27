package org.folio.fqm.migration;

import jakarta.annotation.Nonnull;
import java.util.function.BiFunction;
import org.folio.fql.service.FqlService;

public interface MigrationStrategy
  extends BiFunction<FqlService, MigratableQueryInformation, MigratableQueryInformation> {
  /** For logging purposes */
  String getLabel();

  /** Get the version after which this migration doesn't apply.
   * If the current version &gt; this value, then the migration can be skipped
   */
  String getMaximumApplicableVersion();

  /**
   * Determine if a query should be migrated by this strategy (if this strategy "applies" to a query)
   */
  default boolean applies(@Nonnull MigratableQueryInformation migratableQueryInformation) {
    return true;
  }

  /**
   * Migrate the query. This method will be called iff {@link #applies(MigratableQueryInformation)} returns true.
   *
   * After this method is called, {@link #applies(MigratableQueryInformation)} MUST return false. Otherwise, an infinite
   * loop will occur.
   */
  @Override // respecified to add docblock
  MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation migratableQueryInformation);
}
