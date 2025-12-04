package org.folio.fqm.service;

import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Lazy
@Log4j2
@Service
@RequiredArgsConstructor
public class PermissionsBypassService implements PermissionsService {

  private final EntityTypeFlatteningService entityTypeFlatteningService;
  private final FolioExecutionContext context;

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
    // used to ensure we still throw ET flattening errors even when bypassing permissions,
    // to ensure all ET errors normally thrown during permission checks will still be thrown in dev
    entityTypeFlatteningService.getFlattenedEntityType(
      UUID.fromString(entityType.getId()),
      context.getTenantId(),
      false
    );

    return Set.of();
  }

  @Override
  public void verifyUserHasNecessaryPermissions(EntityType entityType, boolean checkFqmPermissions) {
    this.verifyUserHasNecessaryPermissions(context.getTenantId(), entityType, context.getUserId(), checkFqmPermissions);
  }

  @Override
  public void verifyUserHasNecessaryPermissions(
    String tenantId,
    EntityType entityType,
    UUID userId,
    boolean checkFqmPermissions
  ) {
    // used to ensure we still throw ET flattening errors even when bypassing permissions,
    // to ensure all ET errors normally thrown during permission checks will still be thrown in dev
    entityTypeFlatteningService.getFlattenedEntityType(
      UUID.fromString(entityType.getId()),
      context.getTenantId(),
      false
    );

    log.info(
      "Bypassing permissions check for tenantId: {}, entity type: {}, userId: {}, checkFqmPermissions={}",
      tenantId,
      entityType.getName(),
      userId,
      checkFqmPermissions
    );
  }

  @Override
  public void verifyUserCanAccessCustomEntityType(CustomEntityType entityType) {
    log.info("Bypassing permissions check for custom entity type: {}", entityType.getName());
  }

  @Override
  public boolean canUserAccessCustomEntityType(CustomEntityType entityType) {
    log.info("Bypassing permissions check for custom entity type: {}", entityType.getName());
    return true;
  }
}
