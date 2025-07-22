package org.folio.fqm.service;

import lombok.extern.slf4j.Slf4j;
import org.folio.fql.model.Fql;
import org.folio.fql.service.FqlService;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.exception.QueryNotFoundException;
import org.folio.fqm.model.FqlQueryWithContext;
import org.folio.fqm.repository.IdStreamer;
import org.folio.fqm.repository.QueryRepository;
import org.folio.fqm.repository.ResultSetRepository;
import org.folio.querytool.domain.dto.EntityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Class responsible for processing FQL queries.
 */
@Service
@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class QueryProcessorService {
  private final ResultSetRepository resultSetRepository;
  private final IdStreamer idStreamer;
  private final FqlService fqlService;
  private final CrossTenantQueryService crossTenantQueryService;
  private final QueryRepository queryRepository;

  /**
   * Process the FQL query and return the result IDs in batches.
   *
   * @param fqlQueryWithContext FQL query, along with the relevant context for the execution of the query
   * @param batchSize           Count of IDs to be returned in a single batch
   * @param maxQuerySize        This consumer will receive the IDs in batches, and each batch will also include a
   *                            callback for canceling the query. The consuming application can utilize this callback
   *                            to cancel the query.
   */
  public void getIdsInBatch(FqlQueryWithContext fqlQueryWithContext,
                            int batchSize,
                            int maxQuerySize,
                            Query query) {
    Fql fql = fqlService.getFql(fqlQueryWithContext.fqlQuery());
    idStreamer.streamIdsInBatch(
      fqlQueryWithContext.entityType(),
      fqlQueryWithContext.sortResults(),
      fql,
      batchSize,
      maxQuerySize,
      query.queryId());
    handleSuccess(query);
  }

  public void handleSuccess(Query query) {
    Query savedQuery = queryRepository.getQuery(query.queryId(), false)
      .orElseThrow(() -> new QueryNotFoundException(query.queryId()));
    if (savedQuery.status() == QueryStatus.CANCELLED) {
      log.info("Query {} has been cancelled, therefore not updating", query.queryId());
      return;
    }
    log.info("Query Execution completed for queryId {}, entityTypeId {}", query.queryId(),
      query.entityTypeId());
    queryRepository.updateQuery(query.queryId(), QueryStatus.SUCCESS, OffsetDateTime.now(), null);
  }


  /**
   * Process the FQL query and return the results.
   *
   * @param entityType Entity type
   * @param fqlQuery   FQL query
   * @param fields     fields to return in query results
   * @param limit      Count of records to be returned.
   * @return Results matching the query
   */
  public List<Map<String, Object>> processQuery(EntityType entityType, String fqlQuery, List<String> fields, Integer limit) {
    Fql fql = fqlService.getFql(fqlQuery);
    boolean ecsEnabled = crossTenantQueryService.ecsEnabled();
    List<String> tenantsToQuery = crossTenantQueryService.getTenantsToQuery(entityType);
    return resultSetRepository.getResultSetSync(
      UUID.fromString(entityType.getId()),
      fql,
      fields,
      limit,
      tenantsToQuery,
      ecsEnabled
    );
  }
}
