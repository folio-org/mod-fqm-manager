package org.folio.fqm.exception;

import org.folio.fqm.domain.dto.Error;
import org.springframework.http.HttpStatus;

public abstract class FqmException extends RuntimeException {
  public HttpStatus getHttpStatus() {
    return HttpStatus.BAD_REQUEST;
  }

  public abstract Error getError();
}
