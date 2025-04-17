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
import java.util.UUID;

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
    return getTenantsToQuery(entityType, executionContext.getUserId());
  }

  /**
   * Retrieve list of tenants to run query against for a specified user.
   *
   * @param entityType Entity type definition
   * @param userId ID of user to retrieve tenant affiliations for
   * @return List of tenants to query
   */
  public List<String> getTenantsToQuery(EntityType entityType, UUID userId) {
    if (!Boolean.TRUE.equals(entityType.getCrossTenantQueriesEnabled())
      && !COMPOSITE_INSTANCES_ID.equals(entityType.getId())) {
      return List.of(executionContext.getTenantId());
    }
    return getTenants(entityType, userId);
  }

  /**
   * Retrieve list of tenants to retrieve column values from. This method skips the cross-tenant query check, since the
   * column values API uses simple entity type definitions, which don't have cross-tenant queries enabled.
   * method skips the cross-tenant query check
   * @param entityType Entity type definition
   * @return           List of tenants to query
   */
  public List<String> getTenantsToQueryForColumnValues(EntityType entityType) {
    return getTenants(entityType, executionContext.getUserId());
  }

  private List<String> getTenants(EntityType entityType, UUID userId) {
    log.info("Getting tenants to query for user {}", userId);
    // Get the ECS tenant info first, since this comes from mod-users and should work in non-ECS environments
    // We can use this for determining if it's an ECS environment, and if so, retrieving the consortium ID and central tenant ID
    Map<String, String> ecsTenantInfo = getEcsTenantInfo();
    log.debug("Retrieved ECS tenant info: {}", ecsTenantInfo);

    if (!ecsEnabled(ecsTenantInfo)) {
      log.debug("ECS is not enabled. Querying only the current tenant: {}", executionContext.getTenantId());
      return List.of(executionContext.getTenantId());
    }

    String centralTenantId = getCentralTenantId(ecsTenantInfo);
    log.debug("Central Tenant ID retrieved: {}", centralTenantId);

    if (!executionContext.getTenantId().equals(centralTenantId)) {
      log.debug("Tenant {} is not central tenant. Running intra-tenant query.", executionContext.getTenantId());

      if (INSTANCE_RELATED_ENTITIES.contains(entityType.getId())) {
        log.debug("Entity type {} is related to instances. Querying both current and central tenant.", entityType.getId());
        return List.of(executionContext.getTenantId(), centralTenantId);
      }

      log.debug("Querying only the current tenant: {}", executionContext.getTenantId());
      return List.of(executionContext.getTenantId());
    }

    List<String> tenantsToQuery = new ArrayList<>();
    tenantsToQuery.add(centralTenantId);
    log.debug("Starting query with central tenant: {}", centralTenantId);

    List<Map<String, String>> userTenantMaps = getUserTenants(ecsTenantInfo.get("consortiumId"), userId.toString());
    log.debug("Retrieved user tenants: {}", userTenantMaps);

    for (var userMap : userTenantMaps) {
      String tenantId = userMap.get("tenantId");
      String currentUserId = userMap.get("userId");

      if (!tenantId.equals(centralTenantId)) {
        log.debug("Checking permissions for user {} in tenant {}", currentUserId, tenantId);
        try {
          permissionsService.verifyUserHasNecessaryPermissions(tenantId, entityType, UUID.fromString(currentUserId), true);
          tenantsToQuery.add(tenantId);
          log.debug("User {} has necessary permissions for tenant {}. Added to query list.", currentUserId, tenantId);
        } catch (MissingPermissionsException e) {
          log.info("User with id {} does not have permissions to query tenant {}. Skipping.", currentUserId, tenantId);
        } catch (FeignException e) {
          log.error("Error retrieving permissions for user ID {} in tenant {}. Skipping.", currentUserId, tenantId, e);
        }
      }
    }

    log.debug("Final list of tenants to query: {}", tenantsToQuery);
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
    boolean result = !(ecsTenantInfo == null || ecsTenantInfo.isEmpty());
    log.info("ECS enabled: {}", result);
    return result;
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
    log.info("zzz User tenant response: {}", userTenantsResponse);
    List<Map<String, String>> userTenants = JsonPath
      .parse(userTenantsResponse)
      .read("$.userTenants", List.class);

    return userTenants.stream()
      .findAny()
      .orElse(null);
  }

  private String getCentralTenantId(Map<String, String> ecsTenantInfo) {
    String centralTenantId = ecsTenantInfo != null ? ecsTenantInfo.get("centralTenantId") : null;
    log.info("Central Tenant ID: {}", centralTenantId);
    return centralTenantId;
  }

  private boolean isCentralTenant(Map<String, String> ecsTenantInfo) {
    String tenantId = executionContext.getTenantId();
    String centralTenantId = getCentralTenantId(ecsTenantInfo);
    boolean result = tenantId.equals(centralTenantId);
    log.info("Is Central Tenant: {}, Tenant ID: {}, Central Tenant ID: {}", result, tenantId, centralTenantId);
    return result;
  }
}
