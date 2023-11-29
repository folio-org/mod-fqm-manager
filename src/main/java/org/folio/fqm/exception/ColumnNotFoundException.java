package org.folio.fqm.exception;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ColumnNotFoundException extends RuntimeException {
  private final String entityTypeName;
  private final String column;

  @Override
  public String getMessage() {
    return "The entity type " + entityTypeName + " does not contain a column with name " + column;
  }
}
