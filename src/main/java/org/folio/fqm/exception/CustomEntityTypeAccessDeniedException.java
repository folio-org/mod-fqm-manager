package org.folio.fqm.exception;

import org.folio.fqm.domain.dto.Error;
import org.springframework.http.HttpStatus;

public class CustomEntityTypeAccessDeniedException extends FqmException {

  public CustomEntityTypeAccessDeniedException(String message) {
    super(message);
  }

  @Override
  public Error getError() {
    return new Error(getMessage()).code("CUSTOM_ENTITY_TYPE_ACCESS_DENIED");
  }

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.FORBIDDEN;
  }
}
