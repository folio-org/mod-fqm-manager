package org.folio.fqm.exception;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.domain.dto.Error;

import java.util.UUID;

@RequiredArgsConstructor
public class MaxQuerySizeExceededException extends RuntimeException {
  private final UUID queryId;
  private final int querySize;
  private final int maxSize;

  @Override
  public String getMessage() {
    return String.format("Query %s with size %d has exceeded the maximum size of %d.", queryId, querySize, maxSize);
  }

  public Error getError() {
    return new Error().message(getMessage());
  }
}
