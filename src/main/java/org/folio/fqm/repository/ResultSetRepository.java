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
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.service.FqlToSqlConverterService;
import org.folio.fqm.utils.SqlFieldIdentificationUtils;
import org.folio.querytool.domain.dto.EntityType;

import org.apache.commons.collections4.CollectionUtils;
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
  private final FqlToSqlConverterService fqlToSqlConverter;

  public List<Map<String, Object>> getResultSet(UUID entityTypeId,
                                                List<String> fields,
                                                List<UUID> ids) {
    if (CollectionUtils.isEmpty(fields)) {
      log.info("List of fields to retrieve is empty. Returning empty results list.");
      return List.of();
    }
    var fieldsToSelect = getSqlFields(getEntityType(entityTypeId), fields);
    var result = jooqContext.select(fieldsToSelect)
      .from(entityTypeRepository.getDerivedTableName(entityTypeId)
        .orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId)))
      .where(field(ID_FIELD_NAME).in(ids))
      .fetch();
    return recordToMap(result);
  }

  public List<Map<String, Object>> getResultSet(UUID entityTypeId, Fql fql, List<String> fields, UUID afterId, int limit) {
    if (CollectionUtils.isEmpty(fields)) {
      log.info("List of fields to retrieve is empty. Returning empty results list.");
      return List.of();
    }
    Condition afterIdCondition = afterId != null ? field(ID_FIELD_NAME).greaterThan(afterId) : DSL.noCondition();
    EntityType entityType = getEntityType(entityTypeId);
    Condition condition = fqlToSqlConverter.getSqlCondition(fql.fqlCondition(), entityType);
    var fieldsToSelect = getSqlFields(entityType, fields);
    var sortCriteria = hasIdColumn(entityType) ? field(ID_FIELD_NAME) : DSL.noField();
    var result = jooqContext.select(fieldsToSelect)
      .from(entityTypeRepository.getDerivedTableName(entityTypeId)
        .orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId)))
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
      .map(col -> SqlFieldIdentificationUtils.getSqlField(col).as(col.getName()))
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
