package org.folio.fqm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.repository.IdStreamer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Sorts the results of a query according to the default sort criteria of the entity type, and then
 * streams back to calling application.
 */
@Log4j2
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class QueryResultsSorterService {

  private final IdStreamer idStreamer;

  /**
   * Streams the result IDs of provided queryId, after sorting the results based on default sort criteria of entity type.
   *
   * @param queryId        Query ID
   * @param batchSize      Count of IDs to be returned in a single batch
   * @param idsConsumer    This consumer will receive the IDs in batches, and each batch will also include a
   *                       callback for canceling the query. The consuming application can utilize this callback
   *                       to cancel the query.
   * @param successHandler Once the query execution is finished, this consumer is triggered. It will be provided
   *                       with the overall count of records that corresponded to the query.
   * @param errorHandler   If an error occurs during query execution, this consumer will be called and given the
   *                       exception responsible for the failure
   */
  public void streamSortedIds(UUID queryId,
                              int batchSize,
                              Consumer<IdsWithCancelCallback> idsConsumer,
                              IntConsumer successHandler,
                              Consumer<Throwable> errorHandler) {
    try {
      log.info("Streaming sorted result ids for queryId: {}", queryId);
      int idsCount = idStreamer.streamIdsInBatch(
        queryId,
        true,
        batchSize,
        idsConsumer
      );
      successHandler.accept(idsCount);
    } catch (Exception exception) {
      errorHandler.accept(exception);
    }
  }

  public List<List<String>> getSortedIds(UUID queryId,
                                         int offset, int limit) {
    log.debug("Getting sorted ids for query {}, offset {}, limit {}", queryId, offset, limit);
    // Sort ids based on the sort criteria defined in the entity type definition
    // Note: This does not sort right now, due to performance concerns. Instead, it just pulls straight from query_results
    return idStreamer.getSortedIds("query_results", offset, limit, queryId);
  }
}
