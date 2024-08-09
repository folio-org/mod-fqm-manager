package org.folio.fqm.service;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Log4j2
public class CrossTenantQueryService {

  private final SimpleHttpClient ecsClient;
  private final FolioExecutionContext executionContext;

  public List<String> getTenantsToQuery(EntityType entityType) {
    if (!Boolean.TRUE.equals(entityType.getCrossTqenantQueriesEnabled())) {
      return List.of(executionContext.getTenantId());
    }
    String centralTenantId;
    String consortiumId;
    try {
      String rawJson = ecsClient.get("consortia-configuration", Map.of());
      DocumentContext parsedJson = JsonPath.parse(rawJson);
      centralTenantId = parsedJson.read("centralTenantId");
      if (centralTenantId.equals(executionContext.getTenantId())) {
        String consortiumIdJson = ecsClient.get("consortia",Map.of());
        DocumentContext parsedConsortiumIdJson = JsonPath.parse(consortiumIdJson);
        consortiumId = parsedConsortiumIdJson.read("consortia[0].id");
      } else {
        log.debug("Tenant {} is not central tenant", executionContext.getTenantId());
        return List.of(executionContext.getTenantId());
      }
    } catch (Exception e) {
      log.debug("Error retrieving tenants for cross-tenant query. Tenant may not be in an ECS environment.");
      return List.of(executionContext.getTenantId());
    }

    List<String> tenantsToQuery = new ArrayList<>();
    String json = ecsClient.get("consortia/" + consortiumId + "/tenants", Map.of());
    DocumentContext jsonContext = JsonPath.parse(json);
    List<HashMap<String, String>> tenantHashMaps = jsonContext.read("$.tenants", List.class);

    for (HashMap<String, String> tenantMap : tenantHashMaps) {
      tenantsToQuery.add(tenantMap.get("id"));
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
