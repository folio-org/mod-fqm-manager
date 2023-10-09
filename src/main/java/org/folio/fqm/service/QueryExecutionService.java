package org.folio.fqm.service;

import org.folio.fqm.lib.service.QueryProcessorService;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.domain.Query;
import org.folio.fqm.lib.model.FqlQueryWithContext;
import org.folio.spring.FolioExecutionContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Log4j2
@Service
@RequiredArgsConstructor
public class QueryExecutionService {

  private static final int DEFAULT_BATCH_SIZE = 100;
  private final QueryProcessorService queryProcessorService;
  private final FolioExecutionContext folioExecutionContext;
  private final QueryExecutionCallbacks callbacks;
  private final Supplier<DataBatchCallback> dataBatchCallbackSupplier;

  @Async
  // Long-running method. Running this method within a transaction boundary will hog db connection for
  // long time. Hence, do not run this method in a transaction.
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void executeQueryAsync(Query query, boolean sortResults) {
    try {
      log.info("Executing query {}", query.queryId());
      DataBatchCallback dataBatchCallback = dataBatchCallbackSupplier.get();
      var fqlQueryWithContext = new FqlQueryWithContext(folioExecutionContext.getTenantId(), query.entityTypeId(), query.fqlQuery(), sortResults);
      queryProcessorService.getIdsInBatch(
        fqlQueryWithContext,
        DEFAULT_BATCH_SIZE,
        resultIds -> dataBatchCallback.handleDataBatch(query.queryId(), resultIds),
        totalCount -> callbacks.handleSuccess(query, totalCount),
        throwable -> callbacks.handleFailure(query, throwable)
      );

    } catch (Exception exception) {
      callbacks.handleFailure(query, exception);
    }
  }
}
