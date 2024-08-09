package org.folio.fqm.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.service.CrossTenantQueryService;
import org.folio.fqm.service.EntityTypeFlatteningService;
import org.folio.fqm.service.FqlToSqlConverterService;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.fqm.utils.StreamHelper;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.folio.fqm.utils.EntityTypeUtils.RESULT_ID_FIELD;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Log4j2
public class IdStreamer {

  @Qualifier("readerJooqContext")
  private final DSLContext jooqContext;
  private final EntityTypeFlatteningService entityTypeFlatteningService;
  private final CrossTenantQueryService crossTenantQueryService;
  private final FolioExecutionContext executionContext;

  /**
   * Executes the given Fql Query and stream the result Ids back.
   */
  public int streamIdsInBatch(EntityType entityType,
                              boolean sortResults,
                              Fql fql,
                              int batchSize,
                              Consumer<IdsWithCancelCallback> idsConsumer) {
    List<String> tenantsToQuery = crossTenantQueryService.getTenantsToQuery(entityType);
    return this.streamIdsInBatch(entityType, sortResults, fql, batchSize, idsConsumer, tenantsToQuery);
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
                               Fql fql, int batchSize,
                               Consumer<IdsWithCancelCallback> idsConsumer, List<String> tenantsToQuery) {
    UUID entityTypeId = UUID.fromString(entityType.getId());
    log.debug("List of tenants to query: {}", tenantsToQuery);
    Field<String[]> idValueGetter = EntityTypeUtils.getResultIdValueGetter(entityType);
    Select<Record1<String[]>> fullQuery = null;
    for (String tenantId : tenantsToQuery) {
      EntityType entityTypeDefinition = tenantId != null && tenantId.equals(executionContext.getTenantId()) ?
        entityType : entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, tenantId);
      var currentIdValueGetter = EntityTypeUtils.getResultIdValueGetter(entityTypeDefinition);
      String innerJoinClause = entityTypeFlatteningService.getJoinClause(entityTypeDefinition, tenantId);
      Condition whereClause = FqlToSqlConverterService.getSqlCondition(fql.fqlCondition(), entityTypeDefinition);
      ResultQuery<Record1<String[]>> innerQuery = buildQuery(
        entityTypeDefinition,
        currentIdValueGetter,
        innerJoinClause,
        whereClause,
        sortResults,
        batchSize
      );
      if (fullQuery == null) {
        fullQuery = (Select<Record1<String[]>>) innerQuery;
      } else {
        fullQuery = fullQuery.unionAll((Select<Record1<String[]>>) innerQuery);
      }
    }
    log.debug("Full query: {}", fullQuery);

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
        .forEach(idsWithCancelCallback -> {
          idsConsumer.accept(idsWithCancelCallback);
          total.addAndGet(idsWithCancelCallback.ids().size());
        });
      return total.get();
    }
  }

  private ResultQuery<Record1<String[]>> buildQuery(EntityType entityType, Field<String[]> idValueGetter, String finalJoinClause, Condition sqlWhereClause, boolean sortResults, int batchSize) {
    if (!isEmpty(entityType.getGroupByFields())) {
      Field<?>[] groupByFields = entityType
        .getColumns()
        .stream()
        .filter(col -> entityType.getGroupByFields().contains(col.getName()))
        .map(col -> col.getFilterValueGetter() == null ? col.getValueGetter() : col.getFilterValueGetter())
        .map(DSL::field)
        .toArray(Field[]::new);
      return jooqContext.dsl()
        .select(field(idValueGetter))
        .from(finalJoinClause)
        .where(sqlWhereClause)
        .groupBy(groupByFields)
        .orderBy(EntityTypeUtils.getSortFields(entityType, sortResults))
        .fetchSize(batchSize);
    } else {
      return jooqContext.dsl()
        .select(field(idValueGetter))
        .from(finalJoinClause)
        .where(sqlWhereClause)
        .orderBy(EntityTypeUtils.getSortFields(entityType, sortResults))
        .fetchSize(batchSize);
    }
  }
}
