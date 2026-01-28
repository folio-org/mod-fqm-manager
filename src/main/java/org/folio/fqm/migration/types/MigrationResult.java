package org.folio.fqm.migration.types;

import java.util.Collection;
import java.util.List;
import lombok.With;
import org.folio.fqm.migration.warnings.Warning;

@With
public record MigrationResult<T>(T result, Collection<Warning> warnings, boolean hadBreakingChange) {
  public static <V> MigrationResult<V> withResult(V result) {
    return new MigrationResult<>(result, List.of(), false);
  }
  public static <V> MigrationResult<V> noop(V original) {
    return new MigrationResult<>(original, List.of(), false);
  }
  public static <V> MigrationResult<V> removed() {
    return new MigrationResult<>(null, List.of(), false);
  }
  public <U> MigrationResult<U> withoutResult() {
    return new MigrationResult<>(null, this.warnings, this.hadBreakingChange);
  }
  public <U> MigrationResult<U> withNewResult(U newResult) {
    return new MigrationResult<>(newResult, this.warnings, this.hadBreakingChange);
  }
}
