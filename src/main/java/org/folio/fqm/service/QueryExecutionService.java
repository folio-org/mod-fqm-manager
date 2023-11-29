package org.folio.fqm.service;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.domain.Query;
import org.folio.fqm.model.FqlQueryWithContext;
import org.folio.spring.FolioExecutionContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class QueryExecutionService {

  private static final int DEFAULT_BATCH_SIZE = 100;
  private final QueryProcessorService queryProcessorService;
  private final FolioExecutionContext folioExecutionContext;
  private final QueryExecutionCallbacks callbacks;

  @Async
  // Long-running method. Running this method within a transaction boundary will hog db connection for
  // long time. Hence, do not run this method in a transaction.
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void executeQueryAsync(Query query) {
    try {
      log.info("Executing query {}", query.queryId());
      var fqlQueryWithContext = new FqlQueryWithContext(folioExecutionContext.getTenantId(), query.entityTypeId(), query.fqlQuery(), false);
      queryProcessorService.getIdsInBatch(
        fqlQueryWithContext,
        DEFAULT_BATCH_SIZE,
        resultIds -> callbacks.handleDataBatch(query.queryId(), resultIds),
        totalCount -> callbacks.handleSuccess(query, totalCount),
        throwable -> callbacks.handleFailure(query, throwable)
      );

    } catch (Exception exception) {
      callbacks.handleFailure(query, exception);
    }
  }
}
