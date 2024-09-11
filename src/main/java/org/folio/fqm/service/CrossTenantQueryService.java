package org.folio.fqm.service;

import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.fqm.exception.MissingPermissionsException;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
public class CrossTenantQueryService {

  private final SimpleHttpClient ecsClient;
  private final FolioExecutionContext executionContext;
  private final PermissionsService permissionsService;

  private static final String COMPOSITE_INSTANCES_ID = "6b08439b-4f8e-4468-8046-ea620f5cfb74";

  public List<String> getTenantsToQuery(EntityType entityType, boolean forceCrossTenantQuery) {
    if (!forceCrossTenantQuery && !Boolean.TRUE.equals(entityType.getCrossTenantQueriesEnabled())) {
      return List.of(executionContext.getTenantId());
    }
    List<Map<String, String>> userTenantMaps = getUserTenants();
    String centralTenantId = getCentralTenantId(userTenantMaps); // null if non-ECS environment; non-null otherwise

    if (!executionContext.getTenantId().equals(centralTenantId)) {
      log.debug("Tenant {} is not central tenant. Running intra-tenant query.", executionContext.getTenantId());
      // The Instances entity type is required to retrieve shared instances from the central tenant when
      // running queries from member tenants. This means that if we are running a query for Instances, we need to
      // query the current tenant (for local records) as well as the central tenant (for shared records).
      if (COMPOSITE_INSTANCES_ID.equals(entityType.getId())) {
        return centralTenantId != null
          ? List.of(executionContext.getTenantId(), centralTenantId)
          : List.of(executionContext.getTenantId());
      }
      return List.of(executionContext.getTenantId());
    }

    List<String> tenantsToQuery = new ArrayList<>();
    tenantsToQuery.add(centralTenantId);
    for (var userMap : userTenantMaps) {
      String tenantId = userMap.get("tenantId");
      String userId = userMap.get("userId");
      if (!tenantId.equals(centralTenantId)) {
        try {
          permissionsService.verifyUserHasNecessaryPermissions(tenantId, entityType, true);
          tenantsToQuery.add(tenantId);
        } catch (MissingPermissionsException e) {
          log.info("User with id {} does not have permissions to query tenant {}. Skipping.", userId, tenantId);
        }
      }
    }

    return tenantsToQuery;
  }

  @SuppressWarnings("unchecked")
  // JsonPath.parse is returning a plain List without a type parameter, and the TypeRef (vs Class) parameter to JsonPath.read is not supported by the JSON parser
  private List<Map<String, String>> getUserTenants() {
    String userTenantsResponse = ecsClient.get("user-tenants", Map.of("limit", "1000"));
    List<Map<String, String>> userTenants = JsonPath
      .parse(userTenantsResponse)
      .read("$.userTenants", List.class);

    // Get the first entry for each tenant
    return userTenants.stream().collect(Collectors.groupingBy(m -> m.get("tenantId")))
      .values()
      .stream()
      .filter(l -> !l.isEmpty()) // Just to be safe...
      .map(m -> m.get(0))
      .toList();
  }

  private String getCentralTenantId(List<Map<String, String>> userTenants) {
    return userTenants.stream()
       .map(map -> map.get("centralTenantId"))
       .findFirst()
       .orElse(null);
  }

  public String getCentralTenantId() {
    return getCentralTenantId(getUserTenants());
  }

  public boolean ecsEnabled() {
    return !getUserTenants().isEmpty();
  }
}
