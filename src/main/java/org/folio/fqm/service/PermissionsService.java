package org.folio.fqm.service;

import java.util.Set;
import org.folio.querytool.domain.dto.EntityType;

public interface PermissionsService {
  public Set<String> getUserPermissions();

  public Set<String> getUserPermissions(String tenantId);

  public Set<String> getRequiredPermissions(EntityType entityType);

  public void verifyUserHasNecessaryPermissions(EntityType entityType, boolean checkFqmPermissions);

  public void verifyUserHasNecessaryPermissions(String tenantId, EntityType entityType, boolean checkFqmPermissions);
}
