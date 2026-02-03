package org.folio.fqm.migration.types;

import java.util.UUID;

public interface MigratableFqlField<F extends MigratableFqlField<F>> {
  public UUID entityTypeId();

  public String fieldPrefix();

  public String field();

  public default String getFullField() {
    return fieldPrefix() + field();
  }

  public F dereferenced(UUID innerEntityTypeId, String sourceName, String remainder);
}
