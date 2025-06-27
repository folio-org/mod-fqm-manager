package org.folio.fqm.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.exception.MaxQuerySizeExceededException;
import org.folio.fqm.exception.QueryNotFoundException;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.service.CrossTenantQueryService;
import org.folio.fqm.service.EntityTypeFlatteningService;
import org.folio.fqm.service.FqlToSqlConverterService;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.fqm.utils.StreamHelper;
import org.folio.fqm.utils.flattening.FromClauseUtils;
import org.folio.fql.model.Fql;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.ResultQuery;
import org.jooq.Select;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.jooq.Record1;
import org.jooq.Field;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.folio.fqm.utils.EntityTypeUtils.RESULT_ID_FIELD;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Log4j2
public class IdStreamer {

  private static final long QUERY_CANCELLATION_CHECK_SECONDS = 30;

  @Qualifier("readerJooqContext")
  private final DSLContext jooqContext;
  private final EntityTypeFlatteningService entityTypeFlatteningService;
  private final CrossTenantQueryService crossTenantQueryService;
  private final FolioExecutionContext executionContext;
  private final QueryRepository queryRepository;
  private final QueryResultsRepository queryResultsRepository;
  private final ScheduledExecutorService executorService;

  /**
   * Executes the given Fql Query and stream the result Ids back.
   */
  public void streamIdsInBatch(EntityType entityType,
                               boolean sortResults,
                               Fql fql,
                               int batchSize,
                               int maxQuerySize,
                               UUID queryId) {
    boolean ecsEnabled = crossTenantQueryService.ecsEnabled();
    List<String> tenantsToQuery = crossTenantQueryService.getTenantsToQuery(entityType);
    this.streamIdsInBatch(entityType, sortResults, fql, batchSize, maxQuerySize, queryId, tenantsToQuery, ecsEnabled);
  }

  public List<List<String>> getSortedIds(String derivedTableName,
                                         int offset, int batchSize, UUID queryId) {
    // THIS DOES NOT PROVIDE SORTED IDs! This is a temporary workaround to address performance issues until we
    // can do it properly
    return jooqContext.dsl()
      .select(RESULT_ID_FIELD)
      // NOTE: derivedTableName is <schema>.query_results here as part of the workaround
      .from(table(derivedTableName))
      .where(field("query_id").eq(queryId))
      .orderBy(RESULT_ID_FIELD)
      .offset(offset)
      .limit(batchSize)
      .fetchSize(batchSize)
      .fetch()
      .map(Record1::value1)
      .stream()
      .map(Arrays::asList)
      .toList();
  }

  private void streamIdsInBatch(EntityType entityType,
                                boolean sortResults,
                                Fql fql, int batchSize,
                                int maxQuerySize, UUID queryId, List<String> tenantsToQuery, boolean ecsEnabled) {
    UUID entityTypeId = UUID.fromString(entityType.getId());
    log.debug("List of tenants to query: {}", tenantsToQuery);
    Field<String[]> idValueGetter = EntityTypeUtils.getResultIdValueGetter(entityType);
    Select<Record1<String[]>> fullQuery = null;
    for (String tenantId : tenantsToQuery) {
      EntityType entityTypeDefinition = tenantId != null && tenantId.equals(executionContext.getTenantId()) ?
        entityType : entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, tenantId, false);
      Field<String[]> currentIdValueGetter = EntityTypeUtils.getResultIdValueGetter(entityTypeDefinition);

      // We may have joins to columns which are filtered out via essentialOnly/etc. Therefore, we must re-fetch
      // the entity type with all columns preserved to build the from clause. However, we do not want to only
      // use this version, though, as we want to ensure excluded columns are not used in queries. so we need both.
      EntityType entityTypeDefinitionWithAllFields = entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, tenantId, true);
      String innerFromClause = FromClauseUtils.getFromClause(entityTypeDefinitionWithAllFields, tenantId);

      Condition whereClause = FqlToSqlConverterService.getSqlCondition(fql.fqlCondition(), entityTypeDefinition);


      if (!CollectionUtils.isEmpty(entityType.getFilterConditions())) {
        for (String condition : entityType.getFilterConditions()) {
          whereClause = whereClause.and(condition);
        }
      }
      if (ecsEnabled && !CollectionUtils.isEmpty(entityType.getAdditionalEcsConditions())) {
        for (String condition : entityType.getAdditionalEcsConditions()) {
          whereClause = whereClause.and(condition);
        }
      }

      ResultQuery<Record1<String[]>> innerQuery = buildQuery(
        entityTypeDefinition,
        currentIdValueGetter,
        innerFromClause,
        whereClause,
        sortResults,
        batchSize,
        queryId
      );
      if (fullQuery == null) {
        fullQuery = (Select<Record1<String[]>>) innerQuery;
      } else {
        fullQuery = fullQuery.unionAll((Select<Record1<String[]>>) innerQuery);
      }
    }
    log.debug("Full query: {}", fullQuery);

    if (fullQuery != null) {
      fullQuery.fetchSize(batchSize);
    }

    monitorQueryCancellation(queryId);

    try (
      Cursor<Record1<String[]>> idsCursor = fullQuery.fetchLazy();
      Stream<String[]> idStream = idsCursor
        .stream()
        // This is something we may want to revisit. This implementation is a bit hackish for cross-tenant queries,
        // though it does work
        .map(row -> row.getValue(idValueGetter));
      Stream<List<String[]>> idsStream = StreamHelper.chunk(idStream, batchSize)
    ) {
      var total = new AtomicInteger();
      idsStream.map(ids -> new IdsWithCancelCallback(ids, idsStream::close))
        .forEach(idsWithCancelCallback -> handleBatch(queryId, idsWithCancelCallback, maxQuerySize, total));
    }
  }

  void handleBatch(UUID queryId, IdsWithCancelCallback idsWithCancelCallback, Integer maxQuerySize, AtomicInteger total) {
    Query query = queryRepository.getQuery(queryId, false)
      .orElseThrow(() -> new QueryNotFoundException(queryId));
    if (query.status() == QueryStatus.CANCELLED) {
      log.info("Query {} has been cancelled, closing id stream", queryId);
      idsWithCancelCallback.cancel();
      return;
    }
    List<String[]> resultIds = idsWithCancelCallback.ids();
    log.info("Saving query results for queryId: {}. Count: {}", queryId, resultIds.size());
    queryResultsRepository.saveQueryResults(queryId, resultIds);
    if (total.addAndGet(resultIds.size()) > maxQuerySize) {
      log.info("Query {} with size {} has exceeded maximum query size of {}.",
        queryId, total.get(), maxQuerySize);
      idsWithCancelCallback.cancel();
      throw new MaxQuerySizeExceededException(queryId, total.get(), maxQuerySize);
    }
  }

  void monitorQueryCancellation(UUID queryId) {
    Runnable cancellationMonitor = new Runnable() {
      @Override
      public void run() {
        try {
          log.debug("Checking query cancellation for query {}", queryId);
          QueryStatus queryStatus = queryRepository
            .getQuery(queryId, false)
            .orElseThrow(() -> new QueryNotFoundException(queryId))
            .status();
          if (queryStatus == QueryStatus.CANCELLED) {
            cancelQuery(queryId);
          } else if (queryStatus == QueryStatus.IN_PROGRESS) {
            // Reschedule the cancellation monitor if query is still in progress
            executorService.schedule(this, QUERY_CANCELLATION_CHECK_SECONDS, TimeUnit.SECONDS);
          }
        } catch (Exception e) {
          log.error("Unexpected error occurred while cancelling query: {}", e.getMessage(), e);
        }
      }
    };
    executorService.schedule(cancellationMonitor, QUERY_CANCELLATION_CHECK_SECONDS, TimeUnit.SECONDS);
  }

  void cancelQuery(UUID queryId) {
    log.info("Query {} has been marked as cancelled. Cancelling query in database.", queryId);
    List<Integer> pids = queryRepository.getSelectQueryPids(queryId);
    for (int pid : pids) {
      log.debug("PID for the executing query: {}", pid);
      jooqContext.execute("SELECT pg_cancel_backend(?)", pid);
    }
  }

  private ResultQuery<Record1<String[]>> buildQuery(EntityType entityType, Field<String[]> idValueGetter, String finalJoinClause, Condition sqlWhereClause, boolean sortResults, int batchSize, UUID queryId) {
    String hint = "/* Query ID: " + queryId + " */";
    if (!isEmpty(entityType.getGroupByFields())) {
      Field<?>[] groupByFields = entityType
        .getColumns()
        .stream()
        .filter(col -> entityType.getGroupByFields().contains(col.getName()))
        .map(col -> col.getFilterValueGetter() == null ? col.getValueGetter() : col.getFilterValueGetter())
        .map(DSL::field)
        .toArray(Field[]::new);
      return jooqContext
        .select(field(idValueGetter))
        .hint(hint)
        .from(finalJoinClause)
        .where(sqlWhereClause)
        .groupBy(groupByFields)
        .orderBy(EntityTypeUtils.getSortFields(entityType, sortResults))
        .fetchSize(batchSize);
    } else {
      return jooqContext
        .select(field(idValueGetter))
        .hint(hint)
        .from(finalJoinClause)
        .where(sqlWhereClause)
        .orderBy(EntityTypeUtils.getSortFields(entityType, sortResults))
        .fetchSize(batchSize);
    }
  }
}
