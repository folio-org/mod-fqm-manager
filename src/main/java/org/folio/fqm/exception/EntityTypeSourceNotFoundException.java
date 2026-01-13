package org.folio.fqm.exception;

import java.util.UUID;
import lombok.Builder;
import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.domain.dto.Parameter;
import org.springframework.http.HttpStatus;

@Builder
public class EntityTypeSourceNotFoundException extends FqmException {

  private final UUID parentEntityTypeID;
  private final String parentEntityTypeName;
  private final UUID sourceEntityTypeId;
  private final String sourceEntityTypeAlias;

  public EntityTypeSourceNotFoundException(
    UUID parentEntityTypeID,
    String parentEntityTypeName,
    UUID sourceEntityTypeId,
    String sourceEntityTypeAlias
  ) {
    this.parentEntityTypeID = parentEntityTypeID;
    this.parentEntityTypeName = parentEntityTypeName;
    this.sourceEntityTypeId = sourceEntityTypeId;
    this.sourceEntityTypeAlias = sourceEntityTypeAlias;
  }

  @Override
  public String getMessage() {
    return (
      (
        "Entity type “%s” (%s) references source “%s” (%s) which could not be found. " +
        "This entity type will not be usable until the source is restored or removed from the definition."
      ).formatted(parentEntityTypeName, parentEntityTypeID, sourceEntityTypeAlias, sourceEntityTypeId)
    );
  }

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.NOT_FOUND;
  }

  @Override
  public Error getError() {
    return new Error()
      .message(getMessage())
      .code(Error.CodeEnum.ENTITY_TYPE_SOURCE_MISSING)
      .addParametersItem(new Parameter().key("parentEntityTypeId").value(parentEntityTypeID.toString()))
      .addParametersItem(new Parameter().key("parentEntityTypeName").value(parentEntityTypeName))
      .addParametersItem(new Parameter().key("sourceEntityTypeId").value(sourceEntityTypeId.toString()))
      .addParametersItem(new Parameter().key("sourceEntityTypeAlias").value(sourceEntityTypeAlias));
  }
}
