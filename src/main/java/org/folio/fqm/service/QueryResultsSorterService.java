package org.folio.fqm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.repository.IdStreamer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Sorts the results of a query according to the default sort criteria of the entity type, and then
 * streams back to calling application.
 */
@Log4j2
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class QueryResultsSorterService {

  private final IdStreamer idStreamer;

  public List<List<String>> getSortedIds(UUID queryId,
                                         int offset, int limit) {
    log.debug("Getting sorted ids for query {}, offset {}, limit {}", queryId, offset, limit);
    // Sort ids based on the sort criteria defined in the entity type definition
    // Note: This does not sort right now, due to performance concerns. Instead, it just pulls straight from query_results
    return idStreamer.getSortedIds("query_results", offset, limit, queryId);
  }
}
