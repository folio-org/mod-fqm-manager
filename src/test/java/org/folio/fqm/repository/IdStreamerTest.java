package org.folio.fqm.repository;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.folio.fql.model.EqualsCondition;
import org.folio.fql.model.Fql;
import org.folio.fql.model.field.FqlField;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.exception.MaxQuerySizeExceededException;
import org.folio.fqm.exception.QueryNotFoundException;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.service.CrossTenantQueryService;
import org.folio.fqm.service.EntityTypeFlatteningService;
import org.folio.fqm.service.LocalizationService;
import org.folio.fqm.service.UserTenantService;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.RangedUUIDType;
import org.folio.spring.FolioExecutionContext;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.SelectSelectStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

//@RunWith(MockitoJUnitRunner.class)
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("db-test")
@SpringBootTest
class IdStreamerTest {

  private static final UUID IN_PROGRESS_QUERY_ID = UUID.fromString("d81b10cb-9511-4541-9646-3aaec72e41e0");
  private static final UUID CANCELLED_QUERY_ID = UUID.fromString("6dbe7cf6-ef5f-40b2-a0f2-69a705cb94c8");
  private static final List<String> CONTENT_IDS = List.of("123e4567-e89b-12d3-a456-426614174000", "223e4567-e89b-12d3-a456-426614174001");
  private static final EntityType BASIC_ENTITY_TYPE = new EntityType()
    .name("entity_type_01")
    .id("0cb79a4c-f7eb-4941-a104-745224ae0291")
    .columns(List.of(
      new EntityTypeColumn()
        .name("id")
        .dataType(new RangedUUIDType().dataType("rangedUUIDType"))
        .isIdColumn(true)
        .sourceAlias("source_1")
        .valueGetter("id"),
      new EntityTypeColumn()
        .name("column_01")
        .dataType(new RangedUUIDType().dataType("stringType"))
        .sourceAlias("source_1")
        .valueGetter("column_01")
    ))
    .sources(List.of(new EntityTypeSource().alias("source_1")));
  private static final EntityType ECS_ENTITY_TYPE = new EntityType()
    .name("ecs_entity_type")
    .id("abababab-f7eb-4941-a104-45224ae0ffff")
    .columns(List.of(
      new EntityTypeColumn()
        .name("id")
        .dataType(new RangedUUIDType().dataType("rangedUUIDType"))
        .isIdColumn(true)
        .sourceAlias("source_1")
        .valueGetter("id"),
      new EntityTypeColumn()
        .name("column_01")
        .dataType(new RangedUUIDType().dataType("stringType"))
        .sourceAlias("source_1")
        .valueGetter("column_01")
    ))
    .sources(List.of(new EntityTypeSource().alias("source_1")))
    .additionalEcsConditions(List.of("true = true"));
  private static final EntityType GROUP_BY_ENTITY_TYPE = new EntityType()
    .name("group_by_entity_type")
    .id("47593013-f7eb-4941-a104-45224ae0ffbc")
    .columns(List.of(
      new EntityTypeColumn()
        .name("id")
        .dataType(new RangedUUIDType().dataType("rangedUUIDType"))
        .isIdColumn(true)
        .sourceAlias("source_1")
        .valueGetter("id"),
      new EntityTypeColumn()
        .name("column_01")
        .dataType(new RangedUUIDType().dataType("stringType"))
        .sourceAlias("source_1")
        .valueGetter("column_01")
    ))
    .sources(List.of(new EntityTypeSource().alias("source_1")))
    .groupByFields(List.of("id", "column_01"));

  //  @Autowired
  private IdStreamer idStreamer;
  @MockBean
  private LocalizationService localizationService;
  @Mock
  private FolioExecutionContext executionContext;
  private SimpleHttpClient ecsClient;
  @Mock
  private UserTenantService userTenantService;
  private EntityTypeFlatteningService entityTypeFlatteningService;
  private CrossTenantQueryService crossTenantQueryService;
  //  @MockBean
//  @Autowired
  private QueryRepository queryRepository;
  @Autowired
  private ScheduledExecutorService executorService;
  @Autowired
  @Qualifier("readerJooqContext")
  private DSLContext context;
  //  @Autowired
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
//    DSLContext readerContext = DSL.using(
//      new MockConnection(new IdStreamerTestDataProvider()),
//      SQLDialect.POSTGRES
//    );
//    DSLContext context = DSL.using(
//      new MockConnection(new IdStreamerTestDataProvider()),
//      SQLDialect.POSTGRES
//    );
////    DSLContext readerContext = mock(DSLContext.class);
////    DSLContext context = mock(DSLContext.class);
//
//    executionContext = mock(FolioExecutionContext.class);
////    when(executionContext.getUserId()).thenReturn(UUID.randomUUID());
//    EntityTypeRepository entityTypeRepository = new EntityTypeRepository(
//      readerContext,
//      context,
//      new ObjectMapper(),
//      executionContext,
//      0);
//    localizationService = mock(LocalizationService.class);
//    ecsClient = mock(SimpleHttpClient.class);
//    userTenantService = mock(UserTenantService.class);
//    PermissionsService permissionsService = mock(PermissionsService.class);
//    queryRepository = mock(QueryRepository.class);
//    queryResultsRepository = mock(QueryResultsRepository.class);
////    executorService = mock(ScheduledExecutorService.class);
//    executorService = Executors.newScheduledThreadPool(1);
//    QueryRepository testRepo = mock(QueryRepository.class);
//    System.out.println("queryRepository mock status: " + Mockito.mockingDetails(queryRepository).isMock());
//    System.out.println("queryResultsRepository mock status: " + Mockito.mockingDetails(queryResultsRepository).isMock());
//    System.out.println("ecsClient mock status: " + Mockito.mockingDetails(ecsClient).isMock());
//    System.out.println("localizationService mock status: " + Mockito.mockingDetails(localizationService).isMock());
//
//
//
//    EntityTypeFlatteningService entityTypeFlatteningService = new EntityTypeFlatteningService(entityTypeRepository, new ObjectMapper(), localizationService, executionContext, userTenantService);
//    CrossTenantQueryService crossTenantQueryService = new CrossTenantQueryService(ecsClient, executionContext, permissionsService, userTenantService);

//    var configuration = new DefaultConfiguration();
//    configuration.set(SQLDialect.H2);
//    configuration.set(dataSource);
//    DSLContext jooqContext = DSL.using(configuration);
    ecsClient = mock(SimpleHttpClient.class);
    entityTypeFlatteningService = mock(EntityTypeFlatteningService.class);
    crossTenantQueryService = mock(CrossTenantQueryService.class);
    queryRepository = new QueryRepository(context);
    queryResultsRepository = mock(QueryResultsRepository.class);
    this.idStreamer =
      new IdStreamer(
        context,
        entityTypeFlatteningService,
        crossTenantQueryService,
        mock(FolioExecutionContext.class),
        queryRepository,
        queryResultsRepository,
        executorService
      );
    System.out.println("YYZ Void setup");
  }

  @Test
  void shouldFetchIdStreamForFql() {
    String tenantId = "tenant_01";
    Fql fql = new Fql("", new EqualsCondition(new FqlField("column_01"), "value1"));
    List<String[]> expectedIds = new ArrayList<>();
    expectedIds.add(new String[]{CONTENT_IDS.get(0)});

    when(localizationService.localizeEntityType(any(EntityType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(crossTenantQueryService.getTenantsToQuery(any(EntityType.class))).thenReturn(List.of(tenantId));
    when(entityTypeFlatteningService.getFlattenedEntityType(any(UUID.class), eq(tenantId))).thenReturn(BASIC_ENTITY_TYPE);
    when(entityTypeFlatteningService.getJoinClause(any(EntityType.class), eq(tenantId))).thenReturn("source_1");

    idStreamer.streamIdsInBatch(
      BASIC_ENTITY_TYPE,
      true,
      fql,
      2,
      100,
      IN_PROGRESS_QUERY_ID
    );

    verify(queryResultsRepository, times(1)).saveQueryResults(eq(IN_PROGRESS_QUERY_ID), argThat(actual -> Arrays.deepEquals(expectedIds.toArray(), actual.toArray())));
  }

  @Test
  void shouldFetchIdStreamForFqlCrossTenant() {
    String tenantId = "tenant_01";
    Fql fql = new Fql("", new EqualsCondition(new FqlField("column_01"), "value1"));
    List<String[]> expectedIds = new ArrayList<>();
    // This dummy ECS query produces a union between 2 identical subqueries, so the id will show up twice in the results
    expectedIds.add(new String[]{CONTENT_IDS.get(0)});
    expectedIds.add(new String[]{CONTENT_IDS.get(0)});

    when(localizationService.localizeEntityType(any(EntityType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(crossTenantQueryService.getTenantsToQuery(any(EntityType.class))).thenReturn(List.of(tenantId, "tenant_02"));
    when(crossTenantQueryService.ecsEnabled()).thenReturn(true);
    when(entityTypeFlatteningService.getFlattenedEntityType(any(UUID.class), anyString())).thenReturn(ECS_ENTITY_TYPE);
    when(entityTypeFlatteningService.getJoinClause(any(EntityType.class), anyString())).thenReturn("source_1");

    idStreamer.streamIdsInBatch(
      ECS_ENTITY_TYPE,
      true,
      fql,
      2,
      100,
      IN_PROGRESS_QUERY_ID
    );
    verify(queryResultsRepository, times(1)).saveQueryResults(eq(IN_PROGRESS_QUERY_ID), argThat(actual -> Arrays.deepEquals(expectedIds.toArray(), actual.toArray())));
  }

//  @Test
//  void shouldUseUnionAllForCrossTenantQuery() {
//    String tenantId = "tenant_01";
//    when(executionContext.getTenantId()).thenReturn("tenant_01");
//    Fql fql = new Fql("", new EqualsCondition(new FqlField("field1"), "value1"));
//    List<List<String>> expectedIds = new ArrayList<>();
//    TEST_CONTENT_IDS.forEach(contentId -> expectedIds.add(List.of(contentId.toString())));
//    when(localizationService.localizeEntityType(any(EntityType.class))).thenAnswer(invocation -> invocation.getArgument(0));
//    when(executionContext.getTenantId()).thenReturn("tenant_01");
//    when(executionContext.getUserId()).thenReturn(UUID.randomUUID());
//    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(USER_TENANT_JSON);
//    when(ecsClient.get(eq("consortia/0e88ed41-eadb-44c3-a7a7-f6572bbe06fc/user-tenants"), anyMap())).thenReturn(USER_TENANT_JSON);
//    List<List<String>> actualIds = mockQueryRepositories();
//
//    idStreamer.streamIdsInBatch(
//      IdStreamerTestDataProvider.TEST_ENTITY_TYPE_DEFINITION,
//      true,
//      fql,
//      2,
//      100,
//      UUID.randomUUID()
//    );
//    assertEquals(expectedIds, actualIds);
//  }
//
  @Test
  void shouldHandleGroupByFields() {
    String tenantId = "tenant_01";
    Fql fql = new Fql("", new EqualsCondition(new FqlField("column_01"), "value1"));
    List<String[]> expectedIds = new ArrayList<>();
    expectedIds.add(new String[]{CONTENT_IDS.get(0)});

    when(crossTenantQueryService.getTenantsToQuery(any(EntityType.class))).thenReturn(List.of(tenantId));
    when(entityTypeFlatteningService.getFlattenedEntityType(any(UUID.class), eq(tenantId))).thenReturn(GROUP_BY_ENTITY_TYPE);
    when(entityTypeFlatteningService.getJoinClause(any(EntityType.class), eq(tenantId))).thenReturn("source_1");

    idStreamer.streamIdsInBatch(
      GROUP_BY_ENTITY_TYPE,
      true,
      fql,
      2,
      100,
      IN_PROGRESS_QUERY_ID
    );
    verify(queryResultsRepository, times(1)).saveQueryResults(eq(IN_PROGRESS_QUERY_ID), argThat(actual -> Arrays.deepEquals(expectedIds.toArray(), actual.toArray())));
  }

  @Test
  void shouldGetSortedIds() {
    int offset = 0;
    int limit = 100;
    String derivedTableName = "query_results";
    List<List<String>> expectedIds = new ArrayList<>();
    CONTENT_IDS.forEach(contentId -> expectedIds.add(List.of(contentId.toString())));
    List<List<String>> actualIds = idStreamer.getSortedIds(
      derivedTableName,
      offset,
      limit,
      IN_PROGRESS_QUERY_ID
    );
    assertEquals(expectedIds, actualIds);
  }

  //  @Test
//  void shouldHandleDataBatch() {
//    UUID queryId = UUID.randomUUID();
//    int maxQuerySize = 100;
//    List<String[]> resultIds = List.of(
//      new String[]{UUID.randomUUID().toString()},
//      new String[]{UUID.randomUUID().toString()}
//    );
//    IdsWithCancelCallback idsWithCancelCallback = new IdsWithCancelCallback(resultIds, () -> {
//    });
//    Query expectedQuery = new Query(queryId, UUID.randomUUID(), "", List.of(), UUID.randomUUID(),
//      OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null);
//    when(queryRepository.getQuery(queryId, true)).thenReturn(Optional.of(expectedQuery));
//    idStreamer.handleBatch(queryId, idsWithCancelCallback, maxQuerySize, new AtomicInteger(0));
//    verify(queryResultsRepository, times(1)).saveQueryResults(queryId, resultIds);
//  }
//
//  @Test
//  void shouldHandleDataBatchForCancelledQuery() {
//    AtomicBoolean streamClosed = new AtomicBoolean(false);
//    UUID queryId = UUID.randomUUID();
//    int maxQuerySize = 100;
//    List<String[]> resultIds = List.of(
//      new String[]{UUID.randomUUID().toString()},
//      new String[]{UUID.randomUUID().toString()}
//    );
//    IdsWithCancelCallback idsWithCancelCallback = new IdsWithCancelCallback(resultIds, () -> streamClosed.set(true));
//    assertFalse(streamClosed.get());
//    Query expectedQuery = new Query(queryId, UUID.randomUUID(), "", List.of(), UUID.randomUUID(),
//      OffsetDateTime.now(), null, QueryStatus.CANCELLED, null);
//    when(queryRepository.getQuery(queryId, true)).thenReturn(Optional.of(expectedQuery));
//    idStreamer.handleBatch(queryId, idsWithCancelCallback, maxQuerySize, new AtomicInteger(0));
//    assertTrue(streamClosed.get());
//  }
//
  @Test
  void shouldThrowExceptionWhenMaxQuerySizeExceeded() {
    int maxQuerySize = 1;
    List<String[]> resultIds = List.of(
      new String[]{UUID.randomUUID().toString()},
      new String[]{UUID.randomUUID().toString()}
    );
    IdsWithCancelCallback idsWithCancelCallback = new IdsWithCancelCallback(resultIds, () -> {
    });
    String expectedMessage = String.format("Query %s with size %d has exceeded the maximum size of %d.", IN_PROGRESS_QUERY_ID, 2, maxQuerySize);
    org.folio.fqm.domain.dto.Error expectedError = new Error().message(expectedMessage);
    var total = new AtomicInteger(0);
    MaxQuerySizeExceededException actualException = assertThrows(MaxQuerySizeExceededException.class, () -> idStreamer.handleBatch(IN_PROGRESS_QUERY_ID, idsWithCancelCallback, maxQuerySize, total));
    assertEquals(expectedMessage, actualException.getMessage());
    assertEquals(expectedError, actualException.getError());
  }
//
  @Test
  // Cancel query. Can Have another one for just monitoring if needed
  void shouldCancelQuery() {
    // Horrible mocks because our test DSL doesn't behave nicely in separate threads
    DSLContext mockJooqContext = mock(DSLContext.class);
    SelectSelectStep mockSelectStep = mock(SelectSelectStep.class);
    SelectJoinStep mockJoinStep = mock(SelectJoinStep.class);
    SelectConditionStep mockConditionStep = mock(SelectConditionStep.class);
    SelectConditionStep mockConditionStep2 = mock(SelectConditionStep.class);
    List<Integer> mockPids = List.of(123, 456);

    when(mockJooqContext.select(field("pid", Integer.class))).thenReturn(mockSelectStep);
    when(mockSelectStep.from(table("pg_stat_activity"))).thenReturn(mockJoinStep);
    when(mockJoinStep.where(field("state").eq("active"))).thenReturn(mockConditionStep);
    when(mockConditionStep.and(any(Condition.class))).thenReturn(mockConditionStep2);
    when(mockConditionStep2.fetchInto(Integer.class)).thenReturn(mockPids);

    QueryRepository mockQueryRepository = mock(QueryRepository.class);

    IdStreamer newIdStreamer = new IdStreamer(
      mockJooqContext,
      entityTypeFlatteningService,
      crossTenantQueryService,
      mock(FolioExecutionContext.class),
      mockQueryRepository,
      queryResultsRepository,
      executorService
    );

    List<List<String>> expectedIds = new ArrayList<>();
    when(localizationService.localizeEntityType(any(EntityType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(mockJooqContext.execute(anyString(), anyInt())).thenReturn(1);

    assertDoesNotThrow(() -> newIdStreamer.cancelQuery(CANCELLED_QUERY_ID));
    verify(mockJooqContext, times(1)).execute("SELECT pg_cancel_backend(?)", 123);
    verify(mockJooqContext, times(1)).execute("SELECT pg_cancel_backend(?)", 456);
  }

  @Test
  void shouldMonitorQueryCancellation() {
    QueryRepository mockQueryRepository = mock(QueryRepository.class);
    Query cancelledQuery = new Query(UUID.randomUUID(), UUID.randomUUID(), "", List.of(), UUID.randomUUID(), OffsetDateTime.now(), null, QueryStatus.CANCELLED, null);
    when(mockQueryRepository.getQuery(any(UUID.class), anyBoolean())).thenReturn(Optional.of(cancelledQuery));
    IdStreamer newIdStreamer = new IdStreamer(
      context,
      entityTypeFlatteningService,
      crossTenantQueryService,
      mock(FolioExecutionContext.class),
      mockQueryRepository,
      queryResultsRepository,
      executorService
    );
    when(localizationService.localizeEntityType(any(EntityType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    assertDoesNotThrow(() -> newIdStreamer.monitorQueryCancellation(CANCELLED_QUERY_ID));
  }

  @Test
  void shouldCatchExceptionWhenMonitoringQueryCancellation() {
    QueryRepository mockQueryRepository = mock(QueryRepository.class);
    when(mockQueryRepository.getQuery(any(UUID.class), anyBoolean())).thenThrow(QueryNotFoundException.class);
    IdStreamer newIdStreamer = new IdStreamer(
      context,
      entityTypeFlatteningService,
      crossTenantQueryService,
      mock(FolioExecutionContext.class),
      mockQueryRepository,
      queryResultsRepository,
      executorService
    );
    when(localizationService.localizeEntityType(any(EntityType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    assertDoesNotThrow(() -> newIdStreamer.monitorQueryCancellation(CANCELLED_QUERY_ID));
  }

  @Test
  void shouldCancelQueryWhenHandlingDataBatch() {
    IdsWithCancelCallback idsWithCancelCallback = mock(IdsWithCancelCallback.class);
    QueryRepository mockQueryRepository = mock(QueryRepository.class);
    Query cancelledQuery = new Query(UUID.randomUUID(), UUID.randomUUID(), "", List.of(), UUID.randomUUID(), OffsetDateTime.now(), null, QueryStatus.CANCELLED, null);
    when(mockQueryRepository.getQuery(any(UUID.class), anyBoolean())).thenReturn(Optional.of(cancelledQuery));
    IdStreamer newIdStreamer = new IdStreamer(
      context,
      entityTypeFlatteningService,
      crossTenantQueryService,
      mock(FolioExecutionContext.class),
      mockQueryRepository,
      queryResultsRepository,
      executorService
    );
    when(localizationService.localizeEntityType(any(EntityType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    assertDoesNotThrow(() -> newIdStreamer.handleBatch(CANCELLED_QUERY_ID, idsWithCancelCallback, 100, new AtomicInteger(0)));
    verify(idsWithCancelCallback, times(1)).cancel();
  }

//
//  @Test
//  void shouldCancelQuery() {
//    fail();
//    String tenantId = "tenant_01";
//    Fql fql = new Fql("", new EqualsCondition(new FqlField("field1"), "value1"));
//    Query cancelledQuery = new Query(UUID.randomUUID(), UUID.randomUUID(), "", List.of(), UUID.randomUUID(), OffsetDateTime.now(), null, QueryStatus.CANCELLED, null);
//    List<List<String>> expectedIds = new ArrayList<>();
//    TEST_CONTENT_IDS.forEach(contentId -> expectedIds.add(List.of(contentId.toString())));
//    when(localizationService.localizeEntityType(any(EntityType.class))).thenAnswer(invocation -> invocation.getArgument(0));
//    when(executionContext.getTenantId()).thenReturn(tenantId);
//    when(userTenantService.getUserTenantsResponse(tenantId)).thenReturn(NON_ECS_USER_TENANT_JSON);
//    when(queryRepository.getQuery(any(), eq(false))).thenReturn(Optional.of(cancelledQuery));
//    List<List<String>> actualIds = mockQueryRepositories();
//
//    idStreamer.streamIdsInBatch(
//      IdStreamerTestDataProvider.TEST_ENTITY_TYPE_DEFINITION,
//      true,
//      fql,
//      2,
//      100,
//      UUID.randomUUID()
//    );
//    assertEquals(expectedIds, actualIds);
//  }
//
//  // Suppress the unchecked cast warning on the Class<T> cast below. We know the parameter type is List<String[]>, but can't
//  // easily create a Class object for it.
//  @SuppressWarnings("unchecked")
//  private List<List<String>> mockQueryRepositories() {
//    List<List<String>> actualIds = new ArrayList<>();
////    when(queryRepository.getQuery(any(UUID.class), anyBoolean())).thenReturn(Optional.of(mock(Query.class)));
////    doAnswer(invocation -> {
////      List<String[]> ids = invocation.getArgument(1, List.class);
////      ids.forEach(idSet -> actualIds.add(Arrays.asList(idSet)));
////      return null;
////    }).when(queryResultsRepository).saveQueryResults(any(UUID.class), any(List.class));
////    return actualIds;
////    System.out.println("Mock query repositories");
////    return null;
//    return actualIds;
//  }
}
