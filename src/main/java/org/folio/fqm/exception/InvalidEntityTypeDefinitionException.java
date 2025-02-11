package org.folio.fqm.exception;

import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.domain.dto.Parameter;
import org.folio.querytool.domain.dto.EntityType;
import org.springframework.http.HttpStatus;

public class InvalidEntityTypeDefinitionException extends FqmException {
  private final EntityType entityType;

  public InvalidEntityTypeDefinitionException(String message, Throwable cause, EntityType entityType) {
    super(message, cause);
    this.entityType = entityType;
  }

  public InvalidEntityTypeDefinitionException(String message, EntityType entityType) {
    super(message);
    this.entityType = entityType;
  }

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.INTERNAL_SERVER_ERROR;
  }

  @Override
  public Error getError() {
    return new Error().message(getMessage()).addParametersItem(new Parameter().key("id").value(entityType.getId()));
  }

}
