package org.folio.fqm.service;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.fql.service.FqlValidationService;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.domain.dto.PurgedQueries;
import org.folio.fqm.exception.InvalidFqlException;
import org.folio.fqm.exception.QueryNotFoundException;
import org.folio.fqm.repository.QueryRepository;
import org.folio.fqm.repository.QueryResultsRepository;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.Field;
import org.folio.querytool.domain.dto.QueryDetails;
import org.folio.querytool.domain.dto.QueryIdentifier;
import org.folio.querytool.domain.dto.ResultsetPage;
import org.folio.querytool.domain.dto.SubmitQuery;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * Service class responsible for managing a query
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class QueryManagementService {

  private final EntityTypeService entityTypeService;
  private final FolioExecutionContext executionContext;
  private final QueryRepository queryRepository;
  private final QueryResultsRepository queryResultsRepository;
  private final QueryExecutionService queryExecutionService;
  private final QueryProcessorService queryProcessorService;
  private final QueryResultsSorterService queryResultsSorterService;
  private final ResultSetService resultSetService;
  private final FqlValidationService fqlValidationService;
  private final CrossTenantQueryService crossTenantQueryService;

  @Value("${mod-fqm-manager.query-retention-duration}")
  private Duration queryRetentionDuration;
  @Setter
  @Value("${mod-fqm-manager.max-query-size}")
  private int maxConfiguredQuerySize;

  /**
   * Initiates the asynchronous execution of a query and returns the corresponding query ID.
   *
   * @param submitQuery Query to execute
   * @return ID of the query
   */
  public QueryIdentifier runFqlQueryAsync(SubmitQuery submitQuery) {
    EntityType entityType = entityTypeService
      .getEntityTypeDefinition(submitQuery.getEntityTypeId(), true);
    List<String> fields = CollectionUtils.isEmpty(submitQuery.getFields()) ?
      getFieldsFromEntityType(entityType) : new ArrayList<>(submitQuery.getFields());
    List<String> idColumns = EntityTypeUtils.getIdColumnNames(entityType);
    for (String idColumn : idColumns) {
      if (!fields.contains(idColumn)) {
        fields.add(idColumn);
      }
    }
    Query query = Query.newQuery(submitQuery.getEntityTypeId(),
      submitQuery.getFqlQuery(),
      fields,
      executionContext.getUserId());
    int maxQuerySize = submitQuery.getMaxSize() == null ? maxConfiguredQuerySize : Math.min(submitQuery.getMaxSize(), maxConfiguredQuerySize);
    validateQuery(submitQuery.getEntityTypeId(), submitQuery.getFqlQuery());
    QueryIdentifier queryIdentifier = queryRepository.saveQuery(query);
    queryExecutionService.executeQueryAsync(query, entityType, maxQuerySize);
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
                                   List<String> afterId, Integer limit) {
    validateQuery(entityTypeId, query);
    if (CollectionUtils.isEmpty(fields)) {
      fields = new ArrayList<>();
    }
    EntityType entityType = entityTypeService.getEntityTypeDefinition(entityTypeId, true);
    List<String> idColumns = EntityTypeUtils.getIdColumnNames(entityType);
    for (String idColumn : idColumns) {
      if (!fields.contains(idColumn)) {
        fields.add(idColumn);
      }
    }
    List<Map<String, Object>> queryResults = queryProcessorService.processQuery(entityType, query, fields, afterId, limit);
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
  public PurgedQueries deleteOldQueries() {
    List<UUID> queryIds = queryRepository.getQueryIdsStartedBefore(queryRetentionDuration);
    log.info("Deleting the queries with queryIds {}", queryIds);
    deleteQueryAndResults(queryIds);
    return new PurgedQueries().deletedQueryIds(queryIds);
  }

  /**
   * Deletes a query from the system
   *
   * @param queryId ID of the query to be removed
   */
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
    EntityType entityType = entityTypeService.getEntityTypeDefinition(entityTypeId, true);
    Map<String, String> errorMap = fqlValidationService.validateFql(entityType, fqlQuery);
    if (!errorMap.isEmpty()) {
      throw new InvalidFqlException(fqlQuery, errorMap);
    }
  }

  @SuppressWarnings("java:S2201") // we just use orElseThrow to conveniently throw an exception, we don't want the value
  public List<List<String>> getSortedIds(UUID queryId, int offset, int limit) {
    Query query = queryRepository.getQuery(queryId, false).orElseThrow(() -> new QueryNotFoundException(queryId));

    // ensures it exists
    entityTypeService.getEntityTypeDefinition(query.entityTypeId(), true);

    return queryResultsSorterService.getSortedIds(queryId, offset, limit);
  }

  public List<Map<String, Object>> getContents(UUID entityTypeId, List<String> fields, List<List<String>> ids, UUID userId, boolean localize, boolean privileged) {
    EntityType entityType = entityTypeService.getEntityTypeDefinition(entityTypeId, true);
    EntityTypeUtils.getIdColumnNames(entityType)
      .forEach(colName -> {
        if (!fields.contains(colName)) {
          fields.add(colName);
        }
      });
    List<String> tenantsToQuery = privileged
      ? crossTenantQueryService.getTenantsToQuery(entityType, userId)
      : crossTenantQueryService.getTenantsToQuery(entityType);
    return resultSetService.getResultSet(entityTypeId, fields, ids, tenantsToQuery, localize);
  }

  private List<Map<String, Object>> getContents(UUID queryId, UUID entityTypeId, List<String> fields, boolean includeResults, int offset, int limit) {
    if (includeResults) {
      EntityType entityType = entityTypeService.getEntityTypeDefinition(entityTypeId, true);
      List<List<String>> resultIds = queryResultsRepository.getQueryResultIds(queryId, offset, limit);
      List<String> tenantsToQuery = crossTenantQueryService.getTenantsToQuery(entityType);
      return resultSetService.getResultSet(entityTypeId, fields, resultIds, tenantsToQuery, false);
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

  private List<String> getFieldsFromEntityType(EntityType entityType) {
    return (entityType.getColumns() != null ? entityType.getColumns() : Collections.<EntityTypeColumn>emptyList())
      .stream()
      .map(Field::getName)
      .toList();
  }
}
