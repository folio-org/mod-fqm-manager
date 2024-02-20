package org.folio.fqm.exception;

import lombok.RequiredArgsConstructor;
import org.folio.fql.model.field.FqlField;

@RequiredArgsConstructor
public class FieldNotFoundException extends RuntimeException {

  private final String entityTypeName;
  private final String field;

  public FieldNotFoundException(String entityTypeName, FqlField fieldName) {
    this.entityTypeName = entityTypeName;
    this.field = fieldName.serialize();
  }

  @Override
  public String getMessage() {
    return "The entity type %s does not contain a field with name %s".formatted(entityTypeName, field);
  }
}
