package org.folio.fqm.exception;

import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.domain.dto.Parameter;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;

/**
 * Wrapper class for miscellaneous Java exceptions that are not from FQM, but
 * we still want to report them in the standard FQM error format.
 */
public class UnknownException extends FqmException {

  public UnknownException(Throwable cause) {
    super(cause.getLocalizedMessage(), cause);
  }

  @Override
  public Error getError() {
    return new Error()
      .message(getLocalizedMessage())
      .code(Error.CodeEnum.UNKNOWN_ERROR)
      .addParametersItem(new Parameter().key("type").value(this.getCause().getClass().getName()));
  }

  @Override
  public HttpStatus getHttpStatus() {
    return switch (this.getCause()) {
      case EmptyResultDataAccessException e -> HttpStatus.NOT_FOUND;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }
}
