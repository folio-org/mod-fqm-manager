package org.folio.fqm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.function.TriConsumer;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.exception.MaxQuerySizeExceededException;
import org.folio.fqm.exception.QueryNotFoundException;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.repository.QueryRepository;
import org.folio.fqm.repository.QueryResultsRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Log4j2
public class DataBatchCallback implements TriConsumer<UUID, IdsWithCancelCallback, Integer> {

  private final QueryRepository queryRepository;
  private final QueryResultsRepository queryResultsRepository;
  private int sortSequence = 0;

  @Override
  public void accept(UUID queryId, IdsWithCancelCallback idsWithCancelCallback, Integer maxQuerySize) {
    Query query = queryRepository.getQuery(queryId, true)
      .orElseThrow(() -> new QueryNotFoundException(queryId));
    if (query.status() == QueryStatus.CANCELLED) {
      log.info("Query {} has been cancelled, closing id stream", queryId);
      idsWithCancelCallback.cancel();
      return;
    }
    List<String[]> resultIds = idsWithCancelCallback.ids();
    log.info("Saving query results for queryId: {}. Count: {}", queryId, resultIds.size());
    queryResultsRepository.saveQueryResults(queryId, resultIds);
    sortSequence += resultIds.size();
    if (sortSequence > maxQuerySize) {
      log.info("Query {} with size {} has exceeded maximum query size of {}. Marking execution as failed.",
        queryId, sortSequence, maxQuerySize);
      idsWithCancelCallback.cancel();
      throw new MaxQuerySizeExceededException(queryId, sortSequence, maxQuerySize);
    }
  }
}
