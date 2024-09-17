package org.folio.fqm.service;

import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.exception.MissingPermissionsException;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrossTenantQueryServiceTest {

  private static final EntityType entityType = new EntityType()
    .crossTenantQueriesEnabled(true);

  @Mock
  private SimpleHttpClient ecsClient;

  @Mock
  private FolioExecutionContext executionContext;

  @Mock
  private PermissionsService permissionsService;

  @InjectMocks
  private CrossTenantQueryService crossTenantQueryService;

  private static final String ECS_TENANT_INFO = """
      {
          "userTenants": [
              {
                  "id": "06192681-0df7-4f33-a38f-48e017648d69",
                  "userId": "a5e7895f-503c-4335-8828-f507bc8d1c45",
                  "tenantId": "tenant_01",
                  "centralTenantId": "tenant_01",
                  "consortiumId": "bdaa4720-5e11-4632-bc10-d4455cf252df"
              }
          ],
          "totalRecords": 1
      }
      """;

  private static final String USER_TENANT_JSON = """
      {
          "userTenants": [
              {
                  "id": "06192681-0df7-4f33-a38f-48e017648d69",
                  "userId": "a5e7895f-503c-4335-8828-f507bc8d1c45",
                  "tenantId": "tenant_01"
              },
              {
                  "id": "3c1bfbe9-7d64-41fe-a358-cdaced6a631f",
                  "userId": "a5e7895f-503c-4335-8828-f507bc8d1c45",
                  "tenantId": "tenant_02"
              },
              {
                  "id": "b167837a-ecdd-482b-b5d3-79a391a1dbf1",
                  "userId": "a5e7895f-503c-4335-8828-f507bc8d1c45",
                  "tenantId": "tenant_03",
              }
          ]
      }
      """;

  private static final String ECS_TENANT_INFO_FOR_NON_ECS_ENV = """
      {
          "userTenants": [],
          "totalRecords": 0
      }
      """;

  @BeforeEach
  void setup() {
    lenient().when(executionContext.getUserId()).thenReturn(UUID.randomUUID());
  }

  @Test
  void shouldGetListOfTenantsToQuery() {
    String tenantId = "tenant_01";
    List<String> expectedTenants = List.of("tenant_01", "tenant_02", "tenant_03");

    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(ecsClient.get(eq("user-tenants"), anyMap())).thenReturn(ECS_TENANT_INFO);
    when(ecsClient.get(eq("consortia/bdaa4720-5e11-4632-bc10-d4455cf252df/user-tenants"), anyMap())).thenReturn(USER_TENANT_JSON);

    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(entityType, false);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldRunIntraTenantQueryForNonInstanceEntityTypes() {
    EntityType nonEcsEntityType = new EntityType(UUID.randomUUID().toString(), "test_entity_type", true, false);

    List<String> expectedTenants = List.of("tenant_01");
    when(executionContext.getTenantId()).thenReturn("tenant_01");
    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(nonEcsEntityType, false);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldRunIntraTenantQueryForNonCentralTenant() {
    List<String> expectedTenants = List.of("tenant_02");
    when(executionContext.getTenantId()).thenReturn("tenant_02"); // Central is tenant_01
    when(ecsClient.get(eq("user-tenants"), anyMap())).thenReturn(ECS_TENANT_INFO);
    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(entityType, false);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldRunIntraTenantQueryIfExceptionIsThrown() {
    List<String> expectedTenants = List.of("tenant_01");
    when(executionContext.getTenantId()).thenReturn("tenant_01");
    when(ecsClient.get(eq("user-tenants"), anyMap())).thenReturn(ECS_TENANT_INFO_FOR_NON_ECS_ENV);
    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(entityType, false);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldReturnTenantIdOnlyIfUserTenantsApiThrowsException() {
    String tenantId = "tenant_01";
    List<String> expectedTenants = List.of("tenant_01");

    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(ecsClient.get(eq("user-tenants"), anyMap())).thenReturn(ECS_TENANT_INFO_FOR_NON_ECS_ENV);

    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(entityType, false);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldNotQueryTenantIfUserLacksTenantPermissions() {
    String tenantId = "tenant_01";
    List<String> expectedTenants = List.of("tenant_01", "tenant_02");

    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(ecsClient.get(eq("user-tenants"), anyMap())).thenReturn(ECS_TENANT_INFO);
    when(ecsClient.get(eq("consortia/bdaa4720-5e11-4632-bc10-d4455cf252df/user-tenants"), anyMap())).thenReturn(USER_TENANT_JSON);
    doNothing().when(permissionsService).verifyUserHasNecessaryPermissions("tenant_02", entityType, true);
    doThrow(MissingPermissionsException.class).when(permissionsService).verifyUserHasNecessaryPermissions("tenant_03", entityType, true);
    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(entityType, false);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldQueryCentralTenantForSharedCompositeInstances() {
    String tenantId = "tenant_03";
    List<String> expectedTenants = List.of("tenant_03", "tenant_01");
    EntityType instanceEntityType = new EntityType()
      .id("6b08439b-4f8e-4468-8046-ea620f5cfb74")
      .crossTenantQueriesEnabled(true);

    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(ecsClient.get(eq("user-tenants"), anyMap())).thenReturn(ECS_TENANT_INFO);

    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(instanceEntityType, false);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldGetCentralTenantId() {
    when(ecsClient.get(eq("user-tenants"), anyMap())).thenReturn(ECS_TENANT_INFO);
    String expectedId = "tenant_01";
    String actualId = crossTenantQueryService.getCentralTenantId();
    assertEquals(expectedId, actualId);
  }

  @Test
  void shouldHandleErrorWhenGettingCentralTenantId() {
    when(ecsClient.get(eq("user-tenants"), anyMap())).thenReturn(ECS_TENANT_INFO_FOR_NON_ECS_ENV);
    assertNull(crossTenantQueryService.getCentralTenantId());
  }
}
