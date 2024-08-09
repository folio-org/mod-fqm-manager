package org.folio.fqm.service;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.exception.MissingPermissionsException;
import org.folio.querytool.domain.dto.EntityType;
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
  private final EntityTypeFlatteningService entityTypeFlatteningService;
  private final PermissionsService permissionsService;

  public List<String> getTenantsToQuery(UUID entityTypeId) {
    String centralTenantId;
    String consortiumId;
    // List of ECS tenants and shadow users corresponding to this user in those tenants
    List<Map<String, String>> userTenantMaps;
    // Below if-block is necessary to limit cross-tenant querying to instance entity type until we have a way to
    // configure cross-tenant queries by entity type. This can be removed after completion of MODFQMMGR-335.
    if (!entityTypeId.equals(UUID.fromString("6b08439b-4f8e-4468-8046-ea620f5cfb74"))) {
      return List.of(executionContext.getTenantId());
    }

    EntityType entityType = entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null);

    try {
      String configurationJson = ecsClient.get("consortia-configuration", Map.of());
      centralTenantId = JsonPath
        .parse(configurationJson)
        .read("centralTenantId");
      if (centralTenantId.equals(executionContext.getTenantId())) {
        String consortiumIdJson = ecsClient.get("consortia", Map.of());
        consortiumId =  JsonPath
          .parse(consortiumIdJson)
          .read("consortia[0].id");
      } else {
        log.debug("Tenant {} is not central tenant. Running intra-tenant query.", executionContext.getTenantId());
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
      String rawJson = ecsClient.get("consortia-configuration", Map.of());
      DocumentContext parsedJson = JsonPath.parse(rawJson);
      return parsedJson.read("centralTenantId");
    } catch (Exception e) {
      log.debug("Error retrieving tenants for cross-tenant query. Tenant may not be in an ECS environment.");
      return null;
    }
  }
}
