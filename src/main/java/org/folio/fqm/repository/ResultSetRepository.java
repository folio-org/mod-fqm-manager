package org.folio.fqm.repository;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.jooq.impl.DSL.field;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.folio.fql.model.Fql;
import org.folio.fqm.exception.FieldNotFoundException;
import org.folio.fqm.service.EntityTypeFlatteningService;
import org.folio.fqm.service.FqlToSqlConverterService;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.fqm.utils.SqlFieldIdentificationUtils;
import org.folio.querytool.domain.dto.EntityDataType;
import org.folio.querytool.domain.dto.EntityType;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.jooq.Record;
import org.folio.spring.FolioExecutionContext;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Result;
import org.jooq.SelectConditionStep;
import org.jooq.SelectLimitPercentStep;
import org.jooq.impl.DSL;
import org.postgresql.jdbc.PgArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Repository
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ResultSetRepository {

  @Qualifier("readerJooqContext")
  private final DSLContext jooqContext;
  private final EntityTypeFlatteningService entityTypeFlatteningService;
  private final FolioExecutionContext executionContext;


  public List<Map<String, Object>> getResultSet(UUID entityTypeId,
                                                List<String> fields,
                                                List<List<String>> ids,
                                                List<String> tenantsToQuery) {
    if (CollectionUtils.isEmpty(fields)) {
      log.info("List of fields to retrieve is empty. Returning empty results list.");
      return List.of();
    }

    EntityType baseEntityType = getEntityType(null, entityTypeId);
    List<String> idColumnNames = EntityTypeUtils.getIdColumnNames(baseEntityType);

    SelectConditionStep<Record> query = null;
    for (int i = 0; i < tenantsToQuery.size(); i++) {
      String tenantId = tenantsToQuery.get(i);
      EntityType entityTypeDefinition = tenantId != null && tenantId.equals(executionContext.getTenantId()) ? baseEntityType : getEntityType(tenantId, entityTypeId);
      List<String> idColumnValueGetters = EntityTypeUtils.getIdColumnValueGetters(entityTypeDefinition);

      var currentFieldsToSelect = getSqlFields(entityTypeDefinition, fields);
      var currentFromClause = entityTypeFlatteningService.getJoinClause(entityTypeDefinition, tenantId);
      var currentWhereClause = buildWhereClause(entityTypeDefinition, ids, idColumnNames, idColumnValueGetters);
      if (i == 0) {
        query = jooqContext.select(currentFieldsToSelect)
          .from(currentFromClause)
          .where(currentWhereClause);
      } else {
        var partialQuery = jooqContext.select(currentFieldsToSelect)
          .from(currentFromClause)
          .where(currentWhereClause);
        query = (SelectConditionStep<Record>) query.unionAll(partialQuery);
      }
    }

    Result<Record> result;
    if (isEmpty(baseEntityType.getGroupByFields())) {
      result = query.fetch();
    } else {
      Field<?>[] groupByFields = baseEntityType
        .getColumns()
        .stream()
        .filter(col -> baseEntityType.getGroupByFields().contains(col.getName()))
        .map(org.folio.querytool.domain.dto.Field::getValueGetter)
        .map(DSL::field)
        .toArray(Field[]::new);
      result = query.groupBy(groupByFields).fetch();
    }

    return recordToMap(result);
  }

  // TODO: update for GROUP BY functionality
  public List<Map<String, Object>> getResultSetSync(UUID entityTypeId,
                                                    Fql fql,
                                                    List<String> fields,
                                                    List<String> afterId,
                                                    int limit,
                                                    List<String> tenantsToQuery,
                                                    boolean ecsEnabled) {
    if (CollectionUtils.isEmpty(fields)) {
      log.info("List of fields to retrieve is empty. Returning empty results list.");
      return List.of();
    }

    EntityType baseEntityType = getEntityType(null, entityTypeId);
    Field<String[]> idValueGetter = EntityTypeUtils.getResultIdValueGetter(baseEntityType);
    var sortCriteria = EntityTypeUtils.getSortFields(baseEntityType, true);
    Condition afterIdCondition;

    // TODO: this doesn't exactly work when tenant_id is used as an id column, look into this
    if (afterId != null) {
      String[] afterIdArray = afterId.toArray(new String[0]);
      afterIdCondition = field(idValueGetter).greaterThan(afterIdArray);
    } else {
      afterIdCondition = DSL.noCondition();
    }

    SelectConditionStep<Record> query = null;
    SelectLimitPercentStep<Record> fullQuery = null;
    for (int i = 0; i < tenantsToQuery.size(); i++) {
      // Below is a very hackish way to get around valueGetter issues in FqlToSqlConverterServiceIT
      // (due to the fact the that integration test does not select from an actual table, and instead creates a subquery
      // on the fly. Once the value getter for that test is handled better, then the ternary condition below can be removed
      String tenantId = tenantsToQuery.size() > 1 ? tenantsToQuery.get(i) : null;
      EntityType entityTypeDefinition = tenantId != null && tenantId.equals(executionContext.getTenantId()) ? baseEntityType : getEntityType(tenantId, entityTypeId);
      List<String> idColumnValueGetters = EntityTypeUtils.getIdColumnValueGetters(entityTypeDefinition);
      log.debug("idColumnValueGetters: {}", idColumnValueGetters);
      Condition currentCondition = FqlToSqlConverterService.getSqlCondition(fql.fqlCondition(), baseEntityType);
      if (ecsEnabled && !CollectionUtils.isEmpty(baseEntityType.getAdditionalEcsConditions())) {
        for (String condition : baseEntityType.getAdditionalEcsConditions()) {
          currentCondition = currentCondition.and(condition);
        }
      }
      var currentFieldsToSelect = getSqlFields(entityTypeDefinition, fields);
      var currentFromClause = entityTypeFlatteningService.getJoinClause(entityTypeDefinition, tenantId);
      if (i == 0) {
        query = jooqContext.select(currentFieldsToSelect)
          .from(currentFromClause)
          .where(currentCondition)
          .and(afterIdCondition);
      } else {
        var partialQuery = jooqContext.select(currentFieldsToSelect)
          .from(currentFromClause)
          .where(currentCondition)
          .and(afterIdCondition);
        query = (SelectConditionStep<Record>) query.unionAll(partialQuery);
      }
    }

    fullQuery = query
      .orderBy(sortCriteria)
      .limit(limit);

    Result<Record> result;
    if (isEmpty(baseEntityType.getGroupByFields())) {
      result = fullQuery.fetch();
    } else {
      throw new IllegalArgumentException("Synchronous queries are not currently supported for entity types with GROUP BY clauses.");
    }

    return recordToMap(result);
  }

  private List<Field<Object>> getSqlFields(EntityType entityType, List<String> fields) {
    Set<String> fieldSet = new HashSet<>(fields);
    return entityType.getColumns()
      .stream()
      .filter(col -> fieldSet.contains(col.getName()))
      .filter(col -> !Boolean.TRUE.equals(col.getQueryOnly()))
      .map(col -> SqlFieldIdentificationUtils.getSqlResultsField(col).as(col.getName()))
      .toList();
  }

  private EntityType getEntityType(String tenantId, UUID entityTypeId) {
    return entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, tenantId);
  }

  private List<Map<String, Object>> recordToMap(Result<Record> result) {
    List<Map<String, Object>> resultList = new ArrayList<>();
    result.forEach(row -> {
      Map<String, Object> recordMap = row.intoMap();
      for (Map.Entry<String, Object> entry : recordMap.entrySet()) {
        if (entry.getValue() instanceof PgArray pgArray) {
          try {
            recordMap.put(entry.getKey(), pgArray.getArray());
          } catch (SQLException e) {
            log.error("Error unpacking {} field of record: {}", entry.getKey(), e);
            recordMap.remove(entry.getKey());
          }
        }
      }
      resultList.add(recordMap);
    });

    return resultList;
  }

  private Condition buildWhereClause(EntityType entityType, List<List<String>> ids, List<String> idColumnNames, List<String> idColumnValueGetters) {
    Condition whereClause = DSL.noCondition();
    for (int i = 0; i < idColumnValueGetters.size(); i++) {
      List<String> idColumnValues = new ArrayList<>();
      for (List<String> idList : ids) {
        idColumnValues.add(idList.get(i));
      }
      String idColumnName = idColumnNames.get(i);
      String idColumnValueGetter = idColumnValueGetters.get(i);
      String columnDataType = entityType
        .getColumns()
        .stream()
        .filter(col -> col.getName().equals(idColumnName))
        .map(EntityTypeColumn::getDataType)
        .map(EntityDataType::getDataType)
        .findFirst()
        .orElseThrow(() -> new FieldNotFoundException(entityType.getName(), idColumnName));
      if (columnDataType.equals("rangedUUIDType") || columnDataType.equals("openUUIDType")) {
        List<UUID> idColumnValuesAsUUIDs = idColumnValues
          .stream()
          .map(val -> val != null ? UUID.fromString(val) : null)
          .toList();
        whereClause = whereClause.and(field(idColumnValueGetter).in(idColumnValuesAsUUIDs));
      } else {
        whereClause = whereClause.and(field(idColumnValueGetter).in(idColumnValues));
      }
    }

    return whereClause;
  }
}
