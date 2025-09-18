package org.folio.fqm.exception;

import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.domain.dto.Parameter;
import org.folio.querytool.domain.dto.EntityType;
import org.springframework.http.HttpStatus;

public class EntityTypeInUseException extends FqmException {
  private final String entityTypeId;

  public EntityTypeInUseException(EntityType entityType, String message) {
    super(message);
    this.entityTypeId = entityType.getId();
  }


  @Override
  public Error getError() {
    return new Error().message(getMessage()).addParametersItem(new Parameter().key("entityTypeId").value(entityTypeId));
  }

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.CONFLICT;
  }
}
