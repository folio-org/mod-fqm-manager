package org.folio.fqm.exception;

import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.domain.dto.Parameter;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class EntityTypeNotFoundException extends FqmException {
  private final UUID entityTypeId;
  private final String message;

  public EntityTypeNotFoundException(UUID entityTypeId, String message) {
    this.entityTypeId = entityTypeId;
    this.message = message;
  }

  public EntityTypeNotFoundException(UUID entityTypeId) {
    this(entityTypeId, "Entity type with ID " + entityTypeId + " not found");
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.NOT_FOUND;
  }

  @Override
  public Error getError() {
    return new Error().message(getMessage())
      .addParametersItem(new Parameter().key("entityTypeId").value(entityTypeId.toString()));
  }
}
