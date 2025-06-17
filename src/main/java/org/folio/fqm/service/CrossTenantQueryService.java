package org.folio.fqm.service;

import com.jayway.jsonpath.JsonPath;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.fqm.client.CrossTenantHttpClient;
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

  private final CrossTenantHttpClient crossTenantClient;
  private final FolioExecutionContext executionContext;
  private final PermissionsService permissionsService;
  private final UserTenantService userTenantService;

  private static final String TENANT_ID = "tenantId";
  private static final String TENANT_NAME = "tenantName";
  private static final String COMPOSITE_INSTANCES_ID = "6b08439b-4f8e-4468-8046-ea620f5cfb74";
  private static final String SIMPLE_INSTANCES_ID = "8fc4a9d2-7ccf-4233-afb8-796911839862";
  private static final String SIMPLE_INSTANCE_STATUS_ID = "9c239bfd-198f-4013-bbc4-4551c0cbdeaa";
  private static final String SIMPLE_INSTANCE_TYPE_ID = "af44e2e0-12e0-4eec-b80d-49feb33a866c";
  private static final List<String> INSTANCE_RELATED_ENTITIES = List.of(SIMPLE_INSTANCES_ID, COMPOSITE_INSTANCES_ID, SIMPLE_INSTANCE_STATUS_ID, SIMPLE_INSTANCE_TYPE_ID);

  /**
   * Retrieve list of tenants to run query against.
   *
   * @param entityType Entity type definition
   * @return List of tenants to query
   */
  public List<String> getTenantsToQuery(EntityType entityType) {
    return getTenantsToQuery(entityType, executionContext.getUserId());
  }

  /**
   * Retrieve list of tenants to run query against for a specified user.
   *
   * @param entityType Entity type definition
   * @param userId     ID of user to retrieve tenant affiliations for
   * @return List of tenants to query
   */
  public List<String> getTenantsToQuery(EntityType entityType, UUID userId) {
    if (!Boolean.TRUE.equals(entityType.getCrossTenantQueriesEnabled())
      && !COMPOSITE_INSTANCES_ID.equals(entityType.getId())) {
      return List.of(executionContext.getTenantId());
    }
    return getTenantIds(entityType, userId);
  }

  /**
   * Retrieve list of tenants to retrieve column values from. This method skips the cross-tenant query check, since the
   * column values API uses simple entity type definitions, which don't have cross-tenant queries enabled.
   * method skips the cross-tenant query check
   *
   * @param entityType Entity type definition
   * @return List of tenants to query
   */
  public List<String> getTenantsToQueryForColumnValues(EntityType entityType) {
    return getTenantIds(entityType, executionContext.getUserId());
  }

  public List<Pair<String, String>> getTenantIdNamePairs(EntityType entityType, UUID userId) {
    log.info("Getting tenants to query for user {}", userId);

    Map<String, String> ecsTenantInfo = getEcsTenantInfo();
    log.info("ECS tenant info: {}", ecsTenantInfo);

    Pair<String, String> currentTenantPair = Pair.of(executionContext.getTenantId(), null);
    log.info("Current tenant ID: {}", executionContext.getTenantId());

    if (!ecsEnabled(ecsTenantInfo)) {
      log.info("ECS not enabled. Returning only current tenant: {}", currentTenantPair);
      return List.of(currentTenantPair);
    }

    String currentTenantId = executionContext.getTenantId();
    String centralTenantId = getCentralTenantId(ecsTenantInfo);
    String consortiumId = ecsTenantInfo.get("consortiumId");
    log.info("Current tenant ID: {}, Central tenant ID: {}, Consortium ID: {}", currentTenantId, centralTenantId, consortiumId);

    List<Map<String, String>> userTenantMaps = getUserTenants(consortiumId, userId.toString(), centralTenantId);
    log.info("User tenant maps: {}", userTenantMaps);

    String currentTenantName = userTenantMaps
      .stream()
      .filter(individualMap -> individualMap.get(TENANT_ID).equals(currentTenantId))
      .map(individualMap -> individualMap.get(TENANT_NAME))
      .findFirst()
      .orElse(null);
    log.info("Current tenant name: {}", currentTenantName);

    currentTenantPair = Pair.of(currentTenantId, currentTenantName);

    String centralTenantName = userTenantMaps
      .stream()
      .filter(individualMap -> individualMap.get(TENANT_ID).equals(centralTenantId))
      .map(individualMap -> individualMap.get(TENANT_NAME))
      .findFirst()
      .orElse(null);
    log.info("Central tenant name: {}", centralTenantName);

    Pair<String, String> centralTenantPair = Pair.of(centralTenantId, centralTenantName);

    if (!executionContext.getTenantId().equals(centralTenantId)) {
      log.debug("Tenant {} is not central tenant. Running intra-tenant query.", executionContext.getTenantId());
      if (INSTANCE_RELATED_ENTITIES.contains(entityType.getId())) {
        log.info("Entity type {} is instance-related. Returning current and central tenant pairs: {}, {}", entityType.getId(), currentTenantPair, centralTenantPair);
        return List.of(currentTenantPair, centralTenantPair);
      }
      log.info("Returning only current tenant pair: {}", currentTenantPair);
      return List.of(currentTenantPair);
    }

    List<Pair<String, String>> tenantsToQuery = new ArrayList<>();
    for (var userMap : userTenantMaps) {
      String tenantId = userMap.get(TENANT_ID);
      String currentUserId = userMap.get("userId");
      String tenantName = userMap.get(TENANT_NAME);
      Pair<String, String> currentTenantMap = Pair.of(tenantId, tenantName);
      log.info("Checking permissions for user {} on tenant {} ({})", currentUserId, tenantId, tenantName);
      try {
        permissionsService.verifyUserHasNecessaryPermissions(tenantId, entityType, UUID.fromString(currentUserId), true);
        tenantsToQuery.add(currentTenantMap);
        log.info("User {} has permissions for tenant {}. Added to tenantsToQuery.", currentUserId, tenantId);
      } catch (MissingPermissionsException e) {
        log.info("User with id {} does not have permissions to query tenant {}. Skipping.", currentUserId, tenantId);
      } catch (FeignException e) {
        log.error("Error retrieving permissions for user ID {} in tenant {}. Skipping.", currentUserId, tenantId, e);
      }
    }

    log.info("Final tenants to query: {}", tenantsToQuery);
    return tenantsToQuery;
  }

  private List<String> getTenantIds(EntityType entityType, UUID userId) {
    return getTenantIdNamePairs(entityType, userId)
      .stream()
      .map(Pair::getLeft)
      .toList();
  }

  @SuppressWarnings("unchecked")
  // JsonPath.parse is returning a plain List without a type parameter, and the TypeRef (vs Class) parameter to JsonPath.read is not supported by the JSON parser
  private List<Map<String, String>> getUserTenants(String consortiumId, String userId, String tenantId) {
    String userTenantResponse = crossTenantClient.get(
      "consortia/" + consortiumId + "/user-tenants",
      Map.of("userId", userId, "limit", "1000"),
      tenantId
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
  @SuppressWarnings("unchecked")
  // JsonPath.parse is returning a plain List without a type parameter, and the TypeRef (vs Class) parameter to JsonPath.read is not supported by the JSON parser
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
