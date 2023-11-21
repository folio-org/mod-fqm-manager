package org.folio.fqm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.exception.QueryNotFoundException;
import org.folio.fqm.lib.model.IdsWithCancelCallback;
import org.folio.fqm.repository.QueryRepository;
import org.folio.fqm.repository.QueryResultsRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Log4j2
public class DataBatchCallback  {
  private final QueryRepository queryRepository;
  private final QueryResultsRepository queryResultsRepository;
  private int sortSequence = 0;

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
    queryResultsRepository.saveQueryResults(queryId, resultIds, sortSequence);
    sortSequence += resultIds.size();
  }

}
