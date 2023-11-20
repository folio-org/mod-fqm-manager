package org.folio.fqm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.exception.QueryNotFoundException;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.repository.QueryRepository;
import org.folio.fqm.repository.QueryResultsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Log4j2
public class QueryExecutionCallbacks {

  private final QueryRepository queryRepository;
  private final QueryResultsRepository queryResultsRepository;

  @Transactional
  public void handleDataBatch(UUID queryId, IdsWithCancelCallback idsWithCancelCallback) {
    Query query = queryRepository.getQuery(queryId, true)
      .orElseThrow(() -> new QueryNotFoundException(queryId));
    if (query.status() == QueryStatus.CANCELLED) {
      log.info("Query {} has been cancelled, closing id stream", queryId);
      idsWithCancelCallback.cancel();
      return;
    }
    List<UUID> resultIds = idsWithCancelCallback.ids();
    log.info("Saving query results for queryId: {}. Count: {}", queryId, resultIds.size());
    queryResultsRepository.saveQueryResults(queryId, resultIds);
  }

  @Transactional
  public void handleSuccess(Query query, int totalCount) {
    Query savedQuery = queryRepository.getQuery(query.queryId(), true)
      .orElseThrow(() -> new QueryNotFoundException(query.queryId()));
    if (savedQuery.status() == QueryStatus.CANCELLED) {
      log.info("Query {} has been cancelled, therefore not updating", query.queryId());
      return;
    }
    log.info("Query Execution completed for queryId {}, entityTypeId {}. Total count: {}", query.queryId(),
      query.entityTypeId(), totalCount);
    queryRepository.updateQuery(query.queryId(), QueryStatus.SUCCESS, OffsetDateTime.now(), null);
  }

  @Transactional
  public void handleFailure(Query query, Throwable throwable) {
    log.error("Query Execution failed for queryId {}, entityTypeId {}. Failure reason: {}", query.queryId(),
      query.entityTypeId(), throwable.getMessage());
    queryRepository.updateQuery(query.queryId(), QueryStatus.FAILED, OffsetDateTime.now(), throwable.getMessage());
  }
}
