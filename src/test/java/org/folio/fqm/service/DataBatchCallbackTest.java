package org.folio.fqm.service;

import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.exception.MaxQuerySizeExceededException;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.repository.QueryRepository;
import org.folio.fqm.repository.QueryResultsRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class DataBatchCallbackTest {

  @InjectMocks
  private DataBatchCallback dataBatchCallback;
  @Mock
  private QueryRepository queryRepository;
  @Mock
  private QueryResultsRepository queryResultsRepository;

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
      OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null, false);
    when(queryRepository.getQuery(queryId, true)).thenReturn(Optional.of(expectedQuery));
    dataBatchCallback.accept(queryId, idsWithCancelCallback, maxQuerySize);
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
      OffsetDateTime.now(), null, QueryStatus.CANCELLED, null, false);
    when(queryRepository.getQuery(queryId, true)).thenReturn(Optional.of(expectedQuery));
    dataBatchCallback.accept(queryId, idsWithCancelCallback, maxQuerySize);
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
      OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null, false);
    String expectedMessage = String.format("Query %s with size %d has exceeded the maximum size of %d.", queryId, 2, maxQuerySize);
    Error expectedError = new Error().message(expectedMessage);
    when(queryRepository.getQuery(queryId, true)).thenReturn(Optional.of(expectedQuery));
    MaxQuerySizeExceededException actualException = assertThrows(MaxQuerySizeExceededException.class, () -> dataBatchCallback.accept(queryId, idsWithCancelCallback, maxQuerySize));
    assertEquals(expectedMessage, actualException.getMessage());
    assertEquals(expectedError, actualException.getError());
  }
}
