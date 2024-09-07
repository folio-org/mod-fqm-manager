package org.folio.fqm.service;

import com.jayway.jsonpath.DocumentContext;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Log4j2
public class CrossTenantQueryService {

  private final SimpleHttpClient ecsClient;
  private final FolioExecutionContext executionContext;
  private final PermissionsService permissionsService;

  private static final String CROSS_TENANT_QUERY_ERROR = "Error retrieving tenants for cross-tenant query. Tenant may not be in an ECS environment.";
  private static final String CONSORTIA_CONFIGURATION_PATH = "consortia-configuration";
  private static final String CENTRAL_TENANT_ID = "centralTenantId";
  private static final String COMPOSITE_INSTANCES_ID = "6b08439b-4f8e-4468-8046-ea620f5cfb74";

  public List<String> getTenantsToQuery(EntityType entityType, boolean forceCrossTenantQuery) {
    if (!forceCrossTenantQuery && !Boolean.TRUE.equals(entityType.getCrossTenantQueriesEnabled())) {
      return List.of(executionContext.getTenantId());
    }
    // List of shadow users associated with this user and the ECS tenants that those users exist in
    List<Map<String, String>> userTenantMaps;
    String centralTenantId;
    String consortiumId;
    try {
      String configurationJson = ecsClient.get(CONSORTIA_CONFIGURATION_PATH, Map.of());
      centralTenantId = JsonPath
        .parse(configurationJson)
        .read(CENTRAL_TENANT_ID);
      if (centralTenantId.equals(executionContext.getTenantId())) {
        String consortiumIdJson = ecsClient.get("consortia", Map.of());
        consortiumId =  JsonPath
          .parse(consortiumIdJson)
          .read("consortia[0].id");
      } else {
        log.debug("Tenant {} is not central tenant. Running intra-tenant query.", executionContext.getTenantId());
        // The Instances entity type is required to retrieve shared instances from the central tenant when
        // running queries from member tenants. This means that if we are running a query for Instances, we need to
        // query the current tenant (for local records) as well as the central tenant (for shared records).
        if (COMPOSITE_INSTANCES_ID.equals(entityType.getId())) {
          return List.of(executionContext.getTenantId(), centralTenantId);
        }
        return List.of(executionContext.getTenantId());
      }
      UUID userId = executionContext.getUserId();
      String userTenantResponse = ecsClient.get(
        "consortia/" + consortiumId + "/user-tenants",
        Map.of("userId", userId.toString())
      );
      userTenantMaps = JsonPath
        .parse(userTenantResponse)
        .read("$.userTenants", List.class);
    } catch (Exception e) {
      log.debug("Error retrieving tenants for cross-tenant query. Running intra-tenant query.");
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

  public String getCentralTenantId() {
    try {
      String rawJson = ecsClient.get(CONSORTIA_CONFIGURATION_PATH, Map.of());
      DocumentContext parsedJson = JsonPath.parse(rawJson);
      return parsedJson.read(CENTRAL_TENANT_ID);
    } catch (Exception e) {
      log.debug(CROSS_TENANT_QUERY_ERROR);
      return null;
    }
  }

  public boolean ecsEnabled() {
    try {
      String rawJson = ecsClient.get(CONSORTIA_CONFIGURATION_PATH, Map.of());
      DocumentContext parsedJson = JsonPath.parse(rawJson);
      parsedJson.read(CENTRAL_TENANT_ID);
      return true;
    } catch (Exception e) {
      log.debug(CROSS_TENANT_QUERY_ERROR);
      return false;
    }
  }
}
