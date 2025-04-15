package org.folio.fqm.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.querytool.domain.dto.QueryIdentifier;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;


@Repository
@RequiredArgsConstructor
@Log4j2
public class QueryRepository {

  private static final String QUERY_DETAILS_TABLE = "query_details";
  private static final String QUERY_ID = "query_id";
  private static final int MULTIPLE_FOR_STUCK_QUERIES = 3;

  private final DSLContext jooqContext;
  @Qualifier("readerJooqContext")
  private final DSLContext readerJooqContext;

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
   *
   * This method performs the following steps:
   * 1. Attempts to retrieve the query from the database.
   * 2. If the query is found and its status is IN_PROGRESS, it checks for corresponding running SQL queries.
   * 3. If no running SQL queries are found, it double-checks the query status to account for potential race conditions.
   * 4. If the query is still IN_PROGRESS but no running SQL query is found, it updates the query status to FAILED.
   *
   * Note: This method contains business logic in the repository layer, which might not be ideal
   * but is currently the most convenient place for this logic.
   *
   * @param queryId The UUID of the query to retrieve.
   * @param useCache A boolean flag indicating whether to use caching for this query.
   * @return An Optional containing the Query if found, or empty if not found.
   */
  @Cacheable(value = "queryCache", condition = "#useCache==true")
  public Optional<Query> getQuery(UUID queryId, boolean useCache) {
    return getQuery(queryId)
      .flatMap(query -> {
        // If the query is in progress, double-check in the DB to see if there's a running SQL query
        if (query.status() == QueryStatus.IN_PROGRESS && getQueryPids(queryId).isEmpty()) {
          // 😳! Oh, no.
          // But wait! Don't panic yet! This could just be a race condition and the query completed before we checked the running queries.
          log.warn("Query {} has an in-progress status, but no corresponding running SQL query was found. Double-checking...", queryId);
          if (getQuery(queryId).filter(q2 -> q2.status() == QueryStatus.IN_PROGRESS).isPresent()) {
            // Oh, no.
            log.error("Query {} still has an in-progress status, but no corresponding running SQL query. Marking it as failed", queryId);
            // This is business logic, so it probably doesn't belong in the repository layer, but there's not really another convenient place to put it
            updateQuery(queryId, QueryStatus.FAILED, OffsetDateTime.now(), "No corresponding running SQL query was found.");
            return getQuery(queryId);
          }
        }
        return Optional.of(query);
      });
  }

  // Package-private, to make this visible for tests
  Optional<Query> getQuery(UUID queryId) {
    return Optional.ofNullable(jooqContext.select()
      .from(table(QUERY_DETAILS_TABLE))
      .where(field(QUERY_ID).eq(queryId))
      .fetchOneInto(Query.class));
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

  public List<UUID> getQueryIdsForDeletion(Duration retentionDuration) {
    return jooqContext.select(field(QUERY_ID))
      .from(table(QUERY_DETAILS_TABLE))
      .where(
        field("end_date").lessThan(
          field("CURRENT_TIMESTAMP - INTERVAL '{0} seconds'", OffsetDateTime.class, retentionDuration.getSeconds())
        ).or(
          field("start_date").lessThan(
            field("CURRENT_TIMESTAMP - INTERVAL '{0} seconds'", OffsetDateTime.class,
              retentionDuration.multipliedBy(MULTIPLE_FOR_STUCK_QUERIES).getSeconds())
          )
        )
      )
      .fetchInto(UUID.class);
  }

    public void deleteQueries(List<UUID> queryId) {
    jooqContext.deleteFrom(table(QUERY_DETAILS_TABLE))
      .where(field(QUERY_ID).in(queryId))
      .execute();
  }
}
