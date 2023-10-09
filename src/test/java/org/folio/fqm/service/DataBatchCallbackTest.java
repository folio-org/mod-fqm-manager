package org.folio.fqm.service;

import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.lib.model.IdsWithCancelCallback;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    List<UUID> resultIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    int sortSequence = 0;
    IdsWithCancelCallback idsWithCancelCallback = new IdsWithCancelCallback(resultIds, () -> {});
    Query expectedQuery = new Query(queryId, UUID.randomUUID(), "", List.of(), UUID.randomUUID(),
      OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null);
    when(queryRepository.getQuery(queryId, true)).thenReturn(Optional.of(expectedQuery));
    dataBatchCallback.handleDataBatch(queryId, idsWithCancelCallback);
    verify(queryResultsRepository, times(1)).saveQueryResults(queryId, resultIds, sortSequence);
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
    dataBatchCallback.handleDataBatch(queryId, idsWithCancelCallback);
    assertTrue(streamClosed.get());
  }
}
