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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
  private final Cache<UUID, Set<String>> cache = Caffeine.newBuilder().expireAfterWrite(cacheDurationSeconds, TimeUnit.SECONDS).build();

  public Set<String> getUserPermissions() {
    var userId = context.getUserId();
    return cache.get(userId, id -> isEureka ? getUserPermissionsFromRolesKeycloak(id) : getUserPermissionsFromModPermissions(id));
  }

  private Set<String> getUserPermissionsFromModPermissions(UUID userId) {
    return modPermissionsClient.getPermissionsForUser(userId.toString()).getPermissionNames();
  }

  private Set<String> getUserPermissionsFromRolesKeycloak(UUID userId) {
    throw new NotImplementedException("Not implemented yet");
  }

  public void verifyUserHasNecessaryPermissionsForEntityType(EntityType entityType) {
    List<String> requiredPermissions = entityType.getRequiredPermissions();
    Set<String> userPermissions = getUserPermissions();

    Set<String> missingPermissions = new HashSet<>();
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
}
