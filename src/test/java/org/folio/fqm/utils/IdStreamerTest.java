package org.folio.fqm.utils;

import static org.folio.fqm.utils.IdStreamerTestDataProvider.TEST_CONTENT_IDS;
import static org.folio.fqm.utils.IdStreamerTestDataProvider.TEST_GROUP_BY_ENTITY_TYPE_DEFINITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.folio.fql.model.EqualsCondition;
import org.folio.fql.model.Fql;
import org.folio.fql.model.field.FqlField;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.repository.IdStreamer;
import org.folio.fqm.service.CrossTenantQueryService;
import org.folio.fqm.service.EntityTypeFlatteningService;
import org.folio.fqm.service.LocalizationService;
import org.folio.fqm.service.PermissionsService;
import org.folio.fqm.service.UserTenantService;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * NOTE - Tests in this class depends on the mock results returned from {@link IdStreamerTestDataProvider} class
 */
@RunWith(MockitoJUnitRunner.class)
class IdStreamerTest {

  private IdStreamer idStreamer;
  private LocalizationService localizationService;
  private FolioExecutionContext executionContext;
  private SimpleHttpClient ecsClient;
  private UserTenantService userTenantService;

  private static final String USER_TENANT_JSON = """
      {
          "userTenants": [
              {
                  "id": "06192681-0df7-4f33-a38f-48e017648d69",
                  "userId": "a5e7895f-503c-4335-8828-f507bc8d1c45",
                  "tenantId": "tenant_01",
                  "centralTenantId": "tenant_01",
                  "consortiumId": "0e88ed41-eadb-44c3-a7a7-f6572bbe06fc"
              },
              {
                  "id": "3c1bfbe9-7d64-41fe-a358-cdaced6a631f",
                  "userId": "a5e7895f-503c-4335-8828-f507bc8d1c45",
                  "tenantId": "tenant_02",
                  "centralTenantId": "tenant_01",
                  "consortiumId": "0e88ed41-eadb-44c3-a7a7-f6572bbe06fc"
              },
              {
                  "id": "b167837a-ecdd-482b-b5d3-79a391a1dbf1",
                  "userId": "a5e7895f-503c-4335-8828-f507bc8d1c45",
                  "tenantId": "tenant_03",
                  "centralTenantId": "tenant_01",
                  "consortiumId": "0e88ed41-eadb-44c3-a7a7-f6572bbe06fc"
              }
          ],
          "totalRecords": 3
      }
      """;
  private static final String NON_ECS_USER_TENANT_JSON = """
      {
          "userTenants": [],
          "totalRecords": 0
      }
      """;

  @BeforeEach
  void setup() {
    DSLContext readerContext = DSL.using(
      new MockConnection(new IdStreamerTestDataProvider()),
      SQLDialect.POSTGRES
    );
    DSLContext context = DSL.using(
      new MockConnection(new IdStreamerTestDataProvider()),
      SQLDialect.POSTGRES
    );

    executionContext = mock(FolioExecutionContext.class);
    when(executionContext.getUserId()).thenReturn(UUID.randomUUID());
    EntityTypeRepository entityTypeRepository = new EntityTypeRepository(
      readerContext,
      context,
      new ObjectMapper(),
      executionContext,
      0);
    localizationService = mock(LocalizationService.class);
    ecsClient = mock(SimpleHttpClient.class);
    userTenantService = mock(UserTenantService.class);
    PermissionsService permissionsService = mock(PermissionsService.class);


    EntityTypeFlatteningService entityTypeFlatteningService = new EntityTypeFlatteningService(entityTypeRepository, new ObjectMapper(), localizationService, executionContext, userTenantService);
    CrossTenantQueryService crossTenantQueryService = new CrossTenantQueryService(ecsClient, executionContext, permissionsService, userTenantService);
    this.idStreamer =
      new IdStreamer(
        context,
        entityTypeFlatteningService,
        crossTenantQueryService,
        mock(FolioExecutionContext.class)
      );
  }

  @Test
  void shouldFetchIdStreamForFql() {
    String tenantId = "tenant_01";
    Fql fql = new Fql("", new EqualsCondition(new FqlField("field1"), "value1"));
    List<List<String>> expectedIds = new ArrayList<>();
    TEST_CONTENT_IDS.forEach(contentId -> expectedIds.add(List.of(contentId.toString())));
    List<List<String>> actualIds = new ArrayList<>();
    Consumer<IdsWithCancelCallback> idsConsumer = idsWithCancelCallback -> {
      List<String[]> ids = idsWithCancelCallback.ids();
      ids.forEach(idSet -> actualIds.add(Arrays.asList(idSet)));
    };
    when(localizationService.localizeEntityType(any(EntityType.class), anyBoolean())).thenAnswer(invocation -> invocation.getArgument(0));
<<<<<<< HEAD
    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(NON_ECS_USER_TENANT_JSON);
=======
    when(executionContext.getTenantId()).thenReturn("tenant_01");
<<<<<<< HEAD
    when(ecsClient.get(eq("user-tenants"), anyMap(), )).thenReturn(NON_ECS_USER_TENANT_JSON);
>>>>>>> 94097470 (MODFQMMGR-468: Aggregate tenant locations across all tenants)
=======
    when(ecsClient.get(eq("user-tenants"), anyMap())).thenReturn(NON_ECS_USER_TENANT_JSON);
>>>>>>> ee98bcd2 (Overload simplehttpclient get with tenant param)
    int idsCount = idStreamer.streamIdsInBatch(
      IdStreamerTestDataProvider.TEST_ENTITY_TYPE_DEFINITION,
      true,
      fql,
      2,
      idsConsumer
    );
    assertEquals(expectedIds, actualIds);
    assertEquals(IdStreamerTestDataProvider.TEST_CONTENT_IDS.size(), idsCount);
  }

  @Test
  void shouldUseAdditionalEcsConditionsInEcsEnvironment() {
    String tenantId = "tenant_01";
    Fql fql = new Fql("", new EqualsCondition(new FqlField("field1"), "value1"));
    List<List<String>> expectedIds = List.of(
      List.of("ecsValue")
    );
    List<List<String>> actualIds = new ArrayList<>();
    Consumer<IdsWithCancelCallback> idsConsumer = idsWithCancelCallback -> {
      List<String[]> ids = idsWithCancelCallback.ids();
      ids.forEach(idSet -> actualIds.add(Arrays.asList(idSet)));
    };
    when(localizationService.localizeEntityType(any(EntityType.class), anyBoolean())).thenAnswer(invocation -> invocation.getArgument(0));
<<<<<<< HEAD
<<<<<<< HEAD
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(USER_TENANT_JSON);
    when(ecsClient.get(eq("consortia/0e88ed41-eadb-44c3-a7a7-f6572bbe06fc/user-tenants"), anyMap())).thenReturn(USER_TENANT_JSON);
=======
    when(ecsClient.get(eq("user-tenants"), anyMap(), )).thenReturn(USER_TENANT_JSON);
>>>>>>> 94097470 (MODFQMMGR-468: Aggregate tenant locations across all tenants)
=======
    when(ecsClient.get(eq("user-tenants"), anyMap())).thenReturn(USER_TENANT_JSON);
>>>>>>> ee98bcd2 (Overload simplehttpclient get with tenant param)
    when(executionContext.getTenantId()).thenReturn("tenant_01");

    idStreamer.streamIdsInBatch(
      new EntityType().additionalEcsConditions(List.of("condition 1")).id("6b08439b-4f8e-4468-8046-ea620f5cfb74"),
      true,
      fql,
      2,
      idsConsumer
    );
    assertEquals(expectedIds, actualIds);
  }

  @Test
  void shouldUseUnionAllForCrossTenantQuery() {
    String tenantId = "tenant_01";
    when(executionContext.getTenantId()).thenReturn("tenant_01");
    Fql fql = new Fql("", new EqualsCondition(new FqlField("field1"), "value1"));
    List<List<String>> expectedIds = new ArrayList<>();
    TEST_CONTENT_IDS.forEach(contentId -> expectedIds.add(List.of(contentId.toString())));
    List<List<String>> actualIds = new ArrayList<>();
    Consumer<IdsWithCancelCallback> idsConsumer = idsWithCancelCallback -> {
      List<String[]> ids = idsWithCancelCallback.ids();
      ids.forEach(idSet -> actualIds.add(Arrays.asList(idSet)));
    };
    when(localizationService.localizeEntityType(any(EntityType.class), anyBoolean())).thenAnswer(invocation -> invocation.getArgument(0));
    when(executionContext.getTenantId()).thenReturn("tenant_01");
<<<<<<< HEAD
<<<<<<< HEAD
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(USER_TENANT_JSON);
    when(ecsClient.get(eq("consortia/0e88ed41-eadb-44c3-a7a7-f6572bbe06fc/user-tenants"), anyMap())).thenReturn(USER_TENANT_JSON);
=======
    when(ecsClient.get(eq("user-tenants"), anyMap(), )).thenReturn(USER_TENANT_JSON);
>>>>>>> 94097470 (MODFQMMGR-468: Aggregate tenant locations across all tenants)
=======
    when(ecsClient.get(eq("user-tenants"), anyMap())).thenReturn(USER_TENANT_JSON);
>>>>>>> ee98bcd2 (Overload simplehttpclient get with tenant param)
    int idsCount = idStreamer.streamIdsInBatch(
      IdStreamerTestDataProvider.TEST_ENTITY_TYPE_DEFINITION,
      true,
      fql,
      2,
      idsConsumer
    );
    assertEquals(expectedIds, actualIds);
    assertEquals(IdStreamerTestDataProvider.TEST_CONTENT_IDS.size(), idsCount);
  }

  @Test
  void shouldHandleGroupByFields() {
    String tenantId = "tenant_01";
    Fql fql = new Fql("", new EqualsCondition(new FqlField("field1"), "value1"));
    List<List<String>> expectedIds = new ArrayList<>();
    TEST_CONTENT_IDS.forEach(contentId -> expectedIds.add(List.of(contentId.toString())));
    List<List<String>> actualIds = new ArrayList<>();
    Consumer<IdsWithCancelCallback> idsConsumer = idsWithCancelCallback -> {
      List<String[]> ids = idsWithCancelCallback.ids();
      ids.forEach(idSet -> actualIds.add(Arrays.asList(idSet)));
    };
    when(localizationService.localizeEntityType(any(EntityType.class), anyBoolean())).thenReturn(TEST_GROUP_BY_ENTITY_TYPE_DEFINITION);
    when(executionContext.getTenantId()).thenReturn("tenant_01");
<<<<<<< HEAD
<<<<<<< HEAD
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(NON_ECS_USER_TENANT_JSON);
=======
    when(ecsClient.get(eq("user-tenants"), anyMap(), )).thenReturn(NON_ECS_USER_TENANT_JSON);
>>>>>>> 94097470 (MODFQMMGR-468: Aggregate tenant locations across all tenants)
=======
    when(ecsClient.get(eq("user-tenants"), anyMap())).thenReturn(NON_ECS_USER_TENANT_JSON);
>>>>>>> ee98bcd2 (Overload simplehttpclient get with tenant param)
    int idsCount = idStreamer.streamIdsInBatch(
      IdStreamerTestDataProvider.TEST_GROUP_BY_ENTITY_TYPE_DEFINITION,
      true,
      fql,
      2,
      idsConsumer
    );
    assertEquals(expectedIds, actualIds);
    assertEquals(IdStreamerTestDataProvider.TEST_CONTENT_IDS.size(), idsCount);
  }

  @Test
  void shouldGetSortedIds() {
    UUID queryId = UUID.randomUUID();
    int offset = 0;
    int limit = 0;
    String derivedTableName = "query_results";
    List<List<String>> expectedIds = new ArrayList<>();
    TEST_CONTENT_IDS.forEach(contentId -> expectedIds.add(List.of(contentId.toString())));
    List<List<String>> actualIds = idStreamer.getSortedIds(
      derivedTableName,
      offset,
      limit,
      queryId
    );
    assertEquals(expectedIds, actualIds);
  }
}
