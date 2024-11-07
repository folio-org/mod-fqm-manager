package org.folio.fqm.repository;

import static org.folio.fqm.utils.IdStreamerTestDataProvider.TEST_CONTENT_IDS;
import static org.folio.fqm.utils.IdStreamerTestDataProvider.TEST_GROUP_BY_ENTITY_TYPE_DEFINITION;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.folio.fql.model.EqualsCondition;
import org.folio.fql.model.Fql;
import org.folio.fql.model.field.FqlField;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.exception.MaxQuerySizeExceededException;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.service.CrossTenantQueryService;
import org.folio.fqm.service.EntityTypeFlatteningService;
import org.folio.fqm.service.LocalizationService;
import org.folio.fqm.service.PermissionsService;
import org.folio.fqm.service.UserTenantService;
import org.folio.fqm.utils.IdStreamerTestDataProvider;
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
  private QueryRepository queryRepository;
  QueryResultsRepository queryResultsRepository;

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
    queryRepository = mock(QueryRepository.class);
    queryResultsRepository = mock(QueryResultsRepository.class);


    EntityTypeFlatteningService entityTypeFlatteningService = new EntityTypeFlatteningService(entityTypeRepository, new ObjectMapper(), localizationService, executionContext, userTenantService);
    CrossTenantQueryService crossTenantQueryService = new CrossTenantQueryService(ecsClient, executionContext, permissionsService, userTenantService);
    this.idStreamer =
      new IdStreamer(
        context,
        entityTypeFlatteningService,
        crossTenantQueryService,
        mock(FolioExecutionContext.class),
        queryRepository,
        queryResultsRepository
      );
  }

  @Test
  void shouldFetchIdStreamForFql() {
    String tenantId = "tenant_01";
    Fql fql = new Fql("", new EqualsCondition(new FqlField("field1"), "value1"));
    List<List<String>> expectedIds = new ArrayList<>();
    TEST_CONTENT_IDS.forEach(contentId -> expectedIds.add(List.of(contentId.toString())));
    when(localizationService.localizeEntityType(any(EntityType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(NON_ECS_USER_TENANT_JSON);
    List<List<String>> actualIds = mockQueryRepositories();

    idStreamer.streamIdsInBatch(
      IdStreamerTestDataProvider.TEST_ENTITY_TYPE_DEFINITION,
      true,
      fql,
      2,
      100,
      UUID.randomUUID()
    );
    assertEquals(expectedIds, actualIds);
  }

  @Test
  void shouldUseAdditionalEcsConditionsInEcsEnvironment() {
    String tenantId = "tenant_01";
    Fql fql = new Fql("", new EqualsCondition(new FqlField("field1"), "value1"));
    List<List<String>> expectedIds = List.of(
      List.of("ecsValue")
    );
    when(localizationService.localizeEntityType(any(EntityType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(USER_TENANT_JSON);
    when(ecsClient.get(eq("consortia/0e88ed41-eadb-44c3-a7a7-f6572bbe06fc/user-tenants"), anyMap())).thenReturn(USER_TENANT_JSON);
    when(executionContext.getTenantId()).thenReturn("tenant_01");
    List<List<String>> actualIds = mockQueryRepositories();

    idStreamer.streamIdsInBatch(
      new EntityType().additionalEcsConditions(List.of("condition 1")).id("6b08439b-4f8e-4468-8046-ea620f5cfb74"),
      true,
      fql,
      2,
      100,
      UUID.randomUUID()
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
    when(localizationService.localizeEntityType(any(EntityType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(executionContext.getTenantId()).thenReturn("tenant_01");
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(USER_TENANT_JSON);
    when(ecsClient.get(eq("consortia/0e88ed41-eadb-44c3-a7a7-f6572bbe06fc/user-tenants"), anyMap())).thenReturn(USER_TENANT_JSON);
    List<List<String>> actualIds = mockQueryRepositories();

    idStreamer.streamIdsInBatch(
      IdStreamerTestDataProvider.TEST_ENTITY_TYPE_DEFINITION,
      true,
      fql,
      2,
      100,
      UUID.randomUUID()
    );
    assertEquals(expectedIds, actualIds);
  }

  @Test
  void shouldHandleGroupByFields() {
    String tenantId = "tenant_01";
    Fql fql = new Fql("", new EqualsCondition(new FqlField("field1"), "value1"));
    List<List<String>> expectedIds = new ArrayList<>();
    TEST_CONTENT_IDS.forEach(contentId -> expectedIds.add(List.of(contentId.toString())));
    when(localizationService.localizeEntityType(any(EntityType.class))).thenReturn(TEST_GROUP_BY_ENTITY_TYPE_DEFINITION);
    when(executionContext.getTenantId()).thenReturn("tenant_01");
    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(NON_ECS_USER_TENANT_JSON);
    List<List<String>> actualIds = mockQueryRepositories();

    idStreamer.streamIdsInBatch(
      IdStreamerTestDataProvider.TEST_GROUP_BY_ENTITY_TYPE_DEFINITION,
      true,
      fql,
      2,
      100,
      UUID.randomUUID()
    );
    assertEquals(expectedIds, actualIds);
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

  @Test
  void shouldHandleDataBatch() {
    UUID queryId = UUID.randomUUID();
    int maxQuerySize = 100;
    List<String[]> resultIds = List.of(
      new String[]{UUID.randomUUID().toString()},
      new String[]{UUID.randomUUID().toString()}
    );
    IdsWithCancelCallback idsWithCancelCallback = new IdsWithCancelCallback(resultIds, () -> {});
    Query expectedQuery = new Query(queryId, UUID.randomUUID(), "", List.of(), UUID.randomUUID(),
      OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null);
    when(queryRepository.getQuery(queryId, true)).thenReturn(Optional.of(expectedQuery));
    idStreamer.handleBatch(queryId, idsWithCancelCallback, maxQuerySize, new AtomicInteger(0));
    verify(queryResultsRepository, times(1)).saveQueryResults(queryId, resultIds);
  }

  @Test
  void shouldHandleDataBatchForCancelledQuery() {
    AtomicBoolean streamClosed = new AtomicBoolean(false);
    UUID queryId = UUID.randomUUID();
    int maxQuerySize = 100;
    List<String[]> resultIds = List.of(
      new String[]{UUID.randomUUID().toString()},
      new String[]{UUID.randomUUID().toString()}
    );
    IdsWithCancelCallback idsWithCancelCallback = new IdsWithCancelCallback(resultIds, () -> streamClosed.set(true));
    assertFalse(streamClosed.get());
    Query expectedQuery = new Query(queryId, UUID.randomUUID(), "", List.of(), UUID.randomUUID(),
      OffsetDateTime.now(), null, QueryStatus.CANCELLED, null);
    when(queryRepository.getQuery(queryId, true)).thenReturn(Optional.of(expectedQuery));
    idStreamer.handleBatch(queryId, idsWithCancelCallback, maxQuerySize, new AtomicInteger(0));
    assertTrue(streamClosed.get());
  }

  @Test
  void shouldThrowExceptionWhenMaxQuerySizeExceeded() {
    UUID queryId = UUID.randomUUID();
    int maxQuerySize = 1;
    List<String[]> resultIds = List.of(
      new String[]{UUID.randomUUID().toString()},
      new String[]{UUID.randomUUID().toString()}
    );
    IdsWithCancelCallback idsWithCancelCallback = new IdsWithCancelCallback(resultIds, () -> {});
    Query expectedQuery = new Query(queryId, UUID.randomUUID(), "", List.of(), UUID.randomUUID(),
      OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null);
    String expectedMessage = String.format("Query %s with size %d has exceeded the maximum size of %d.", queryId, 2, maxQuerySize);
    org.folio.fqm.domain.dto.Error expectedError = new Error().message(expectedMessage);
    when(queryRepository.getQuery(queryId, true)).thenReturn(Optional.of(expectedQuery));
    var total = new AtomicInteger(0);
    MaxQuerySizeExceededException actualException = assertThrows(MaxQuerySizeExceededException.class, () -> idStreamer.handleBatch(queryId, idsWithCancelCallback, maxQuerySize, total));
    assertEquals(expectedMessage, actualException.getMessage());
    assertEquals(expectedError, actualException.getError());
  }

  // Suppress the unchecked cast warning on the Class<T> cast below. We know the parameter type is List<String[]>, but can't
  // easily create a Class object for it.
  @SuppressWarnings("unchecked")
  private List<List<String>> mockQueryRepositories() {
    List<List<String>> actualIds = new ArrayList<>();
    when(queryRepository.getQuery(any(UUID.class), anyBoolean())).thenReturn(Optional.of(mock(Query.class)));
    doAnswer(invocation -> {
      List<String[]> ids = invocation.getArgument(1, List.class);
      ids.forEach(idSet -> actualIds.add(Arrays.asList(idSet)));
      return null;
    }).when(queryResultsRepository).saveQueryResults(any(UUID.class), any(List.class));
    return actualIds;
  }
}
