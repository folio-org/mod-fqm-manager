package org.folio.fqm.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.NotImplementedException;
import org.folio.fqm.client.ModPermissionsClient;
import org.folio.fqm.exception.MissingPermissionsException;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Log4j2
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class PermissionsService {

  // package-private for visibility in unit tests
  @Value("${folio.is-eureka:false}")
  boolean isEureka;

  @Value("${mod-fqm-manager.permissions-cache-timeout-seconds:60}")
  private long cacheDurationSeconds;

  private final FolioExecutionContext context;
  private final ModPermissionsClient modPermissionsClient;
  private final Cache<TenantUserPair, Set<String>> cache = Caffeine.newBuilder().expireAfterWrite(cacheDurationSeconds, TimeUnit.SECONDS).build();
  private final EntityTypeFlatteningService entityTypeFlatteningService;
  private static final List<String> CROSS_TENANT_FQM_PERMISSIONS = List.of(
    "fqm.entityTypes.item.get",
    "fqm.query.async.results.get",
    "fqm.query.async.post"
  );

  public Set<String> getUserPermissions() {
    return getUserPermissions(context.getTenantId());
  }

  public Set<String> getUserPermissions(String tenantId) {
    TenantUserPair key = new TenantUserPair(tenantId, context.getUserId());
    return cache.get(key, k -> isEureka ? getUserPermissionsFromRolesKeycloak(k.userId()) : getUserPermissionsFromModPermissions(tenantId, k.userId()));
  }

  public String myMethod(String argument) {
    return argument != null ? argument : "NOT FOUND";
  }

  private Set<String> getUserPermissionsFromModPermissions(String tenantId, UUID userId) {
    return modPermissionsClient
      .getPermissionsForUser(tenantId, userId.toString())
      .getPermissionNames();
  }

  private Set<String> getUserPermissionsFromRolesKeycloak(UUID userId) {
    throw new NotImplementedException("Not implemented yet");
  }

  public Set<String> getRequiredPermissions(EntityType entityType) {
    EntityType flattenedEntityType = entityTypeFlatteningService.getFlattenedEntityType(UUID.fromString(entityType.getId()), null);
    return new HashSet<>(flattenedEntityType.getRequiredPermissions());
  }

  public void verifyUserHasNecessaryPermissions(EntityType entityType, boolean checkFqmPermissions) {
    verifyUserHasNecessaryPermissions(context.getTenantId(), entityType, checkFqmPermissions);
  }

  public void verifyUserHasNecessaryPermissions(String tenantId, EntityType entityType, boolean checkFqmPermissions) {
    Set<String> requiredPermissions = getRequiredPermissions(entityType);
    Set<String> userPermissions = getUserPermissions(tenantId);

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
      log.warn("User {} is missing permissions that are required for this operation: [{}]", context.getUserId(), missingPermissions);
      throw new MissingPermissionsException(missingPermissions);
    }
  }

  private record TenantUserPair(String tenant, UUID userId) {
  }
}
