package org.folio.fqm.exception;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.domain.dto.Parameter;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@RequiredArgsConstructor
public class EntityTypeNotFoundException extends FqmException {
  private final UUID entityTypeId;

  @Override
  public String getMessage() {
    return "Entity type with ID " + entityTypeId + " not found";
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
