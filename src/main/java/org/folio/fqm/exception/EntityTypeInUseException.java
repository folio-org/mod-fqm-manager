package org.folio.fqm.exception;

import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.domain.dto.Parameter;
import org.folio.querytool.domain.dto.EntityType;
import org.springframework.http.HttpStatus;

public class EntityTypeInUseException extends FqmException {
  private final EntityType entityType;

  public EntityTypeInUseException(EntityType entityType, String message) {
    super(message);
    this.entityType = entityType;
  }


  @Override
  public Error getError() {
    return new Error().message(getMessage()).addParametersItem(new Parameter().key("id").value(entityType.getId()));
  }

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.CONFLICT;
  }
}
