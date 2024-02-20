package org.folio.fqm.exception;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FieldNotFoundException extends RuntimeException {

  private final String entityTypeName;
  private final String field;

  @Override
  public String getMessage() {
    return "The entity type %s does not contain a field with name %s".formatted(entityTypeName, field);
  }
}
