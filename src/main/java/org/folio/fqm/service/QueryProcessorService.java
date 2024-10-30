package org.folio.fqm.service;

import org.folio.fql.model.Fql;
import org.folio.fql.service.FqlService;
import org.folio.fqm.model.FqlQueryWithContext;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.repository.IdStreamer;
import org.folio.fqm.repository.ResultSetRepository;
import org.folio.querytool.domain.dto.EntityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Class responsible for processing FQL queries.
 */
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class QueryProcessorService {
  private final ResultSetRepository resultSetRepository;
  private final IdStreamer idStreamer;
  private final FqlService fqlService;
  private final CrossTenantQueryService crossTenantQueryService;

  /**
   * Process the FQL query and return the result IDs in batches.
   *
   * @param fqlQueryWithContext FQL query, along with the relevant context for the execution of the query
   * @param batchSize           Count of IDs to be returned in a single batch
   * @param idsConsumer         This consumer will receive the IDs in batches, and each batch will also include a
   *                            callback for canceling the query. The consuming application can utilize this callback
   *                            to cancel the query.
   * @param successHandler      Once the query execution is finished, this consumer is triggered. It will be provided
   *                            with the overall count of records that corresponded to the query.
   * @param errorHandler        If an error occurs during query execution, this consumer will be called and given the
   *                            exception responsible for the failure
   */
  public void getIdsInBatch(FqlQueryWithContext fqlQueryWithContext,
                            int batchSize,
                            Consumer<IdsWithCancelCallback> idsConsumer,
                            IntConsumer successHandler,
                            Consumer<Throwable> errorHandler) {
    try {
      Fql fql = fqlService.getFql(fqlQueryWithContext.fqlQuery());
      int idsCount = idStreamer.streamIdsInBatch(
        fqlQueryWithContext.entityType(),
        fqlQueryWithContext.sortResults(),
        fql,
        batchSize,
        idsConsumer);
      successHandler.accept(idsCount);
    } catch (Exception exception) {
      errorHandler.accept(exception);
    }
  }

  /**
   * Process the FQL query and return the results.
   *
   * @param entityType Entity type
   * @param fqlQuery   FQL query
   * @param fields     fields to return in query results
   * @param afterId    A cursor used for pagination. 'afterId' is the ID representing your place in the paginated list.
   *                   This parameter should be omitted in the first call. All subsequent pagination call should
   *                   include 'afterId'
   * @param limit      Count of records to be returned.
   * @return Results matching the query
   */
  public List<Map<String, Object>> processQuery(EntityType entityType, String fqlQuery, List<String> fields, List<String> afterId, Integer limit) {
    Fql fql = fqlService.getFql(fqlQuery);
    boolean ecsEnabled = crossTenantQueryService.ecsEnabled();
    List<String> tenantsToQuery = crossTenantQueryService.getTenantsToQuery(entityType);
    return resultSetRepository.getResultSetSync(
      UUID.fromString(entityType.getId()),
      fql,
      fields,
      afterId,
      limit,
      tenantsToQuery,
      ecsEnabled
    );
  }
}
