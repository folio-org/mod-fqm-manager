package org.folio.fqm.service;

import com.jayway.jsonpath.JsonPath;
import feign.FeignException;
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

@Service
@RequiredArgsConstructor
@Log4j2
public class CrossTenantQueryService {

  private final SimpleHttpClient ecsClient;
  private final FolioExecutionContext executionContext;
  private final PermissionsService permissionsService;
  private final UserTenantService userTenantService;

  private static final String COMPOSITE_INSTANCES_ID = "6b08439b-4f8e-4468-8046-ea620f5cfb74";
  private static final String SIMPLE_INSTANCES_ID = "8fc4a9d2-7ccf-4233-afb8-796911839862";
  private static final String SIMPLE_INSTANCE_STATUS_ID = "9c239bfd-198f-4013-bbc4-4551c0cbdeaa";
  private static final String SIMPLE_INSTANCE_TYPE_ID = "af44e2e0-12e0-4eec-b80d-49feb33a866c";
  private static final List<String> INSTANCE_RELATED_ENTITIES = List.of(SIMPLE_INSTANCES_ID, COMPOSITE_INSTANCES_ID, SIMPLE_INSTANCE_STATUS_ID, SIMPLE_INSTANCE_TYPE_ID);

  /**
   * Retrieve list of tenants to run query against.
   * @param entityType Entity type definition
   * @return           List of tenants to query
   */
  public List<String> getTenantsToQuery(EntityType entityType) {
    if (!Boolean.TRUE.equals(entityType.getCrossTenantQueriesEnabled())
      && !COMPOSITE_INSTANCES_ID.equals(entityType.getId())) {
      return List.of(executionContext.getTenantId());
    }
    return getTenants(entityType);
  }

  /**
   * Retrieve list of tenants to retrieve column values from. This method skips the cross-tenant query check, since the
   * column values API uses simple entity type definitions, which don't have cross-tenant queries enabled.
   * method skips the cross-tenant query check
   * @param entityType Entity type definition
   * @return           List of tenants to query
   */
  public List<String> getTenantsToQueryForColumnValues(EntityType entityType) {
    return getTenants(entityType);
  }

  private List<String> getTenants(EntityType entityType) {
    log.info("Getting tenants to query");
    // Get the ECS tenant info first, since this comes from mod-users and should work in non-ECS environments
    // We can use this for determining if it's an ECS environment, and if so, retrieving the consortium ID and central tenant ID
    Map<String, String> ecsTenantInfo = getEcsTenantInfo();
    if (!ecsEnabled(ecsTenantInfo)) {
      return List.of(executionContext.getTenantId());
    }

    String centralTenantId = getCentralTenantId(ecsTenantInfo);
    if (!executionContext.getTenantId().equals(centralTenantId)) {
      log.info("Tenant {} is not central tenant. Running intra-tenant query.", executionContext.getTenantId());
      // The Instances entity type is required to retrieve shared instances from the central tenant when
      // running queries from member tenants. This means that if we are running a query for Instances, we need to
      // query the current tenant (for local records) as well as the central tenant (for shared records).
      if (INSTANCE_RELATED_ENTITIES.contains(entityType.getId())) {
        return List.of(executionContext.getTenantId(), centralTenantId);
      }
      return List.of(executionContext.getTenantId());
    }

    List<String> tenantsToQuery = new ArrayList<>();
    tenantsToQuery.add(centralTenantId);
    List<Map<String, String>> userTenantMaps = getUserTenants(ecsTenantInfo.get("consortiumId"), executionContext.getUserId().toString());
    for (var userMap : userTenantMaps) {
      String tenantId = userMap.get("tenantId");
      log.info("Tenant id: {}", tenantId);
      String userId = userMap.get("userId");
      log.info("User id: {}", userId);
      if (!tenantId.equals(centralTenantId)) {
        try {
          permissionsService.verifyUserHasNecessaryPermissions(tenantId, entityType, true);
          tenantsToQuery.add(tenantId);
          log.info("User with id {} has permissions to query tenant {}!", userId, tenantId);
        } catch (MissingPermissionsException e) {
          log.info("User with id {} does not have permissions to query tenant {}. Skipping.", userId, tenantId);
        } catch (FeignException e) {
          log.error("Error retrieving permissions for user ID %s in tenant %s".formatted(userId, tenantId), e);
          throw e;
        }
      }
    }

    return tenantsToQuery;
  }

  @SuppressWarnings("unchecked")
  // JsonPath.parse is returning a plain List without a type parameter, and the TypeRef (vs Class) parameter to JsonPath.read is not supported by the JSON parser
  private List<Map<String, String>> getUserTenants(String consortiumId, String userId) {
    String userTenantResponse = ecsClient.get(
      "consortia/" + consortiumId + "/user-tenants",
      Map.of("userId", userId, "limit", "1000")
    );
    return JsonPath
      .parse(userTenantResponse)
      .read("$.userTenants", List.class);
  }

  public String getCentralTenantId() {
    return getCentralTenantId(getEcsTenantInfo());
  }

  public boolean ecsEnabled() {
    return ecsEnabled(getEcsTenantInfo());
  }

  public boolean isCentralTenant() {
    return isCentralTenant(getEcsTenantInfo());
  }

  private boolean ecsEnabled(Map<String, String> ecsTenantInfo) {
    return !(ecsTenantInfo == null || ecsTenantInfo.isEmpty());
  }

  /**
   * Retrieve the primary affiliation for a user.
   * This retrieves the primary affiliation for an arbitrary user in the tenant.
   * In ECS environments, this will return data for a user (in member tenants, it's a dummy user, but that works)
   * In non-ECS environments, this will return null
   */
  @SuppressWarnings("unchecked") // JsonPath.parse is returning a plain List without a type parameter, and the TypeRef (vs Class) parameter to JsonPath.read is not supported by the JSON parser
  private Map<String, String> getEcsTenantInfo() {
    String userTenantsResponse = userTenantService.getUserTenantsResponse(executionContext.getTenantId());
    List<Map<String, String>> userTenants = JsonPath
      .parse(userTenantsResponse)
      .read("$.userTenants", List.class);

    return userTenants.stream()
      .findAny()
      .orElse(null);
  }

  private String getCentralTenantId(Map<String, String> ecsTenantInfo) {
    return ecsTenantInfo != null ? ecsTenantInfo.get("centralTenantId") : null;
  }

  private boolean isCentralTenant(Map<String, String> ecsTenantInfo) {
    return executionContext.getTenantId().equals(getCentralTenantId(ecsTenantInfo));
  }
}
