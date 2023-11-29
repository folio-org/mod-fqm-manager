package org.folio.fqm.exception;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class EntityTypeNotFoundException extends RuntimeException {
  private final UUID entityTypeId;

  @Override
  public String getMessage() {
    return "Entity type with ID " + entityTypeId + " not found";
  }
}
