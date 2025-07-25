package org.folio.fqm.service;

import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.fql.service.FqlValidationService;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.domain.dto.PurgedQueries;
import org.folio.fqm.exception.InvalidFqlException;
import org.folio.fqm.exception.QueryNotFoundException;
import org.folio.fqm.migration.MigratableQueryInformation;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

import static org.folio.fqm.domain.QueryStatus.FAILED;

/**
 * Service class responsible for managing a query
 */
@Service
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
  private final MigrationService migrationService;
  private final RetryTemplate zombieQueryRetry;

  @Value("${mod-fqm-manager.query-retention-duration}")
  private Duration queryRetentionDuration;
  @Setter
  @Value("${mod-fqm-manager.max-query-size}")
  private int maxConfiguredQuerySize;


  @Autowired
  public QueryManagementService(EntityTypeService entityTypeService,
                                FolioExecutionContext executionContext,
                                QueryRepository queryRepository,
                                QueryResultsRepository queryResultsRepository,
                                QueryExecutionService queryExecutionService,
                                QueryProcessorService queryProcessorService,
                                QueryResultsSorterService queryResultsSorterService,
                                ResultSetService resultSetService,
                                FqlValidationService fqlValidationService,
                                CrossTenantQueryService crossTenantQueryService,
                                MigrationService migrationService,
                                @Value("${mod-fqm-manager.zombie-query-max-wait-seconds:30}") int zombieQueryMaxWaitSeconds) {
    this.entityTypeService = entityTypeService;
    this.executionContext = executionContext;
    this.queryRepository = queryRepository;
    this.queryResultsRepository = queryResultsRepository;
    this.queryExecutionService = queryExecutionService;
    this.queryProcessorService = queryProcessorService;
    this.queryResultsSorterService = queryResultsSorterService;
    this.resultSetService = resultSetService;
    this.fqlValidationService = fqlValidationService;
    this.crossTenantQueryService = crossTenantQueryService;
    this.migrationService = migrationService;
    var retryBuilder = RetryTemplate.builder()
      .retryOn(ZombieQueryException.class);
    if (zombieQueryMaxWaitSeconds != 0) {
      retryBuilder = retryBuilder.exponentialBackoff(Duration.ofSeconds(1), 1.5, Duration.ofSeconds(zombieQueryMaxWaitSeconds))
        .withTimeout(Duration.ofSeconds(zombieQueryMaxWaitSeconds));
    } else { // The max wait == 0, so only retry once
      retryBuilder = retryBuilder.maxAttempts(2);
    }
    this.zombieQueryRetry = retryBuilder.build();
  }

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
    validateQuery(submitQuery.getEntityTypeId(), submitQuery.getFqlQuery());

    // Verify that the query is up to date before execution
    // Note: Use the actual requested fields, not the default ones from the entity type
    MigratableQueryInformation migratableQueryInformation = new MigratableQueryInformation(submitQuery.getEntityTypeId(), submitQuery.getFqlQuery(), submitQuery.getFields() != null ? submitQuery.getFields() : List.of());
    migrationService.throwExceptionIfQueryNeedsMigration(migratableQueryInformation);

    QueryIdentifier queryIdentifier = queryRepository.saveQuery(query);
    int maxQuerySize = submitQuery.getMaxSize() == null ? maxConfiguredQuerySize : Math.min(submitQuery.getMaxSize(), maxConfiguredQuerySize);
    queryExecutionService.executeQueryAsync(query, entityType, maxQuerySize);
    return queryIdentifier;
  }

  /**
   * Executes a query synchronously and returns the page of results.
   *
   * @param query        Query to execute
   * @param entityTypeId ID of the entity type corresponding to the query
   * @param fields       List of fields to return for each element in the result set
   * @param limit        Maximum number of results to retrieves
   * @return Page containing the results of the query
   */
  public ResultsetPage runFqlQuery(String query, UUID entityTypeId, List<String> fields,
                                   Integer limit) {
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

    // Verify that the query is up to date before execution
    MigratableQueryInformation migratableQueryInformation = new MigratableQueryInformation(entityTypeId, query, fields);
    migrationService.throwExceptionIfQueryNeedsMigration(migratableQueryInformation);

    List<Map<String, Object>> queryResults = queryProcessorService.processQuery(entityType, query, fields, limit);
    // NOTE: unlike the async query, which returns the total number of records matching the query, the synchronous query
    // API returns the number of records included in this individual response, which may be less than the total number
    // of records matching the query.
    return new ResultsetPage().content(queryResults).totalRecords(queryResults.size());
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
    return getPotentialZombieQuery(queryId)
      .map(query -> {
        QueryDetails details = new QueryDetails()
          .queryId(queryId)
          .entityTypeId(query.entityTypeId())
          .fqlQuery(query.fqlQuery())
          .fields(query.fields())
          .status(QueryDetails.StatusEnum.valueOf(query.status().toString()))
          .startDate(offsetDateTimeAsDate(query.startDate()))
          .endDate(offsetDateTimeAsDate(query.endDate()))
          .failureReason(query.failureReason());
        if (!query.status().equals(FAILED)) {
          details.totalRecords(queryResultsRepository.getQueryResultsCount(queryId));
        }
        details.content(getContents(queryId, query.entityTypeId(), query.fields(), includeResults, offset, limit));
        return details;
      });
  }

  /**
   * Retrieves a Query by its ID, with validation that fails queries with no backing DB query
   * <p>
   * This method performs the following steps:
   * 1. Attempts to retrieve the query from the database.
   * 2. If the query is found and its status is IN_PROGRESS, it checks for corresponding running SQL queries.
   * 3. If no running SQL queries are found, it double-checks the query status to account for potential race conditions.
   * 4. If the query is still IN_PROGRESS but no running SQL query is found, it updates the query status to FAILED.
   *
   * @param queryId The UUID of the query to retrieve.
   * @return An Optional containing the Query if found, or empty if not found.
   */
  public Optional<Query> getPotentialZombieQuery(UUID queryId) {

    return zombieQueryRetry.execute(
      context -> getAndValidateQuery(queryId),
      context -> handleZombieQuery(queryId)
    );
  }

  private Optional<Query> getAndValidateQuery(UUID queryId) {
    Optional<Query> query = queryRepository.getQuery(queryId, false);
    if (query.filter(q -> q.status() == QueryStatus.IN_PROGRESS).isPresent()
      && queryRepository.getSelectQueryPids(queryId).isEmpty()
      && queryRepository.getInsertQueryPids(queryId).isEmpty()
    ) {
      log.warn("Query {} has an in-progress status, but no corresponding running SQL query was found. Retrying...", queryId);
      throw new ZombieQueryException(); // This exception is the trigger to retry in the RetryTemplate
    }
    return query;
  }

  private Optional<Query> handleZombieQuery(UUID queryId) {
    log.error("Query {} still has an in-progress status, but no corresponding running SQL query. Marking it as failed", queryId);
    queryRepository.updateQuery(queryId, FAILED, OffsetDateTime.now(), "No corresponding running SQL query was found.");
    return queryRepository.getQuery(queryId, false);
  }

  private static class ZombieQueryException extends RuntimeException {
    public ZombieQueryException() {
      super("🧟"); // BRAAAAAAAAAINS!!!!!!
    }
  }

  /**
   * Deletes queries that completed execution over a configured number of hours ago (e.g., 3 hours ago) from the system.
   *
   * @return IDs of the removed queries
   */
  public PurgedQueries deleteOldQueries() {
    List<UUID> queryIds = queryRepository.getQueryIdsForDeletion(queryRetentionDuration);
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
    Query query = getPotentialZombieQuery(queryId).orElseThrow(() -> new QueryNotFoundException(queryId));

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
