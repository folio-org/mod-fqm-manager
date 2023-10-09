package org.folio.fqm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.fql.FqlService;
import org.folio.fql.model.Fql;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.domain.dto.PurgedQueries;
import org.folio.fqm.exception.InvalidFqlException;
import org.folio.fqm.exception.QueryNotFoundException;
import org.folio.fqm.lib.exception.EntityTypeNotFoundException;
import org.folio.fqm.lib.service.FqlValidationService;
import org.folio.fqm.lib.service.FqmMetaDataService;
import org.folio.fqm.lib.service.QueryProcessorService;
import org.folio.fqm.lib.service.QueryResultsSorterService;
import org.folio.fqm.lib.service.ResultSetService;
import org.folio.fqm.repository.QueryRepository;
import org.folio.fqm.repository.QueryResultsRepository;
import org.folio.querytool.domain.dto.QueryDetails;
import org.folio.querytool.domain.dto.QueryIdentifier;
import org.folio.querytool.domain.dto.ResultsetPage;
import org.folio.querytool.domain.dto.SubmitQuery;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service class responsible for managing a query
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class QueryManagementService {

  private final FolioExecutionContext executionContext;
  private final FqmMetaDataService fqmMetaDataService;
  private final QueryRepository queryRepository;
  private final QueryResultsRepository queryResultsRepository;
  private final QueryExecutionService queryExecutionService;
  private final QueryProcessorService queryProcessorService;
  private final QueryResultsSorterService queryResultsSorterService;
  private final ResultSetService resultSetService;
  private final FqlValidationService fqlValidationService;
  private final FqlService fqlService;
  @Value("${mod-fqm-manager.query-retention-duration}")
  private Duration queryRetentionDuration;

  /**
   * Initiates the asynchronous execution of a query and returns the corresponding query ID.
   *
   * @param submitQuery Query to execute
   * @return ID of the query
   */
  @Transactional
  public QueryIdentifier runFqlQueryAsync(SubmitQuery submitQuery) {
    Query query = Query.newQuery(submitQuery.getEntityTypeId(),
      submitQuery.getFqlQuery(),
      CollectionUtils.isEmpty(submitQuery.getFields()) ? List.of() : submitQuery.getFields(),
      executionContext.getUserId());
    validateQuery(submitQuery.getEntityTypeId(), submitQuery.getFqlQuery());
    QueryIdentifier queryIdentifier = queryRepository.saveQuery(query);
    queryExecutionService.executeQueryAsync(query, Boolean.TRUE.equals(submitQuery.getSortResults()));
    return queryIdentifier;
  }

  /**
   * Executes a query synchronously and returns the page of results.
   *
   * @param query        Query to execute
   * @param entityTypeId ID of the entity type corresponding to the query
   * @param fields       List of fields to return for each element in the result set
   * @param afterId      ID of the element to begin retrieving results after (e.g. if the id of 100th element
   *                     is provided, the first element retrieved in the result set will be the 101st element)
   * @param limit        Maximum number of results to retrieves
   * @return Page containing the results of the query
   */
  public ResultsetPage runFqlQuery(String query, UUID entityTypeId, List<String> fields,
                                   UUID afterId, Integer limit) {
    validateQuery(entityTypeId, query);
    if (CollectionUtils.isEmpty(fields)) {
      fields = new ArrayList<>();
    }
    if (!fields.contains("id")) {
      fields.add("id");
    }
    List<Map<String, Object>> queryResults = queryProcessorService.processQuery(executionContext.getTenantId(),
      entityTypeId, query, fields, afterId, limit);
    return new ResultsetPage().content(queryResults);
  }

  /**
   * Returns the details of a query
   *
   * @param queryId        Query ID
   * @param includeResults Specifies whether the response should include the query results.
   * @param offset         Offset for pagination. The offset parameter is zero-based. Applicable only if
   *                       "includeResults" parameter is true
   * @param limit          Maximum number of results to return. Applicable only if "includeResults" parameter is true
   * @return Details of the query
   */
  @Transactional(readOnly = true)
  public Optional<QueryDetails> getQuery(UUID queryId, boolean includeResults, int offset, int limit) {
    return queryRepository.getQuery(queryId, false)
      .map(query -> new QueryDetails().queryId(queryId)
        .entityTypeId(query.entityTypeId())
        .fqlQuery(query.fqlQuery())
        .fields(query.fields())
        .status(QueryDetails.StatusEnum.valueOf(query.status().toString()))
        .startDate(offsetDateTimeAsDate(query.startDate()))
        .endDate(offsetDateTimeAsDate(query.endDate()))
        .failureReason(query.failureReason())
        .totalRecords(queryResultsRepository.getQueryResultsCount(queryId))
        .content(getContents(queryId, query.entityTypeId(), query.fields(), includeResults, offset, limit)));
  }

  /**
   * Deletes queries that completed execution over a configured number of hours ago (e.g., 3 hours ago) from the system.
   *
   * @return IDs of the removed queries
   */
  @Transactional
  public PurgedQueries deleteOldQueries() {
    List<UUID> queryIds = queryRepository.getQueryIdsCompletedBefore(queryRetentionDuration);
    log.info("Deleting the queries with queryIds {}", queryIds);
    deleteQueryAndResults(queryIds);
    return new PurgedQueries().deletedQueryIds(queryIds);
  }

  /**
   * Deletes a query from the system
   *
   * @param queryId ID of the query to be removed
   */
  @Transactional
  public void deleteQuery(UUID queryId) {
    log.info("Deleting the query with queryId {}", queryId);
    Query query = queryRepository.getQuery(queryId, false).orElseThrow(() -> new QueryNotFoundException(queryId));
    if (query.status() == QueryStatus.IN_PROGRESS) {
      queryRepository.updateQuery(queryId, QueryStatus.CANCELLED, OffsetDateTime.now(), null);
    } else {
      deleteQueryAndResults(List.of(queryId));
    }
  }

  public void validateQuery(UUID entityTypeId, String fqlQuery) {
    EntityType entityType = fqmMetaDataService.getEntityTypeDefinition(executionContext.getTenantId(), entityTypeId)
      .orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId));
    Map<String, String> errorMap = fqlValidationService.validateFql(entityType ,fqlQuery);
    if (!errorMap.isEmpty()) {
      throw new InvalidFqlException(fqlQuery, errorMap);
    }
  }

  public List<UUID> getSortedIds(UUID queryId, int offset, int limit) {
    // We don't actually need the query here, but this provides a nice error if the query isn't present
    Query query = queryRepository.getQuery(queryId, false).orElseThrow(() -> new QueryNotFoundException(queryId));
    return queryResultsSorterService.getSortedIds(executionContext.getTenantId(), queryId, offset, limit);
  }

  public List<Map<String, Object>> getContents(UUID entityTypeId, List<String> fields, List<UUID> ids) {
    return resultSetService.getResultSet(executionContext.getTenantId(), entityTypeId, fields, ids);
  }

  private List<Map<String, Object>> getContents(UUID queryId, UUID entityTypeId, List<String> fields, boolean includeResults, int offset, int limit) {
    if (includeResults) {
      if (CollectionUtils.isEmpty(fields)) {
        Query query = queryRepository.getQuery(queryId, false)
          .orElseThrow(() -> new QueryNotFoundException(queryId));
        Fql fql = fqlService.getFql(query.fqlQuery());
        fields = fqlService.getFqlFields(fql);
      }
      if (!fields.contains("id")) {
        fields.add("id");
      }
      List<UUID> resultIds = queryResultsRepository.getQueryResultIds(queryId, offset, limit);
      return resultSetService.getResultSet(executionContext.getTenantId(), entityTypeId, fields, resultIds);
    }
    return List.of();
  }

  private void deleteQueryAndResults(List<UUID> queryId) {
    queryResultsRepository.deleteQueryResults(queryId);
    queryRepository.deleteQueries(queryId);
  }

  private static Date offsetDateTimeAsDate(OffsetDateTime offsetDateTime) {
    return offsetDateTime == null ? null : Date.from(offsetDateTime.toInstant());
  }
}
