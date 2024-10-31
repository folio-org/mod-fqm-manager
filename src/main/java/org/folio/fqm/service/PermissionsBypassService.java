package org.folio.fqm.service;

import java.util.Set;
import java.util.UUID;

import lombok.extern.log4j.Log4j2;
import org.folio.querytool.domain.dto.EntityType;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Lazy
@Log4j2
@Service
public class PermissionsBypassService implements PermissionsService {

  @Override
  public Set<String> getUserPermissions() {
    return Set.of();
  }

  @Override
  public Set<String> getUserPermissions(String tenantId, UUID userId) {
    return Set.of();
  }

  @Override
  public Set<String> getRequiredPermissions(EntityType entityType) {
    return Set.of();
  }

  @Override
  public void verifyUserHasNecessaryPermissions(EntityType entityType, boolean checkFqmPermissions) {
    log.info(
      "Bypassing permissions check for entity type: {}, checkFqmPermissions={}",
      entityType.getName(),
      checkFqmPermissions
    );
  }

  @Override
  public void verifyUserHasNecessaryPermissions(String tenantId, EntityType entityType, UUID userId, boolean checkFqmPermissions) {
    log.info(
      "Bypassing permissions check for tenantId: {}, entity type: {}, userId: {}, checkFqmPermissions={}",
      tenantId,
      entityType.getName(),
      userId,
      checkFqmPermissions
    );
  }
}
