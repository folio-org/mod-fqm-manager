package org.folio.fqm.migration.types;

import java.util.Collection;
import java.util.List;
import lombok.With;
import org.folio.fqm.migration.warnings.Warning;

@With
public record SingleFieldMigrationResult<F extends MigratableFqlField<F>>(
  Collection<F> result,
  Collection<Warning> warnings,
  boolean hadBreakingChange
) {
  public static <T extends MigratableFqlField<T>> SingleFieldMigrationResult<T> withField(T field) {
    return new SingleFieldMigrationResult<>(List.of(field), List.of(), false);
  }
  public static <T extends MigratableFqlField<T>> SingleFieldMigrationResult<T> removed() {
    return new SingleFieldMigrationResult<T>(List.of(), List.of(), false);
  }
  public static <T extends MigratableFqlField<T>> SingleFieldMigrationResult<T> noop(T original) {
    return new SingleFieldMigrationResult<>(List.of(original), List.of(), false);
  }
}
