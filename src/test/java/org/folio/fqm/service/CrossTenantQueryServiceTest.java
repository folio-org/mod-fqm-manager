package org.folio.fqm.service;

import feign.FeignException;
import org.folio.fqm.client.CrossTenantHttpClient;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrossTenantQueryServiceTest {

  private static final EntityType entityType = new EntityType()
    .id(UUID.randomUUID().toString())
    .crossTenantQueriesEnabled(true);

  @Mock
  private CrossTenantHttpClient crossTenantClient;

  @Mock
  private FolioExecutionContext executionContext;

  @Mock
  private PermissionsService permissionsService;

  @Mock
  private UserTenantService userTenantService;

  @InjectMocks
  private CrossTenantQueryService crossTenantQueryService;

  private static final String USER_ID = "a5e7895f-503c-4335-8828-f507bc8d1c45";
  private static final String CONSORTIUM_ID = "bdaa4720-5e11-4632-bc10-d4455cf252df";
  private static final String CENTRAL_TENANT_ID = "tenant_01";
  private static final String ECS_TENANT_INFO = """
    {
        "userTenants": [
            {
                "id": "06192681-0df7-4f33-a38f-48e017648d69",
                "userId": "a5e7895f-503c-4335-8828-f507bc8d1c45",
                "tenantId": "tenant_01",
                "tenantName": "Tenant 01",
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
                "tenantId": "tenant_01",
                "tenantName": "Tenant 01",
                "centralTenantId": "tenant_01"
            },
            {
                "id": "3c1bfbe9-7d64-41fe-a358-cdaced6a631f",
                "userId": "a5e7895f-503c-4335-8828-f507bc8d1c45",
                "tenantId": "tenant_02",
                "tenantName": "Tenant 02",
                "centralTenantId": "tenant_01"
            },
            {
                "id": "b167837a-ecdd-482b-b5d3-79a391a1dbf1",
                "userId": "a5e7895f-503c-4335-8828-f507bc8d1c45",
                "tenantId": "tenant_03",
                "tenantName": "Tenant 03",
                "centralTenantId": "tenant_01"
            },
            {
                "id": "b167837a-ecdd-482b-b5d3-79a391a1dbf1",
                "userId": "a5e7895f-503c-4335-8828-f507bc8d1c45",
                "tenantId": "tenant_04",
                "tenantName": "Tenant 04",
                "centralTenantId": "tenant_01"
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
    lenient().when(executionContext.getUserId()).thenReturn(UUID.fromString(USER_ID));
  }

  @Test
  void shouldGetListOfTenantsToQuery() {
    String tenantId = "tenant_01";
    List<String> expectedTenants = List.of("tenant_01", "tenant_02", "tenant_03", "tenant_04");

    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(ECS_TENANT_INFO);
    when(crossTenantClient.get(
      "consortia/bdaa4720-5e11-4632-bc10-d4455cf252df/user-tenants",
      Map.of("userId", USER_ID, "limit", "1000"),
      tenantId
    )).thenReturn(USER_TENANT_JSON);

    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(entityType);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldRunIntraTenantQueryForNonInstanceEntityTypes() {
    EntityType nonEcsEntityType = new EntityType(UUID.randomUUID().toString(), "test_entity_type", true);

    List<String> expectedTenants = List.of("tenant_01");
    when(executionContext.getTenantId()).thenReturn("tenant_01");
    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(nonEcsEntityType);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldRunIntraTenantQueryForNonCentralTenant() {
    String tenantId = "tenant_02";
    List<String> expectedTenants = List.of(tenantId);
    when(executionContext.getTenantId()).thenReturn(tenantId); // Central is tenant_01
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(ECS_TENANT_INFO);
    when(crossTenantClient.get(
      "consortia/" + CONSORTIUM_ID + "/user-tenants",
      Map.of("userId", USER_ID, "limit", "1000"),
      CENTRAL_TENANT_ID
    )).thenReturn(USER_TENANT_JSON);
    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(entityType);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldRunIntraTenantQueryIfExceptionIsThrown() {
    String tenantId = "tenant_01";
    List<String> expectedTenants = List.of(tenantId);
    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(ECS_TENANT_INFO_FOR_NON_ECS_ENV);
    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(entityType);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldReturnTenantIdOnlyIfUserTenantsApiThrowsException() {
    String tenantId = "tenant_01";
    List<String> expectedTenants = List.of("tenant_01");

    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(ECS_TENANT_INFO_FOR_NON_ECS_ENV);

    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(entityType);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldAttemptCrossTenantQueryIfForceParamIsTrue() {
    String tenantId = "tenant_01";
    List<String> expectedTenants = List.of("tenant_01");

    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(ECS_TENANT_INFO_FOR_NON_ECS_ENV);

    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(entityType);
    verify(userTenantService, times(1)).getUserTenantsResponse(tenantId);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldNotQueryTenantIfUserLacksTenantPermissions() {
    List<String> expectedTenants = List.of("tenant_01", "tenant_02");

    when(executionContext.getTenantId()).thenReturn(CENTRAL_TENANT_ID);
    when(executionContext.getUserId()).thenReturn(UUID.fromString(USER_ID));
    when(userTenantService.getUserTenantsResponse(CENTRAL_TENANT_ID)).thenReturn(ECS_TENANT_INFO);
    when(crossTenantClient.get(
      "consortia/bdaa4720-5e11-4632-bc10-d4455cf252df/user-tenants",
      Map.of("userId", USER_ID, "limit", "1000"),
      CENTRAL_TENANT_ID
    )).thenReturn(USER_TENANT_JSON);
    doNothing().when(permissionsService).verifyUserHasNecessaryPermissions(CENTRAL_TENANT_ID, entityType, UUID.fromString(USER_ID), true);
    doNothing().when(permissionsService).verifyUserHasNecessaryPermissions("tenant_02", entityType, UUID.fromString(USER_ID), true);
    doThrow(MissingPermissionsException.class).when(permissionsService).verifyUserHasNecessaryPermissions("tenant_03", entityType, UUID.fromString(USER_ID), true);
    doThrow(FeignException.class).when(permissionsService).verifyUserHasNecessaryPermissions("tenant_04", entityType, UUID.fromString(USER_ID), true);
    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(entityType);
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
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(ECS_TENANT_INFO);
    when(crossTenantClient.get(
      "consortia/bdaa4720-5e11-4632-bc10-d4455cf252df/user-tenants",
      Map.of("userId", USER_ID, "limit", "1000"),
      CENTRAL_TENANT_ID
    )).thenReturn(USER_TENANT_JSON);

    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(instanceEntityType);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldGetListOfTenantsToQueryForColumnValues() {
    String tenantId = "tenant_01";
    List<String> expectedTenants = List.of("tenant_01", "tenant_02", "tenant_03", "tenant_04");

    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(ECS_TENANT_INFO);
    when(crossTenantClient.get(
      "consortia/bdaa4720-5e11-4632-bc10-d4455cf252df/user-tenants",
      Map.of("userId", USER_ID, "limit", "1000"),
      CENTRAL_TENANT_ID
    )).thenReturn(USER_TENANT_JSON);

    List<String> actualTenants = crossTenantQueryService.getTenantsToQueryForColumnValues(entityType);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldGetListOfTenantsToQueryForSpecifiedUser() {
    String tenantId = "tenant_01";
    UUID userId = UUID.randomUUID();
    List<String> expectedTenants = List.of("tenant_01", "tenant_02", "tenant_03", "tenant_04");

    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(ECS_TENANT_INFO);
    when(crossTenantClient.get(
      "consortia/bdaa4720-5e11-4632-bc10-d4455cf252df/user-tenants",
      Map.of("userId", userId.toString(), "limit", "1000"),
      CENTRAL_TENANT_ID
    )).thenReturn(USER_TENANT_JSON);

    List<String> actualTenants = crossTenantQueryService.getTenantsToQuery(entityType, userId);
    assertEquals(expectedTenants, actualTenants);
  }

  @Test
  void shouldGetCentralTenantId() {
    String expectedId = "tenant_01";
    when(executionContext.getTenantId()).thenReturn(expectedId);
    when(userTenantService.getUserTenantsResponse(expectedId)).thenReturn(ECS_TENANT_INFO);
    String actualId = crossTenantQueryService.getCentralTenantId();
    assertEquals(expectedId, actualId);
  }

  @Test
  void shouldHandleErrorWhenGettingCentralTenantId() {
    String tenantId = "tenant_01";
    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(ECS_TENANT_INFO_FOR_NON_ECS_ENV);
    assertNull(crossTenantQueryService.getCentralTenantId());
  }

  @Test
  void testIsCentralTenant() {
    String tenantId = "tenant_01";
    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(USER_TENANT_JSON);
    assertTrue(crossTenantQueryService.isCentralTenant());
  }
}
