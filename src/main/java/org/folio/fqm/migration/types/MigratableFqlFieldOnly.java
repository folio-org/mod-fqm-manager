package org.folio.fqm.migration.types;

import java.util.UUID;
import lombok.With;

@With
public record MigratableFqlFieldOnly(UUID entityTypeId, String fieldPrefix, String field)
  implements MigratableFqlField<MigratableFqlFieldOnly> {
  public MigratableFqlFieldOnly dereferenced(UUID innerEntityTypeId, String sourceName, String remainder) {
    return new MigratableFqlFieldOnly(innerEntityTypeId, this.fieldPrefix + sourceName + ".", remainder);
  }
}
