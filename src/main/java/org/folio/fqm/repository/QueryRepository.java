package org.folio.fqm.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.querytool.domain.dto.QueryIdentifier;
import org.jooq.DSLContext;
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

  private final DSLContext jooqContext;

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

  @Cacheable(value = "queryCache", condition = "#useCache==true")
  public Optional<Query> getQuery(UUID queryId, boolean useCache) {
    System.out.println("Whatever");
//    log.info("Getting query {}", queryId);

//    var test = jooqContext.select(field("definition")).from(table("entity_type_definition")).fetch();
//    System.out.println("test: " + test);
//
//    var tables = jooqContext.resultQuery("SELECT * FROM INFORMATION_SCHEMA.TABLES").fetch();
//    System.out.println(tables);

//    var result = jooqContext.select()
////      .from(table(QUERY_DETAILS_TABLE))
//      .from(table("tenant_01_mod_fqm_manager.query_details"))
//      .where(field(QUERY_ID).eq(queryId))
//      .fetch();
    var returnValue = Optional.ofNullable(jooqContext.select()
      .from(table(QUERY_DETAILS_TABLE))
      .where(field(QUERY_ID).eq(queryId))
      .fetchOneInto(Query.class));
//    System.out.println("Result: " + result);
    log.info("Return value: {}", returnValue);
    return returnValue;
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
