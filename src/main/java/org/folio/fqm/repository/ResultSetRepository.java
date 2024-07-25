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
import org.folio.fqm.utils.IdColumnUtils;
import org.folio.fqm.utils.SqlFieldIdentificationUtils;
import org.folio.querytool.domain.dto.EntityDataType;
import org.folio.querytool.domain.dto.EntityType;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
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

  @Qualifier("readerJooqContext") private final DSLContext jooqContext;
  private final EntityTypeFlatteningService entityTypeFlatteningService;

  public List<Map<String, Object>> getResultSet(UUID entityTypeId,
                                                List<String> fields,
                                                List<List<String>> ids) {
    if (CollectionUtils.isEmpty(fields)) {
      log.info("List of fields to retrieve is empty. Returning empty results list.");
      return List.of();
    }
    EntityType entityType = getEntityType(entityTypeId);
    var fieldsToSelect = getSqlFields(entityType, fields);
    List<String> idColumnNames = IdColumnUtils.getIdColumnNames(entityType);
    List<String> idColumnValueGetters = IdColumnUtils.getIdColumnValueGetters(entityType);
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
          // TODO: the below check keeps things just-about working, but still leads to records being counted as "deleted" if one of their id columns is null. Need to handle this better.
          .map(val -> val != null ? UUID.fromString(val) : null)
          .toList();
        whereClause = whereClause.and(field(idColumnValueGetter).in(idColumnValuesAsUUIDs));
      } else {
        whereClause = whereClause.and(field(idColumnValueGetter).in(idColumnValues));
      }
    }

    String fromClause = entityTypeFlatteningService.getJoinClause(entityType);
    var query = jooqContext.select(fieldsToSelect)
      .from(fromClause)
      .where(whereClause);

    Result<Record> result;
    if (isEmpty(entityType.getGroupByFields())) {
      result = query.fetch();
    } else {
      Field<?>[] groupByFields = entityType
        .getColumns()
        .stream()
        .filter(col -> entityType.getGroupByFields().contains(col.getName()))
        .map(org.folio.querytool.domain.dto.Field::getValueGetter)
        .map(DSL::field)
        .toArray(Field[]::new);
      result = query.groupBy(groupByFields).fetch();
    }

    return recordToMap(result);
  }

  // TODO: update for GROUP BY functionality
  public List<Map<String, Object>> getResultSet(UUID entityTypeId, Fql fql, List<String> fields, List<String> afterId, int limit) {
    if (CollectionUtils.isEmpty(fields)) {
      log.info("List of fields to retrieve is empty. Returning empty results list.");
      return List.of();
    }
    EntityType entityType = getEntityType(entityTypeId);
    Field<String[]> idValueGetter = IdColumnUtils.getResultIdValueGetter(entityType);
    Condition afterIdCondition;
    if (afterId != null) {
      String[] afterIdArray = afterId.toArray(new String[0]);
      afterIdCondition = field(idValueGetter).greaterThan(afterIdArray);
    } else {
      afterIdCondition = DSL.noCondition();
    }

    Condition condition = FqlToSqlConverterService.getSqlCondition(fql.fqlCondition(), entityType);
    var fieldsToSelect = getSqlFields(entityType, fields);
    String idColumnName = entityType
      .getColumns()
      .stream()
      .filter(EntityTypeColumn::getIsIdColumn)
      .findFirst()
      .orElseThrow()
      .getName();
    var sortCriteria = hasIdColumn(entityType) ? field(idColumnName) : DSL.noField(); // TODO: new changes break sorting
    String fromClause = entityTypeFlatteningService.getJoinClause(entityType);
    var result = jooqContext.select(fieldsToSelect)
      .from(fromClause)
      .where(condition)
      .and(afterIdCondition)
      .orderBy(sortCriteria)
      .limit(limit)
      .fetch();
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

  private EntityType getEntityType(UUID entityTypeId) {
    return entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, true);
  }

  private boolean hasIdColumn(EntityType entityType) {
    return entityType.getColumns().stream()
      .anyMatch(col -> col.getIsIdColumn());
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
}
