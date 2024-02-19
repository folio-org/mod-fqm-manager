package org.folio.fqm.repository;

import static org.folio.fqm.repository.EntityTypeRepository.ID_FIELD_NAME;
import static org.jooq.impl.DSL.field;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.folio.fql.model.Fql;
import org.folio.fqm.exception.ColumnNotFoundException;
import org.folio.fqm.exception.EntityTypeNotFoundException;
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
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Repository
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ResultSetRepository {

  private final DSLContext jooqContext;
  private final EntityTypeRepository entityTypeRepository;

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
        .orElseThrow(() -> new ColumnNotFoundException(entityType.getName(), idColumnName));
      if (columnDataType.equals("rangedUUIDType") || columnDataType.equals("openUUIDType")) {
        List<UUID> idColumnValuesAsUUIDs = idColumnValues
          .stream()
          .map(UUID::fromString)
          .toList();
        whereClause = whereClause.and(field(idColumnValueGetter).in(idColumnValuesAsUUIDs));
      } else {
        whereClause = whereClause.and(field(idColumnValueGetter).in(idColumnValues));
      }
    }
    var result = jooqContext.select(fieldsToSelect)
      .from(entityType.getFromClause())
      .where(whereClause)
      .fetch();
    return recordToMap(result);
  }

  public List<Map<String, Object>> getResultSet(UUID entityTypeId, Fql fql, List<String> fields, List<String> afterId, int limit) {
    if (CollectionUtils.isEmpty(fields)) {
      log.info("List of fields to retrieve is empty. Returning empty results list.");
      return List.of();
    }
    EntityType entityType = getEntityType(entityTypeId);
    List<String> idColumnNames = IdColumnUtils.getIdColumnNames(entityType);
    Field<String[]> idValueGetter = IdColumnUtils.getResultIdValueGetter(entityType);
    Condition afterIdCondition;
    if (afterId != null) {
      String[] afterIdArray = afterId.toArray(new String[0]);
      afterIdCondition = field(idValueGetter).greaterThan(afterIdArray);
    } else {
      afterIdCondition = DSL.noCondition();
    }
    // Make sure idColumns are included in results
    for (String idColumnName : idColumnNames) {
      if (!fields.contains(idColumnName)) {
        fields.add(idColumnName);
      }
    }

    Condition condition = FqlToSqlConverterService.getSqlCondition(fql.fqlCondition(), entityType);
    var fieldsToSelect = getSqlFields(entityType, fields);
    var sortCriteria = hasIdColumn(entityType) ? field(ID_FIELD_NAME) : DSL.noField();
    var result = jooqContext.select(fieldsToSelect)
      .from(entityType.getFromClause())
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
      .map(col -> SqlFieldIdentificationUtils.getSqlResultsField(col).as(col.getName()))
      .toList();
  }

  private EntityType getEntityType(UUID entityTypeId) {
    return entityTypeRepository.getEntityTypeDefinition(entityTypeId)
      .orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId));
  }

  private boolean hasIdColumn(EntityType entityType) {
    return entityType.getColumns().stream()
      .anyMatch(col -> ID_FIELD_NAME.equals(col.getName()));
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
