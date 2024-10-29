package org.folio.fqm.service;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.domain.Query;
import org.folio.fqm.model.FqlQueryWithContext;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class QueryExecutionService {

  private static final int DEFAULT_BATCH_SIZE = 10000;
  private final QueryProcessorService queryProcessorService;
  private final FolioExecutionContext folioExecutionContext;
  private final QueryExecutionCallbacks callbacks;
  private final Supplier<DataBatchCallback> dataBatchCallbackSupplier;

  @Async
  // Long-running method. Running this method within a transaction boundary will hog db connection for
  // long time. Hence, do not run this method in a transaction.
  public void executeQueryAsync(Query query, EntityType entityType, int maxQuerySize) {
    try {
      log.info("Executing query {}", query.queryId());
      FqlQueryWithContext fqlQueryWithContext = new FqlQueryWithContext(
        folioExecutionContext.getTenantId(),
        entityType,
        query.fqlQuery(),
        false
      );
      DataBatchCallback dataBatchCallback = dataBatchCallbackSupplier.get();
      queryProcessorService.getIdsInBatch(
        fqlQueryWithContext,
        DEFAULT_BATCH_SIZE,
        resultIds -> dataBatchCallback.accept(query.queryId(), resultIds, maxQuerySize),
        totalCount -> callbacks.handleSuccess(query, totalCount),
        throwable -> callbacks.handleFailure(query, throwable)
      );
    } catch (Exception exception) {
      callbacks.handleFailure(query, exception);
    }
  }
}
