package org.folio.fqm.migration.strategies;

import org.folio.fqm.migration.MigratableQueryInformation;

/**
 * Fully custom migration strategy, allowing full custom control of the entire migration process.
 * Note that you probably <strong>do not</strong> want to implement this directly; most migrations
 * should be implemented via {@link AbstractSimpleMigrationStrategy} or, for more complex use cases,
 * {@link AbstractRegularMigrationStrategy}.
 */
public interface MigrationStrategy {
  /** For logging purposes */
  String getLabel();

  /**
   * Get the version after which this migration doesn't apply.
   * If the current version is greater than this value, the migration can be skipped
   */
  String getMaximumApplicableVersion();

  /**
   * Migrate a query.
   */
  MigratableQueryInformation apply(MigratableQueryInformation migratableQueryInformation);
}
