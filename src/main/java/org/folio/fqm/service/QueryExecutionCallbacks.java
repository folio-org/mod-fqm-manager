package org.folio.fqm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.exception.QueryNotFoundException;
import org.folio.fqm.repository.QueryRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Log4j2
public class QueryExecutionCallbacks {

  private final QueryRepository queryRepository;

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

  public void handleFailure(Query query, Throwable throwable) {
    log.error("Query Execution failed for queryId {}, entityTypeId {}. Failure reason: {}", query.queryId(),
      query.entityTypeId(), throwable.getMessage());
    queryRepository.updateQuery(query.queryId(), QueryStatus.FAILED, OffsetDateTime.now(), throwable.getMessage());
  }
}
