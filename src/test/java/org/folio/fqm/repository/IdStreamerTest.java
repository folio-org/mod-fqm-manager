package org.folio.fqm.repository;

import static org.folio.fqm.utils.flattening.FromClauseUtils.getFromClause;
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
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.exception.MaxQuerySizeExceededException;
import org.folio.fqm.exception.QueryNotFoundException;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.service.CrossTenantQueryService;
import org.folio.fqm.service.EntityTypeFlatteningService;
import org.folio.fqm.utils.flattening.FromClauseUtils;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.RangedUUIDType;
import org.folio.spring.FolioExecutionContext;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@RunWith(MockitoJUnitRunner.class)
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
    .filterConditions(List.of("false = false"))
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

  private IdStreamer idStreamer;
  private EntityTypeFlatteningService entityTypeFlatteningService;
  private CrossTenantQueryService crossTenantQueryService;
  private QueryResultsRepository queryResultsRepository;

  @Autowired
  private ScheduledExecutorService executorService;
  @Autowired
  @Qualifier("readerJooqContext")
  private DSLContext context;

  @BeforeEach
  void setup() {
    QueryRepository queryRepository = new QueryRepository(context, context);
    entityTypeFlatteningService = mock(EntityTypeFlatteningService.class);
    crossTenantQueryService = mock(CrossTenantQueryService.class);
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
  }

  @Test
  void shouldFetchIdStreamForFql() {
    String tenantId = "tenant_01";
    Fql fql = new Fql("", new EqualsCondition(new FqlField("column_01"), "value1"));
    List<String[]> expectedIds = new ArrayList<>();
    expectedIds.add(new String[]{CONTENT_IDS.get(0)});

    when(crossTenantQueryService.getTenantsToQuery(any(EntityType.class))).thenReturn(List.of(tenantId));
    when(entityTypeFlatteningService.getFlattenedEntityType(any(UUID.class), eq(tenantId), anyBoolean())).thenReturn(BASIC_ENTITY_TYPE);
    try (MockedStatic<FromClauseUtils> fromClauseUtils = mockStatic(FromClauseUtils.class)) {
      fromClauseUtils.when(() -> getFromClause(any(EntityType.class), eq(tenantId))).thenReturn("source_1");

      idStreamer.streamIdsInBatch(
        BASIC_ENTITY_TYPE,
        true,
        fql,
        2,
        100,
        IN_PROGRESS_QUERY_ID
      );
    }

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

    when(crossTenantQueryService.getTenantsToQuery(any(EntityType.class))).thenReturn(List.of(tenantId, "tenant_02"));
    when(crossTenantQueryService.ecsEnabled()).thenReturn(true);
    when(entityTypeFlatteningService.getFlattenedEntityType(any(UUID.class), anyString(), anyBoolean())).thenReturn(ECS_ENTITY_TYPE);
    try (MockedStatic<FromClauseUtils> fromClauseUtils = mockStatic(FromClauseUtils.class)) {
      fromClauseUtils.when(() -> getFromClause(any(EntityType.class), anyString())).thenReturn("source_1");

      idStreamer.streamIdsInBatch(
        ECS_ENTITY_TYPE,
        true,
        fql,
        2,
        100,
        IN_PROGRESS_QUERY_ID
      );
    }

    verify(queryResultsRepository, times(1)).saveQueryResults(eq(IN_PROGRESS_QUERY_ID), argThat(actual -> Arrays.deepEquals(expectedIds.toArray(), actual.toArray())));
  }

  @Test
  void shouldHandleGroupByFields() {
    String tenantId = "tenant_01";
    Fql fql = new Fql("", new EqualsCondition(new FqlField("column_01"), "value1"));
    List<String[]> expectedIds = new ArrayList<>();
    expectedIds.add(new String[]{CONTENT_IDS.get(0)});

    when(crossTenantQueryService.getTenantsToQuery(any(EntityType.class))).thenReturn(List.of(tenantId));
    when(entityTypeFlatteningService.getFlattenedEntityType(any(UUID.class), eq(tenantId), anyBoolean())).thenReturn(GROUP_BY_ENTITY_TYPE);
    try (MockedStatic<FromClauseUtils> fromClauseUtils = mockStatic(FromClauseUtils.class)) {
      fromClauseUtils.when(() -> getFromClause(any(EntityType.class), eq(tenantId))).thenReturn("source_1");

      idStreamer.streamIdsInBatch(
        GROUP_BY_ENTITY_TYPE,
        true,
        fql,
        2,
        100,
        IN_PROGRESS_QUERY_ID
      );
    }

    verify(queryResultsRepository, times(1)).saveQueryResults(eq(IN_PROGRESS_QUERY_ID), argThat(actual -> Arrays.deepEquals(expectedIds.toArray(), actual.toArray())));
  }

  @Test
  void shouldGetSortedIds() {
    int offset = 0;
    int limit = 100;
    String derivedTableName = "query_results";
    List<List<String>> expectedIds = new ArrayList<>();
    CONTENT_IDS.forEach(contentId -> expectedIds.add(List.of(contentId)));
    List<List<String>> actualIds = idStreamer.getSortedIds(
      derivedTableName,
      offset,
      limit,
      IN_PROGRESS_QUERY_ID
    );
    assertEquals(expectedIds, actualIds);
  }

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

  @Test
  void shouldCancelQuery() {
    // This test uses a mocked DSLContext because our test DSLContext doesn't behave nicely in separate threads
    DSLContext mockJooqContext = mock(DSLContext.class);
    QueryRepository mockQueryRepository = mock(QueryRepository.class);
    when(mockQueryRepository.getSelectQueryPids(any())).thenReturn(List.of(123, 456));

    IdStreamer newIdStreamer = new IdStreamer(
      mockJooqContext,
      entityTypeFlatteningService,
      crossTenantQueryService,
      mock(FolioExecutionContext.class),
      mockQueryRepository,
      queryResultsRepository,
      executorService
    );

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

    assertDoesNotThrow(() -> newIdStreamer.handleBatch(CANCELLED_QUERY_ID, idsWithCancelCallback, 100, new AtomicInteger(0)));
    verify(idsWithCancelCallback, times(1)).cancel();
  }
}
