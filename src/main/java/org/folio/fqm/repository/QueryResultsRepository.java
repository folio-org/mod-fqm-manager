package org.folio.fqm.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record3;
import org.jooq.Table;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.field;

@Repository
@RequiredArgsConstructor
@Log4j2
public class QueryResultsRepository {
  private static final Table<Record> QUERY_RESULTS_TABLE = table("query_results");
  private static final Field<UUID> QUERY_ID_FIELD = field("query_id", UUID.class);
  private static final Field<UUID> RESULT_ID_FIELD = field("result_id", UUID.class);
  private static final Field<Integer> SORT_SEQ_FIELD = field("sort_seq", Integer.class);

  private final DSLContext jooqContext;

  public void saveQueryResults(UUID queryId, List<UUID> resultIds, int sortSequence) {
    log.info("Received data batch for queryId {}", queryId);
    log.debug("Data batch: {}", resultIds);
    AtomicInteger sequenceOffset = new AtomicInteger(0);
    List<Record3<UUID, UUID, Integer>> records = resultIds.stream()
      .map(resultId -> jooqContext.newRecord(QUERY_ID_FIELD, RESULT_ID_FIELD, SORT_SEQ_FIELD)
                                  .values(queryId, resultId, sortSequence + sequenceOffset.getAndAdd(1))
      )
      .toList();
    jooqContext.insertInto(QUERY_RESULTS_TABLE, QUERY_ID_FIELD, RESULT_ID_FIELD, SORT_SEQ_FIELD)
      .valuesOfRecords(records)
      .execute();
  }

  public int getQueryResultsCount(UUID queryId) {
    log.info("Retrieving result count for queryId {}", queryId);
    return jooqContext.fetchCount(QUERY_RESULTS_TABLE.where(QUERY_ID_FIELD.eq(queryId)));
  }

  public List<UUID> getQueryResultIds(UUID queryId, int offset, int limit) {
    log.info("Retrieving results for queryId {}", queryId);
    List<UUID> resultIds = jooqContext.select(RESULT_ID_FIELD)
      .from(QUERY_RESULTS_TABLE)
      .where(QUERY_ID_FIELD.eq(queryId))
      .orderBy(RESULT_ID_FIELD)
      .offset(offset)
      .limit(limit)
      .fetchInto(UUID.class);
    log.debug("Retrieved result ids for queryId {}. Result ids: {}", queryId, resultIds);
    return resultIds;
  }

  public void deleteQueryResults(List<UUID> queryId) {
    log.info("Deleting query results for queryIds {}", queryId);
    jooqContext.deleteFrom(QUERY_RESULTS_TABLE)
      .where(QUERY_ID_FIELD.in(queryId))
      .execute();
  }
}
