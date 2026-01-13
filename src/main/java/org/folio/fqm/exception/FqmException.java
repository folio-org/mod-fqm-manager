package org.folio.fqm.exception;

import org.folio.fqm.domain.dto.Error;
import org.springframework.http.HttpStatus;

public abstract class FqmException extends RuntimeException {
  protected FqmException() {
    super();
  }

  protected FqmException(String message) {
    super(message);
  }

  protected FqmException(Throwable cause) {
    super(cause);
  }

  protected FqmException(String message, Throwable cause) {
    super(message, cause);
  }

  public HttpStatus getHttpStatus() {
    return HttpStatus.BAD_REQUEST;
  }

  public abstract Error getError();
}
