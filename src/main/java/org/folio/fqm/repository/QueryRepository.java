package org.folio.fqm.repository;

import lombok.extern.log4j.Log4j2;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.querytool.domain.dto.QueryIdentifier;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
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
@Log4j2
public class QueryRepository {
  private static final String QUERY_DETAILS_TABLE = "query_details";
  private static final String QUERY_ID = "query_id";
  private static final int MULTIPLE_FOR_STUCK_QUERIES = 3;

  private final DSLContext jooqContext;
  private final DSLContext readerJooqContext;

  @Autowired
  public QueryRepository(DSLContext jooqContext,
                         @Qualifier("readerJooqContext") DSLContext readerJooqContext) {
    this.jooqContext = jooqContext;
    this.readerJooqContext = readerJooqContext;
  }

  public QueryIdentifier saveQuery(Query query) {
    jooqContext.insertInto(table(QUERY_DETAILS_TABLE))
      .set(field(QUERY_ID), query.queryId())
      .set(field("entity_type_id"), query.entityTypeId())
      .set(field("fql_query"), query.fqlQuery())
      .set(field("fields"), query.fields().toArray(new String[0]))
      .set(field("created_by"), query.createdBy())
      .set(field("start_date"), field("timezone('UTC', now())", OffsetDateTime.class))
      .set(field("status"), query.status().toString())
      .execute();
    return new QueryIdentifier().queryId(query.queryId());
  }

  public void updateQuery(UUID queryId, QueryStatus queryStatus, OffsetDateTime endDate, String failureReason) {
    jooqContext.update(table(QUERY_DETAILS_TABLE))
      .set(field("status"), queryStatus.toString())
      .set(field("end_date"), field("timezone('UTC', {0})", OffsetDateTime.class, endDate))
      .set(field("failure_reason"), failureReason)
      .where(field(QUERY_ID).eq(queryId))
      .execute();
  }

  // Public wrapper around getQuery(UUID), to expose the cached version
  @Cacheable(value = "queryCache", condition = "#useCache==true")
  public Optional<Query> getQuery(UUID queryId, boolean useCache) {
    return getQuery(queryId);
  }

  // Package-private, to make this visible for tests
  Optional<Query> getQuery(UUID queryId) {
    Query query = jooqContext.select()
      .from(table(QUERY_DETAILS_TABLE))
      .where(field(QUERY_ID).eq(queryId))
      .fetchOneInto(Query.class);
    return Optional.ofNullable(query);
  }

  public List<Integer> getSelectQueryPids(UUID queryId) {
    return getQueryPids(readerJooqContext, queryId);
  }

  public List<Integer> getInsertQueryPids(UUID queryId) {
    return getQueryPids(jooqContext, queryId);
  }

  private List<Integer> getQueryPids(DSLContext context, UUID queryId) {
    String pattern = "%/* Query ID: " + queryId + " */%";
    return context
      .select(field("pid", Integer.class))
      .from(table("pg_stat_activity"))
      .where(field("state").eq("active"))
      .and(field("query").like(pattern))
      .fetchInto(Integer.class);
  }

  public List<UUID> getQueryIdsForDeletion(Duration retentionDuration) {
    long retentionSeconds = retentionDuration.getSeconds();
    long stuckQuerySeconds = retentionDuration.multipliedBy(MULTIPLE_FOR_STUCK_QUERIES).getSeconds();
    return jooqContext.select(field(QUERY_ID))
      .from(table(QUERY_DETAILS_TABLE))
      .where(field("end_date")
        .lessThan(field("CURRENT_TIMESTAMP AT TIME ZONE 'UTC' - INTERVAL '" + retentionSeconds + " second'"))
      ).or(
        field("start_date").lessThan(
            field("CURRENT_TIMESTAMP AT TIME ZONE 'UTC' - INTERVAL '" + stuckQuerySeconds + " second'"))
          )
      .fetchInto(UUID.class);
}

  public void deleteQueries(List<UUID> queryId) {
    jooqContext.deleteFrom(table(QUERY_DETAILS_TABLE))
      .where(field(QUERY_ID).in(queryId))
      .execute();
  }
}
