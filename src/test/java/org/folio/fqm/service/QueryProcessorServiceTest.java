package org.folio.fqm.service;

import org.folio.fql.model.EqualsCondition;
import org.folio.fql.model.Fql;
import org.folio.fql.model.field.FqlField;
import org.folio.fql.service.FqlService;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.model.FqlQueryWithContext;
import org.folio.fqm.repository.IdStreamer;
import org.folio.fqm.repository.QueryRepository;
import org.folio.fqm.repository.ResultSetRepository;
import org.folio.fqm.testutil.TestDataFixture;
import org.folio.querytool.domain.dto.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryProcessorServiceTest {

  public static final int DEFAULT_BATCH_SIZE = 1000;
  private QueryProcessorService service;
  private ResultSetRepository resultSetRepository;
  private IdStreamer idStreamer;
  private FqlService fqlService;
  private CrossTenantQueryService crossTenantQueryService;
  private QueryRepository queryRepository;

  @BeforeEach
  public void setup() {
    resultSetRepository = mock(ResultSetRepository.class);
    idStreamer = mock(IdStreamer.class);
    fqlService = mock(FqlService.class);
    crossTenantQueryService = mock(CrossTenantQueryService.class);
    queryRepository = mock(QueryRepository.class);
    this.service = new QueryProcessorService(resultSetRepository, idStreamer, fqlService, crossTenantQueryService, queryRepository);
  }

  @Test
  void shouldGetIdsInBatch() {
    Fql fql = new Fql("", new EqualsCondition(new FqlField("status"), "missing"));
    String tenantId = "tenant_01";
    String fqlCriteria = "{\"status\": {\"$eq\": \"missing\"}}";
    EntityType entityType = new EntityType();

    when(fqlService.getFql(fqlCriteria)).thenReturn(fql);
    FqlQueryWithContext fqlQueryWithContext = new FqlQueryWithContext(tenantId, entityType, fqlCriteria, true);
    Query query = TestDataFixture.getMockQuery(QueryStatus.IN_PROGRESS);
    when(queryRepository.getQuery(any(UUID.class), anyBoolean())).thenReturn(Optional.of(query));
    service.getIdsInBatch(fqlQueryWithContext,
      DEFAULT_BATCH_SIZE,
      100,
      query
    );

    verify(idStreamer, times(1))
      .streamIdsInBatch(
        fqlQueryWithContext.entityType(),
        fqlQueryWithContext.sortResults(),
        fql,
        DEFAULT_BATCH_SIZE,
        100,
        query.queryId()
      );
  }

  @Test
  void shouldRunSynchronousQueryAndReturnPaginatedResults() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType(entityTypeId.toString(), "test_ET", true);
    String fqlQuery = """
      {"field1": {"eq": "value1" }}
      """;
    List<String> tenantIds = List.of("tenant_01");
    int limit = 100;
    Fql expectedFql = new Fql("", new EqualsCondition(new FqlField("status"), "value1"));
    List<String> fields = List.of("field1", "field2");
    List<Map<String, Object>> expectedContent = List.of(
      Map.of("field1", "value1", "field2", "value2"),
      Map.of("field1", "value1", "field2", "value4")
    );
    when(fqlService.getFql(fqlQuery)).thenReturn(expectedFql);
    when(crossTenantQueryService.getTenantsToQuery(entityType)).thenReturn(tenantIds);
    when(resultSetRepository.getResultSetSync(entityTypeId, expectedFql, fields, limit, tenantIds, false)).thenReturn(expectedContent);
    List<Map<String, Object>> actualContent = service.processQuery(entityType, fqlQuery, fields, limit);
    assertEquals(expectedContent, actualContent);
  }

  @Test
  void shouldHandleSuccess() {
    Query query = TestDataFixture.getMockQuery();
    when(queryRepository.getQuery(query.queryId(), false)).thenReturn(Optional.of(query));
    service.handleSuccess(query);
    verify(queryRepository, times(1)).updateQuery(eq(query.queryId()), eq(QueryStatus.SUCCESS), any(), eq(null));
  }

  @Test
  void successHandlerShouldHandleCancelledQuery() {
    Query query = new Query(UUID.randomUUID(), UUID.randomUUID(), "", List.of(), UUID.randomUUID(),
      OffsetDateTime.now(), null, QueryStatus.CANCELLED, null);
    when(queryRepository.getQuery(query.queryId(), false)).thenReturn(Optional.of(query));
    service.handleSuccess(query);
    verify(queryRepository, times(0)).updateQuery(query.queryId(), query.status(), query.endDate(), query.failureReason());
  }
}
