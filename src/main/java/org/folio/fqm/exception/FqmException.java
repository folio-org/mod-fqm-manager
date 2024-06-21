package org.folio.fqm.exception;

import org.folio.fqm.domain.dto.Error;
import org.springframework.http.HttpStatus;

public abstract class FqmException extends RuntimeException {
  public FqmException() {
    super();
  }

  public FqmException(String message) {
    super(message);
  }

  public FqmException(Throwable cause) {
    super(cause);
  }

  public FqmException(String message, Throwable cause) {
    super(message, cause);
  }

  public HttpStatus getHttpStatus() {
    return HttpStatus.BAD_REQUEST;
  }

  public abstract Error getError();
}
