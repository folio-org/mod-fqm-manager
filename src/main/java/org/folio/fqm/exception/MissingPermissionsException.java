package org.folio.fqm.exception;

import org.folio.fqm.domain.dto.Error;
import org.springframework.http.HttpStatus;

import java.util.Set;

public class MissingPermissionsException extends FqmException {
  private final Set<String> missingPermissions;

  public MissingPermissionsException(Set<String> missingPermissions) {
    this.missingPermissions = missingPermissions;
  }

  @Override
  public String getMessage() {
    return "User is missing permissions that are required for this operation: [%s]"
      .formatted(String.join(", ", missingPermissions));
  }

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.UNAUTHORIZED;
  }

  @Override
  public Error getError() {
    return new Error().message(getMessage());
  }

  public Set<String> getMissingPermissions() {
    return missingPermissions;
  }
}
