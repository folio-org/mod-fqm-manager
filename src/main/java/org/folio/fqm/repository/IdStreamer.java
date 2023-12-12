package org.folio.fqm.repository;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.exception.ColumnNotFoundException;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.service.DerivedTableIdentificationService;
import org.folio.fqm.service.FqlToSqlConverterService;
import org.folio.fqm.utils.StreamHelper;
import org.folio.fql.model.Fql;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeDefaultSort;
import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.SortField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.jooq.Record1;
import org.jooq.Field;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.folio.fqm.repository.EntityTypeRepository.ID_FIELD_NAME;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.table;

@Repository
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class IdStreamer {
  private static final String RESULT_ID = "result_id";

  private final DSLContext jooqContext;
  private final EntityTypeRepository entityTypeRepository;
  private final DerivedTableIdentificationService derivedTableIdentifier;
  private final QueryDetailsRepository queryDetailsRepository;

  /**
   * Executes the given Fql Query and stream the result Ids back.
   */
  public int streamIdsInBatch(UUID entityTypeId,
                              boolean sortResults,
                              Fql fql,
                              int batchSize,
                              Consumer<IdsWithCancelCallback> idsConsumer) {
    EntityType entityType = getEntityType(entityTypeId);
    Condition sqlWhereClause = FqlToSqlConverterService.getSqlCondition(fql.fqlCondition(), entityType);
    return this.streamIdsInBatch(entityType, sortResults, sqlWhereClause, batchSize, idsConsumer);
  }

  /**
   * Streams the result Ids of the given queryId
   */
  public int streamIdsInBatch(UUID queryId,
                              boolean sortResults,
                              int batchSize,
                              Consumer<IdsWithCancelCallback> idsConsumer) {
    UUID entityTypeId = queryDetailsRepository.getEntityTypeId(queryId);
    EntityType entityType = getEntityType(entityTypeId);
    Condition condition = field(ID_FIELD_NAME).in(
      select(field(RESULT_ID))
        .from(table("query_results"))
        .where(field("query_id").eq(queryId))
    );
    return streamIdsInBatch(entityType, sortResults, condition, batchSize, idsConsumer);
  }

  public List<UUID> getSortedIds(String derivedTableName,
                                  int offset, int batchSize, UUID queryId) {
    // THIS DOES NOT PROVIDE SORTED IDs! This is a temporary workaround to address performance issues until we
    // can do it properly
    return jooqContext.dsl()
      .select(field(RESULT_ID))
      // NOTE: derivedTableName is <schema>.query_results here as part of the workaround
      .from(table(derivedTableName))
      .where(field("query_id").eq(queryId))
      .orderBy(field(RESULT_ID))
      .offset(offset)
      .limit(batchSize)
      .fetchInto(UUID.class);
    }

  private int streamIdsInBatch(EntityType entityType,
                               boolean sortResults,
                               Condition sqlWhereClause,
                               int batchSize,
                               Consumer<IdsWithCancelCallback> idsConsumer) {
    return jooqContext.transactionResult(ctx -> {
      String idValueGetter = entityType
        .getColumns()
        .stream()
        .filter(col -> ID_FIELD_NAME.equals(col.getName()))
        .map(col -> col.getValueGetter() != null ? col.getValueGetter() : col.getName())
        .findFirst()
        .orElseThrow(() -> new ColumnNotFoundException(entityType.getName(), ID_FIELD_NAME));
      try (
        Cursor<Record1<Object>> idsCursor = ctx.dsl()
          .select(field(idValueGetter))
          .from(entityType.getFromClause())
          .where(sqlWhereClause)
          .orderBy(getSortFields(entityType, sortResults))
          .fetchSize(batchSize)
          .fetchLazy();
        Stream<UUID> uuidStream = idsCursor
          .stream()
          .map(row -> (UUID) row.getValue(idValueGetter));
        Stream<List<UUID>> idsStream = StreamHelper.chunk(uuidStream, batchSize)
      ) {
        var total = new AtomicInteger();
        idsStream.map(ids -> new IdsWithCancelCallback(ids, idsStream::close))
                 .forEach(idsWithCancelCallback -> {
                   idsConsumer.accept(idsWithCancelCallback);
                   total.addAndGet(idsWithCancelCallback.ids().size());
                 });
        return total.get();
      }
    });
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

  private EntityType getEntityType(UUID entityTypeId) {
    return entityTypeRepository.getEntityTypeDefinition(entityTypeId)
      .orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId));
  }
}
