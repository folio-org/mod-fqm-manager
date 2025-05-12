package org.folio.fqm.service;

import org.folio.fql.deserializer.FqlParsingException;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.exception.MaxQuerySizeExceededException;
import org.folio.fqm.exception.QueryNotFoundException;
import org.folio.fqm.model.FqlQueryWithContext;
import org.folio.fqm.repository.QueryRepository;
import org.folio.fqm.testutil.TestDataFixture;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class QueryExecutionServiceTest {

  @InjectMocks
  private QueryExecutionService queryExecutionService;
  @Mock
  private QueryProcessorService queryProcessorService;
  @Mock
  private FolioExecutionContext executionContext;
  @Mock
  private QueryRepository queryRepository;


  @Test
  void testQueryExecutionService() {
    String tenantId = "Tenant_01";
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType();
    UUID createdById = UUID.randomUUID();
    String fqlQuery = "{“item_status“: {“$in“: [\"missing\", \"lost\"]}}";
    List<String> fields = List.of();
    Query query = Query.newQuery(entityTypeId, fqlQuery, fields, createdById);
    int maxSize = 100;
    when(executionContext.getTenantId()).thenReturn(tenantId);

    queryExecutionService.executeQueryAsync(query, entityType, maxSize);
    FqlQueryWithContext fqlQueryWithContext = new FqlQueryWithContext(tenantId, entityType, fqlQuery, false);
    verify(queryProcessorService, times(1)).getIdsInBatch(
      eq(fqlQueryWithContext),
      anyInt(),
      anyInt(),
      any());
  }

  @Test
  void shouldHandleFailure() {
    Query query = TestDataFixture.getMockQuery();
    Throwable throwable = new FqlParsingException("field1", "Field not present");
    queryExecutionService.handleFailure(query, throwable);
    verify(queryRepository, times(1)).updateQuery(eq(query.queryId()), eq(QueryStatus.FAILED), any(), any());
  }

  @Test
  void shouldHandleQueryCancellation() {
    String tenantId = "tenant_01";
    EntityType entityType = new EntityType();
    String fqlQuery = "{“item_status“: {“$in“: [\"missing\", \"lost\"]}}";
    List<String> fields = List.of();
    Query query = new Query(UUID.randomUUID(), UUID.randomUUID(), fqlQuery, fields, UUID.randomUUID(), OffsetDateTime.now(), null, QueryStatus.CANCELLED, null);

    int maxSize = 100;
    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(queryRepository.getQuery(query.queryId(), false)).thenReturn(Optional.of(query));

    doThrow(RuntimeException.class).when(queryProcessorService).getIdsInBatch(
      any(),
      anyInt(),
      anyInt(),
      any());

    assertDoesNotThrow(() -> queryExecutionService.executeQueryAsync(query, entityType, maxSize));
    verify(queryRepository, times(0)).updateQuery(any(), any(), any(), any());
  }

  @Test
  void shouldHandleMaxQuerySizeExceeded() {
    String tenantId = "tenant_01";
    EntityType entityType = new EntityType();
    String fqlQuery = "{“item_status“: {“$in“: [\"missing\", \"lost\"]}}";
    List<String> fields = List.of();
    Query query = new Query(UUID.randomUUID(), UUID.randomUUID(), fqlQuery, fields, UUID.randomUUID(), OffsetDateTime.now(), null, QueryStatus.CANCELLED, null);

    int maxSize = 100;
    when(executionContext.getTenantId()).thenReturn(tenantId);

    doThrow(MaxQuerySizeExceededException.class).when(queryProcessorService).getIdsInBatch(
      any(),
      anyInt(),
      anyInt(),
      any());

    assertDoesNotThrow(() -> queryExecutionService.executeQueryAsync(query, entityType, maxSize));
    verify(queryRepository, times(1)).updateQuery(eq(query.queryId()), eq(QueryStatus.MAX_SIZE_EXCEEDED), any(), eq(null));
  }

  @Test
  void shouldCatchExceptionDuringExecution() {
    String tenantId = "tenant_01";
    EntityType entityType = new EntityType();
    String fqlQuery = "{“item_status“: {“$in“: [\"missing\", \"lost\"]}}";
    List<String> fields = List.of();
    Query query = new Query(UUID.randomUUID(), UUID.randomUUID(), fqlQuery, fields, UUID.randomUUID(), OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null);

    int maxSize = 100;
    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(queryRepository.getQuery(query.queryId(), false)).thenReturn(Optional.of(query));

    doThrow(RuntimeException.class).when(queryProcessorService).getIdsInBatch(
      any(),
      anyInt(),
      anyInt(),
      any());

    assertDoesNotThrow(() -> queryExecutionService.executeQueryAsync(query, entityType, maxSize));
    verify(queryRepository, times(1)).updateQuery(eq(query.queryId()), eq(QueryStatus.FAILED), any(), any());
  }

  @Test
  void shouldHandleQueryNotFoundException() {
    String tenantId = "tenant_01";
    EntityType entityType = new EntityType();
    String fqlQuery = "{“item_status“: {“$in“: [\"missing\", \"lost\"]}}";
    Query query = new Query(UUID.randomUUID(), UUID.randomUUID(), fqlQuery, List.of(), UUID.randomUUID(), OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null);

    int maxSize = 100;
    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(queryRepository.getQuery(query.queryId(), false)).thenThrow(QueryNotFoundException.class);

    doThrow(RuntimeException.class).when(queryProcessorService).getIdsInBatch(
      any(),
      anyInt(),
      anyInt(),
      any());

    assertDoesNotThrow(() -> queryExecutionService.executeQueryAsync(query, entityType, maxSize));
    verify(queryRepository, times(1)).updateQuery(eq(query.queryId()), eq(QueryStatus.FAILED), any(), any());
  }
}
