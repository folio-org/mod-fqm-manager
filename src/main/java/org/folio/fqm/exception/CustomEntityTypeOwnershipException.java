package org.folio.fqm.exception;

import org.folio.fqm.domain.dto.Error;
import org.springframework.http.HttpStatus;

public class CustomEntityTypeOwnershipException extends FqmException {

  public CustomEntityTypeOwnershipException(String message) {
    super(message);
  }

  @Override
  public Error getError() {
    return new Error(getMessage());
  }

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.FORBIDDEN;
  }
}
