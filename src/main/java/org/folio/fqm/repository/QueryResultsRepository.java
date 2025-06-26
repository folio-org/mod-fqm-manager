package org.folio.fqm.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Table;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.folio.fqm.utils.EntityTypeUtils.RESULT_ID_FIELD;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.field;

@Repository
@RequiredArgsConstructor
@Log4j2
public class QueryResultsRepository {
  private static final Table<Record> QUERY_RESULTS_TABLE = table("query_results");
  private static final Field<UUID> QUERY_ID_FIELD = field("query_id", UUID.class);

  @Qualifier("readerJooqContext")
  private final DSLContext readerJooqContext;
  private final DSLContext jooqContext;


  public void saveQueryResults(UUID queryId, List<String[]> resultIds) {
    log.info("Received data batch for queryId {}", queryId);
    log.debug("Data batch: {}", resultIds);

    List<Record2<UUID, String[]>> records = resultIds.stream()
      .map(resultId -> jooqContext.newRecord(QUERY_ID_FIELD, RESULT_ID_FIELD).values(queryId, resultId))
      .toList();
    String queryIdComment = String.format("/* Query ID: %s */ {0}", queryId);
    jooqContext.query(
      queryIdComment,
      jooqContext.insertInto(QUERY_RESULTS_TABLE, QUERY_ID_FIELD, RESULT_ID_FIELD).valuesOfRecords(records)
    ).execute();
  }

  public int getQueryResultsCount(UUID queryId) {
    log.info("Retrieving result count for queryId {}", queryId);
    return readerJooqContext.fetchCount(QUERY_RESULTS_TABLE.where(QUERY_ID_FIELD.eq(queryId)));
  }

  public List<List<String>> getQueryResultIds(UUID queryId, int offset, int limit) {
    log.info("Retrieving results for queryId {}", queryId);
    List<List<String>> resultLists = readerJooqContext.select(RESULT_ID_FIELD)
      .from(QUERY_RESULTS_TABLE)
      .where(QUERY_ID_FIELD.eq(queryId))
      .orderBy(RESULT_ID_FIELD)
      .offset(offset)
      .limit(limit)
      .fetch()
      .map(Record1::value1)
      .stream()
      .map(Arrays::asList)
      .toList();
    log.debug("Retrieved result ids for queryId {}. Result ids: {}", queryId, resultLists);
    return resultLists;
  }

  public void deleteQueryResults(List<UUID> queryId) {
    log.info("Deleting query results for queryIds {}", queryId);
    jooqContext.deleteFrom(QUERY_RESULTS_TABLE)
      .where(QUERY_ID_FIELD.in(queryId))
      .execute();
  }
}
