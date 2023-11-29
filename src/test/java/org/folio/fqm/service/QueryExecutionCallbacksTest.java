package org.folio.fqm.service;

import org.folio.fql.deserializer.FqlParsingException;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.repository.QueryRepository;
import org.folio.fqm.repository.QueryResultsRepository;
import org.folio.fqm.testutil.TestDataFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class QueryExecutionCallbacksTest {

  @InjectMocks
  private QueryExecutionCallbacks callbacks;
  @Mock
  private QueryRepository queryRepository;
  @Mock
  private QueryResultsRepository queryResultsRepository;

  @Test
  void shouldHandleDataBatch() {
    UUID queryId = UUID.randomUUID();
    List<UUID> resultIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    IdsWithCancelCallback idsWithCancelCallback = new IdsWithCancelCallback(resultIds, () -> {});
    Query expectedQuery = new Query(queryId, UUID.randomUUID(), "", List.of(), UUID.randomUUID(),
      OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null);
    when(queryRepository.getQuery(queryId, true)).thenReturn(Optional.of(expectedQuery));
    callbacks.handleDataBatch(queryId, idsWithCancelCallback);
    verify(queryResultsRepository, times(1)).saveQueryResults(queryId, resultIds);
  }

  @Test
  void shouldHandleDataBatchForCancelledQuery() {
    AtomicBoolean streamClosed = new AtomicBoolean(false);
    UUID queryId = UUID.randomUUID();
    List<UUID> resultIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    IdsWithCancelCallback idsWithCancelCallback = new IdsWithCancelCallback(resultIds, () -> streamClosed.set(true));
    assertFalse(streamClosed.get());
    Query expectedQuery = new Query(queryId, UUID.randomUUID(), "", List.of(), UUID.randomUUID(),
      OffsetDateTime.now(), null, QueryStatus.CANCELLED, null);
    when(queryRepository.getQuery(queryId, true)).thenReturn(Optional.of(expectedQuery));
    callbacks.handleDataBatch(queryId, idsWithCancelCallback);
    assertTrue(streamClosed.get());
  }

  @Test
  void shouldHandleSuccess() {
    Query query = TestDataFixture.getMockQuery();
    int totalCount = 0;
    when(queryRepository.getQuery(query.queryId(), true)).thenReturn(Optional.of(query));
    callbacks.handleSuccess(query, totalCount);
    verify(queryRepository, times(1)).updateQuery(eq(query.queryId()), eq(QueryStatus.SUCCESS), any(), eq(null));
  }

  @Test
  void successHandlerShouldHandleCancelledQuery() {
    Query query = new Query(UUID.randomUUID(), UUID.randomUUID(), "", List.of(), UUID.randomUUID(),
      OffsetDateTime.now(), null, QueryStatus.CANCELLED, null);
    int totalCount = 0;
    when(queryRepository.getQuery(query.queryId(), true)).thenReturn(Optional.of(query));
    callbacks.handleSuccess(query, totalCount);
    verify(queryRepository, times(0)).updateQuery(query.queryId(), query.status(), query.endDate(), query.failureReason());
  }

  @Test
  void shouldHandleFailure() {
    Query query = TestDataFixture.getMockQuery();
    Throwable throwable = new FqlParsingException("field1", "Field not present");
    callbacks.handleFailure(query, throwable);
    verify(queryRepository, times(1)).updateQuery(eq(query.queryId()), eq(QueryStatus.FAILED), any(), any());
  }
}
