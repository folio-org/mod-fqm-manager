package org.folio.fqm.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.ModPermissionsClient;
import org.folio.fqm.client.ModRolesKeycloakClient;
import org.folio.fqm.exception.CustomEntityTypeAccessDeniedException;
import org.folio.fqm.exception.MissingPermissionsException;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Lazy
@Log4j2
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class PermissionsRegularService implements PermissionsService {

  // package-private for visibility in unit tests
  @Value("${folio.is-eureka}")
  boolean isEureka;

  @Value("${mod-fqm-manager.permissions-cache-timeout-seconds:60}")
  private long cacheDurationSeconds;

  private final FolioExecutionContext context;
  private final ModPermissionsClient modPermissionsClient;
  private final ModRolesKeycloakClient modRolesKeycloakClient;
  private final Cache<TenantUserPair, Set<String>> cache = Caffeine.newBuilder()
    .expireAfterWrite(cacheDurationSeconds, TimeUnit.SECONDS)
    .build();
  private final EntityTypeFlatteningService entityTypeFlatteningService;

  private static final List<String> CROSS_TENANT_FQM_PERMISSIONS = List.of(
    "fqm.entityTypes.item.get",
    "fqm.query.async.results.get",
    "fqm.query.async.post"
  );

  @Override
  public Set<String> getUserPermissions() {
    return getUserPermissions(context.getTenantId(), context.getUserId());
  }

  public Set<String> getUserPermissions(String tenantId, UUID userId) {
    TenantUserPair key = new TenantUserPair(tenantId, userId);
    return cache.get(key, k -> isEureka ? getUserPermissionsFromRolesKeycloak(k.tenant(), k.userId()) : getUserPermissionsFromModPermissions(k.tenant(), k.userId()));
  }

  public Set<String> getRequiredPermissions(EntityType entityType) {
    EntityType flattenedEntityType = entityTypeFlatteningService.getFlattenedEntityType(UUID.fromString(entityType.getId()), context.getTenantId(), false);
    return new HashSet<>(flattenedEntityType.getRequiredPermissions());
  }

  @Override
  public void verifyUserHasNecessaryPermissions(EntityType entityType, boolean checkFqmPermissions) {
    verifyUserHasNecessaryPermissions(context.getTenantId(), entityType, context.getUserId(), checkFqmPermissions);
  }

  public void verifyUserHasNecessaryPermissions(String tenantId, EntityType entityType, UUID userId, boolean checkFqmPermissions) {
    Set<String> requiredPermissions = getRequiredPermissions(entityType);
    Set<String> userPermissions = getUserPermissions(tenantId, userId);

    Set<String> missingPermissions = new HashSet<>();
    if (checkFqmPermissions) {
      for (String requiredPermission : CROSS_TENANT_FQM_PERMISSIONS) {
        if (!userPermissions.contains(requiredPermission)) {
          missingPermissions.add(requiredPermission);
        }
      }
    }

    for (String requiredPermission : requiredPermissions) {
      if (!userPermissions.contains(requiredPermission)) {
        missingPermissions.add(requiredPermission);
      }
    }

    if (!missingPermissions.isEmpty()) {
      log.warn("User {} is missing permissions that are required for this operation: [{}]", userId, missingPermissions);
      throw new MissingPermissionsException(missingPermissions);
    }
  }

  @Override
  public void verifyUserCanAccessCustomEntityType(CustomEntityType entityType) {
    if (!canUserAccessCustomEntityType(entityType)) {
      throw new CustomEntityTypeAccessDeniedException("Entity type " + entityType.getId() + " is not shared. It can only be accessed by its owner");
    }
  }

  @Override
  public boolean canUserAccessCustomEntityType(CustomEntityType entityType) {
    return entityType.getShared() || context.getUserId().equals(entityType.getOwner());
  }

  private Set<String> getUserPermissionsFromModPermissions(String tenantId, UUID userId) {
    return modPermissionsClient
      .getPermissionsForUser(tenantId, userId.toString())
      .getPermissionNames();
  }

  private Set<String> getUserPermissionsFromRolesKeycloak(String tenantId, UUID userId) {
    return modRolesKeycloakClient
      .getPermissionsUser(tenantId, userId)
      .getPermissionNames();
  }

  private record TenantUserPair(String tenant, UUID userId) {
  }
}
