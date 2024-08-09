package org.folio.fqm.service;

import org.folio.fqm.client.SimpleHttpClient;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrossTenantQueryServiceTest {

  private static final EntityType entityType = new EntityType("6b08439b-4f8e-4468-8046-ea620f5cfb74", "test_entity_type", true, false)
    .crossTenantQueriesEnabled(true);

  @Mock
  private SimpleHttpClient ecsClient;

  @Mock
  private FolioExecutionContext executionContext;

  @InjectMocks
  private CrossTenantQueryService crossTenantQueryService;

  @Test
  void shouldGetListOfTenantsToQuery() {
    List<String> expectedTenants = List.of("tenant_01", "tenant_02", "tenant_03");
    String configurationJson = """
        {
          "centralTenantId": "tenant_01"
        }
      """;
    String consortiaJson = """
        {
          "consortia": [
            {
              "id": "bdaa4720-5e11-4632-bc10-d4455cf252df"
            }
          ]
        }
      """;
    String tenantJson = """
      {
        "tenants": [
          {
            "id": "tenant_01"
          },
          {
            "id": "tenant_02"
          },
          {
            "id": "tenant_03"
          },
        ]
      }
      """;
    when(executionContext.getTenantId()).thenReturn("tenant_01");
    when(ecsClient.get("consortia-configuration", Map.of())).thenReturn(configurationJson);
    when(ecsClient.get("consortia", Map.of())).thenReturn(consortiaJson);
    when(ecsClient.get("consortia/bdaa4720-5e11-4632-bc10-d4455cf252df/tenants", Map.of())).thenReturn(tenantJson);
    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(entityType);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldRunIntraTenantQueryForNonInstanceEntityTypes() {
    EntityType nonEcsEntityType = new EntityType(UUID.randomUUID().toString(), "test_entity_type", true, false);

    List<String> expectedTenants = List.of("tenant_01");
    when(executionContext.getTenantId()).thenReturn("tenant_01");
    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(nonEcsEntityType);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldRunIntraTenantQueryForNonCentralTenant() {
    List<String> expectedTenants = List.of("tenant_01");
    String configurationJson = """
        {
          "centralTenantId": "tenant_02"
        }
      """;
    when(executionContext.getTenantId()).thenReturn("tenant_01");
    when(ecsClient.get("consortia-configuration", Map.of())).thenReturn(configurationJson);
    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(entityType);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldRunIntraTenantQueryIfExceptionIsThrown() {
    List<String> expectedTenants = List.of("tenant_01");
    when(executionContext.getTenantId()).thenReturn("tenant_01");
    when(ecsClient.get("consortia-configuration", Map.of())).thenThrow(NotFoundException.class);
    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(entityType);
    assertEquals(expectedTenants, actualTenants);
  }
}
