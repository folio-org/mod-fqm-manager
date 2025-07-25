package org.folio.fqm.exception;

import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.domain.dto.Parameter;
import org.folio.querytool.domain.dto.EntityType;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InvalidEntityTypeDefinitionException extends FqmException {
  private final String entityTypeId;

  public InvalidEntityTypeDefinitionException(String message, UUID entityTypeId) {
    super(message);
    this.entityTypeId = entityTypeId != null ? entityTypeId.toString() : null;
  }

  public InvalidEntityTypeDefinitionException(String message, EntityType entityType) {
    super(message);
    this.entityTypeId = entityType.getId();
  }

  public InvalidEntityTypeDefinitionException(String message, EntityType entityType, Throwable cause) {
    super(message, cause);
    this.entityTypeId = entityType.getId();
  }

  @Override
  public Error getError() {
    return new Error().message(getMessage()).addParametersItem(new Parameter().key("id").value(entityTypeId));
  }

}
