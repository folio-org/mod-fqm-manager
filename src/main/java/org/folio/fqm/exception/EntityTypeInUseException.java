package org.folio.fqm.exception;

import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.domain.dto.Parameter;
import org.folio.querytool.domain.dto.EntityType;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class EntityTypeInUseException extends FqmException {
  private final UUID entityTypeId;

  public EntityTypeInUseException(EntityType entityType, String message) {
    super(message);
    this.entityTypeId = UUID.fromString(entityType.getId());
  }


  @Override
  public Error getError() {
    return new Error().message(getMessage()).addParametersItem(new Parameter().key("id").value(entityTypeId.toString()));
  }

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.CONFLICT;
  }
}
