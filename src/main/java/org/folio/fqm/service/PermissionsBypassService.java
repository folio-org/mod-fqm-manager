package org.folio.fqm.service;

import java.util.Set;
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
  public Set<String> getUserPermissions(String tenantId) {
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
  public void verifyUserHasNecessaryPermissions(String tenantId, EntityType entityType, boolean checkFqmPermissions) {
    log.info(
      "Bypassing permissions check for tenantId: {}, entity type: {}, checkFqmPermissions={}",
      tenantId,
      entityType.getName(),
      checkFqmPermissions
    );
  }
}
