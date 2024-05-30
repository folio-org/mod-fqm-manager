package org.folio.fqm.exception;

import lombok.RequiredArgsConstructor;
import org.folio.fql.model.field.FqlField;
import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.domain.dto.Parameter;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public class FieldNotFoundException extends FqmException {

  private final String entityTypeName;
  private final String field;

  public FieldNotFoundException(String entityTypeName, FqlField fieldName) {
    this.entityTypeName = entityTypeName;
    this.field = fieldName.serialize();
  }

  @Override
  public String getMessage() {
    return "The entity type %s does not contain a field with name %s".formatted(entityTypeName, field);
  }

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.NOT_FOUND;
  }

  @Override
  public Error getError() {
    return new Error().message(getLocalizedMessage())
      .addParametersItem(new Parameter().key("entityTypeName").value(entityTypeName))
      .addParametersItem(new Parameter().key("field").value(field));
  }
}
