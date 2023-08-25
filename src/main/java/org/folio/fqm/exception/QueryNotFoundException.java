package org.folio.fqm.exception;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class QueryNotFoundException extends RuntimeException {
  private final UUID queryId;

  @Override
  public String getMessage() {
    return "Query with id " + queryId + " not found. ";
  }
}
