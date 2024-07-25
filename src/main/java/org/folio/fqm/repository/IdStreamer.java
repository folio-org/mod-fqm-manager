package org.folio.fqm.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.service.EntityTypeFlatteningService;
import org.folio.fqm.service.FqlToSqlConverterService;
import org.folio.fqm.utils.IdColumnUtils;
import org.folio.fqm.utils.StreamHelper;
import org.folio.fql.model.Fql;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeDefaultSort;
import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.ResultQuery;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.jooq.Record1;
import org.jooq.Field;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.folio.fqm.utils.IdColumnUtils.RESULT_ID_FIELD;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Log4j2
public class IdStreamer {

  @Qualifier("readerJooqContext")
  private final DSLContext jooqContext;
  private final EntityTypeFlatteningService entityTypeFlatteningService;

  /**
   * Executes the given Fql Query and stream the result Ids back.
   */
  public int streamIdsInBatch(EntityType entityType,
                              boolean sortResults,
                              Fql fql,
                              int batchSize,
                              Consumer<IdsWithCancelCallback> idsConsumer) {
    Condition sqlWhereClause = FqlToSqlConverterService.getSqlCondition(fql.fqlCondition(), entityType);
    return this.streamIdsInBatch(entityType, sortResults, sqlWhereClause, batchSize, idsConsumer);
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
      .fetch()
      .map(Record1::value1)
      .stream()
      .map(Arrays::asList)
      .toList();
  }

  private int streamIdsInBatch(EntityType entityType,
                               boolean sortResults,
                               Condition sqlWhereClause,
                               int batchSize,
                               Consumer<IdsWithCancelCallback> idsConsumer) {
    String finalJoinClause = entityTypeFlatteningService.getJoinClause(entityType);
    Field<String[]> idValueGetter = IdColumnUtils.getResultIdValueGetter(entityType);
    ResultQuery<Record1<String[]>> query = null;
    if (!isEmpty(entityType.getGroupByFields())) {
      Field<?>[] groupByFields = entityType
        .getColumns()
        .stream()
        .filter(col -> entityType.getGroupByFields().contains(col.getName()))
        .map(col -> col.getFilterValueGetter() == null ? col.getValueGetter() : col.getFilterValueGetter())
        .map(DSL::field)
        .toArray(Field[]::new);
      query = jooqContext.dsl()
        .select(field(idValueGetter))
        .from(finalJoinClause)
        .where(sqlWhereClause)
        .groupBy(groupByFields)
        .orderBy(getSortFields(entityType, sortResults))
        .fetchSize(batchSize);
    } else {
      query = jooqContext.dsl()
        .select(field(idValueGetter))
        .from(finalJoinClause)
        .where(sqlWhereClause)
        .orderBy(getSortFields(entityType, sortResults))
        .fetchSize(batchSize);
    }

    try (
      Cursor<Record1<String[]>> idsCursor = query.fetchLazy();
      Stream<String[]> idStream = idsCursor
        .stream()
        .map(row -> row.getValue(idValueGetter));
      Stream<List<String[]>> idsStream = StreamHelper.chunk(idStream, batchSize)
    ) {
      var total = new AtomicInteger();
      idsStream.map(ids -> new IdsWithCancelCallback(ids, idsStream::close))
        .forEach(idsWithCancelCallback -> {
          idsConsumer.accept(idsWithCancelCallback);
          total.addAndGet(idsWithCancelCallback.ids().size());
        });
      return total.get();
    }
  }

  private List<SortField<Object>> getSortFields(EntityType entityType, boolean sortResults) {
    if (sortResults && !isEmpty(entityType.getDefaultSort())) {
      return entityType
        .getDefaultSort()
        .stream()
        .map(IdStreamer::toSortField)
        .toList();
    }
    return List.of();
  }

  private static SortField<Object> toSortField(EntityTypeDefaultSort entityTypeDefaultSort) {
    Field<Object> field = field(entityTypeDefaultSort.getColumnName());
    return entityTypeDefaultSort.getDirection() == EntityTypeDefaultSort.DirectionEnum.DESC ? field.desc() : field.asc();
  }
}
