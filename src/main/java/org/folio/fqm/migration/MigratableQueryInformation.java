package org.folio.fqm.migration;

import java.util.List;
import java.util.UUID;
import javax.annotation.CheckForNull;
import lombok.Builder;
import lombok.Singular;
import lombok.With;
import org.folio.fqm.migration.warnings.Warning;

@With
@Builder
public record MigratableQueryInformation(
  UUID entityTypeId,
  @CheckForNull String fqlQuery,
  List<String> fields,
  @Singular List<Warning> warnings
) {
  public MigratableQueryInformation(UUID entityTypeId, String fqlQuery, List<String> fields) {
    this(entityTypeId, fqlQuery, fields, List.of());
  }
}
