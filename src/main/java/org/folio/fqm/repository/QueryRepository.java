package org.folio.fqm.repository;

import lombok.extern.log4j.Log4j2;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.querytool.domain.dto.QueryIdentifier;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;


@Repository
@Log4j2
public class QueryRepository {
  private static final String QUERY_DETAILS_TABLE = "query_details";
  private static final String QUERY_ID = "query_id";

  private final DSLContext jooqContext;
  private final DSLContext readerJooqContext;
  private final RetryTemplate zombieQueryRetry;

  @Autowired
  public QueryRepository(DSLContext jooqContext,
                         @Qualifier("readerJooqContext") DSLContext readerJooqContext,
                         @Value("${mod-fqm-manager.zombie-query-max-wait-seconds:30}") int zombieQueryMaxWaitSeconds) {
    this.jooqContext = jooqContext;
    this.readerJooqContext = readerJooqContext;

    var retryBuilder = RetryTemplate.builder()
      .retryOn(ZombieQueryException.class);
    if (zombieQueryMaxWaitSeconds != 0) {
      retryBuilder = retryBuilder.exponentialBackoff(Duration.ofSeconds(1), 1.5, Duration.ofSeconds(zombieQueryMaxWaitSeconds))
        .withTimeout(Duration.ofSeconds(zombieQueryMaxWaitSeconds));
    } else { // The max wait == 0, so just disable retrying.
      retryBuilder = retryBuilder.maxAttempts(1);
    }
    this.zombieQueryRetry = retryBuilder.build();
  }

  public QueryIdentifier saveQuery(Query query) {
    jooqContext.insertInto(table(QUERY_DETAILS_TABLE))
      .set(field(QUERY_ID), query.queryId())
      .set(field("entity_type_id"), query.entityTypeId())
      .set(field("fql_query"), query.fqlQuery())
      .set(field("fields"), query.fields().toArray(new String[0]))
      .set(field("created_by"), query.createdBy())
      .set(field("start_date"), query.startDate())
      .set(field("status"), query.status().toString())
      .execute();
    return new QueryIdentifier().queryId(query.queryId());
  }

  public void updateQuery(UUID queryId, QueryStatus queryStatus, OffsetDateTime endDate, String failureReason) {
    jooqContext.update(table(QUERY_DETAILS_TABLE))
      .set(field("status"), queryStatus.toString())
      .set(field("end_date"), endDate)
      .set(field("failure_reason"), failureReason)
      .where(field(QUERY_ID).eq(queryId))
      .execute();
  }

  /**
   * Retrieves a Query by its ID, with optional caching and status validation.
   * <p>
   * This method performs the following steps:
   * 1. Attempts to retrieve the query from the database.
   * 2. If the query is found and its status is IN_PROGRESS, it checks for corresponding running SQL queries.
   * 3. If no running SQL queries are found, it double-checks the query status to account for potential race conditions.
   * 4. If the query is still IN_PROGRESS but no running SQL query is found, it updates the query status to FAILED.
   * <p>
   * Note: This method contains business logic in the repository layer, which might not be ideal
   * but is currently the most convenient place for this logic.
   *
   * @param queryId  The UUID of the query to retrieve.
   * @param useCache A boolean flag indicating whether to use caching for this query.
   * @return An Optional containing the Query if found, or empty if not found.
   */
  @Cacheable(value = "queryCache", condition = "#useCache==true")
  public Optional<Query> getQuery(UUID queryId, boolean useCache) {

    return Optional.ofNullable(zombieQueryRetry.execute(
      context -> getAndValidateQuery(queryId),
      context -> handleZombieQuery(queryId)
    ));
  }

  private Query getAndValidateQuery(UUID queryId) {
    Query query = getQuery(queryId);
    if (query != null
        && query.status() == QueryStatus.IN_PROGRESS
        && getQueryPids(queryId).isEmpty()) {
      log.warn("Query {} has an in-progress status, but no corresponding running SQL query was found. Retrying...", queryId);
      throw new ZombieQueryException(); // This exception is the trigger to retry in the RetryTemplate
    }
    return query;
  }

  private Query handleZombieQuery(UUID queryId) {
    log.error("Query {} still has an in-progress status, but no corresponding running SQL query. Marking it as failed", queryId);
    updateQuery(queryId, QueryStatus.FAILED, OffsetDateTime.now(), "No corresponding running SQL query was found.");
    return getQuery(queryId);
  }

  private static class ZombieQueryException extends RuntimeException {
    public ZombieQueryException() {
      super("🧟");
    }
  }

  // Package-private, to make this visible for tests
  Query getQuery(UUID queryId) {
    return jooqContext.select()
      .from(table(QUERY_DETAILS_TABLE))
      .where(field(QUERY_ID).eq(queryId))
      .fetchOneInto(Query.class);
  }

  public List<Integer> getQueryPids(UUID queryId) {
    String querySearchText = "%Query ID: " + queryId + "%";
    return readerJooqContext
      .select(field("pid", Integer.class))
      .from(table("pg_stat_activity"))
      .where(field("state").eq("active"))
      .and(field("query").like(querySearchText))
      .fetchInto(Integer.class);
  }

  public List<UUID> getQueryIdsStartedBefore(Duration duration) {
    return jooqContext.select(field(QUERY_ID))
      .from(table(QUERY_DETAILS_TABLE))
      .where(field("start_date").
        lessOrEqual(OffsetDateTime.now().minus(duration)))
      .fetchInto(UUID.class);
  }

  public void deleteQueries(List<UUID> queryId) {
    jooqContext.deleteFrom(table(QUERY_DETAILS_TABLE))
      .where(field(QUERY_ID).in(queryId))
      .execute();
  }
}
