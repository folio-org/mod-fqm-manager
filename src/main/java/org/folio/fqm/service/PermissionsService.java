package org.folio.fqm.service;

import java.util.Set;
import java.util.UUID;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.EntityType;

public interface PermissionsService {
  public Set<String> getUserPermissions();

  public Set<String> getUserPermissions(String tenantId, UUID userId);

  public Set<String> getRequiredPermissions(EntityType entityType);

  public void verifyUserHasNecessaryPermissions(EntityType entityType, boolean checkFqmPermissions);

  public void verifyUserHasNecessaryPermissions(
    String tenantId,
    EntityType entityType,
    UUID userId,
    boolean checkFqmPermissions
  );

  public boolean canUserAccessCustomEntityType(CustomEntityType entityType);

  public void verifyUserCanAccessCustomEntityType(CustomEntityType entityType);
}
