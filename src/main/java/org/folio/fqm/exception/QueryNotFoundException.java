package org.folio.fqm.exception;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.domain.dto.Parameter;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public class QueryNotFoundException extends FqmException {

  private final UUID queryId;

  @Override
  public String getMessage() {
    return "Query with id " + queryId + " not found. ";
  }

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.NOT_FOUND;
  }

  @Override
  public Error getError() {
    return new Error()
      .message(getMessage())
      .code(Error.CodeEnum.QUERY_NOT_FOUND)
      .addParametersItem(new Parameter().key("queryId").value(queryId.toString()));
  }
}
