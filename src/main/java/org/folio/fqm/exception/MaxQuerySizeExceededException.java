package org.folio.fqm.exception;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.domain.dto.Error;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@RequiredArgsConstructor
public class MaxQuerySizeExceededException extends FqmException {
  private final UUID queryId;
  private final int querySize;
  private final int maxSize;

  @Override
  public String getMessage() {
    return String.format("Query %s with size %d has exceeded the maximum size of %d.", queryId, querySize, maxSize);
  }

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.BAD_REQUEST;
  } // TODO: not sure about this 400 or 200?

  @Override
  public Error getError() {
    return new Error().message(getMessage());
  }
}
