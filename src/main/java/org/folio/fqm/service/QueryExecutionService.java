package org.folio.fqm.service;

import java.time.OffsetDateTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.exception.MaxQuerySizeExceededException;
import org.folio.fqm.exception.QueryNotFoundException;
import org.folio.fqm.model.FqlQueryWithContext;
import org.folio.fqm.repository.QueryRepository;
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
  private final QueryRepository queryRepository;


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
      queryProcessorService.getIdsInBatch(
        fqlQueryWithContext,
        DEFAULT_BATCH_SIZE,
        maxQuerySize,
        query
      );
    } catch (MaxQuerySizeExceededException exception) {
      queryRepository.updateQuery(query.queryId(), QueryStatus.MAX_SIZE_EXCEEDED, OffsetDateTime.now(), null);
    }
    catch (Exception exception) {
      try {
        // Check query status. Only mark query as failed if query is not already cancelled
        QueryStatus queryStatus = queryRepository
          .getQuery(query.queryId(), false)
          .orElseThrow(() -> new QueryNotFoundException(query.queryId()))
          .status();
        if (queryStatus != QueryStatus.CANCELLED) {
          handleFailure(query, exception);
        }
      } catch (QueryNotFoundException e) {
        handleFailure(query, e);
      }
    }
  }

  public void handleFailure(Query query, Throwable throwable) {
    log.error("Query Execution failed for queryId {}, entityTypeId {}. Failure reason: {}", query.queryId(),
      query.entityTypeId(), throwable.getMessage());
    queryRepository.updateQuery(query.queryId(), QueryStatus.FAILED, OffsetDateTime.now(), throwable.getMessage());
  }

}
