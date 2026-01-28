package org.folio.fqm.migration.types;

import java.util.function.Supplier;

@FunctionalInterface
public interface ValueTransformer {
  MigrationResult<String> apply(MigratableFqlFieldAndCondition key, String value, Supplier<String> fql);
}
