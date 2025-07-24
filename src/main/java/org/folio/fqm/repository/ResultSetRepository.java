package org.folio.fqm.repository;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.jooq.impl.DSL.field;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.folio.fql.model.Fql;
import org.folio.fqm.exception.FieldNotFoundException;
import org.folio.fqm.service.EntityTypeFlatteningService;
import org.folio.fqm.service.FqlToSqlConverterService;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.fqm.utils.SqlFieldIdentificationUtils;
import org.folio.fqm.utils.flattening.FromClauseUtils;
import org.folio.querytool.domain.dto.EntityDataType;
import org.folio.querytool.domain.dto.EntityType;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.JsonbArrayType;
import org.jooq.*;
import org.folio.spring.FolioExecutionContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.postgresql.jdbc.PgArray;
import org.postgresql.util.PGobject;
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
  private final ObjectMapper objectMapper;


  public List<Map<String, Object>> getResultSet(UUID entityTypeId,
                                                List<String> fields,
                                                List<List<String>> ids,
                                                List<String> tenantsToQuery) {
    if (CollectionUtils.isEmpty(fields)) {
      log.info("List of fields to retrieve is empty. Returning empty results list.");
      return List.of();
    }
    if (CollectionUtils.isEmpty(ids)) {
      return List.of();
    }

    EntityType baseEntityType = getEntityType(executionContext.getTenantId(), entityTypeId);
    List<String> idColumnNames = EntityTypeUtils.getIdColumnNames(baseEntityType);

    SelectConditionStep<Record> query = null;
    for (int i = 0; i < tenantsToQuery.size(); i++) {
      String tenantId = tenantsToQuery.get(i);
      EntityType entityTypeDefinition = tenantId != null && tenantId.equals(executionContext.getTenantId()) ? baseEntityType : getEntityType(tenantId, entityTypeId);
      List<String> idColumnValueGetters = EntityTypeUtils.getIdColumnValueGetters(entityTypeDefinition);

      // We may have joins to columns which are filtered out via essentialOnly/etc. Therefore, we must re-fetch
      // the entity type with all columns preserved to build the from clause. However, we do not want to only
      // use this version, though, as we want to ensure excluded columns are not used in queries. so we need both.
      EntityType entityTypeDefinitionWithAllFields = entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, tenantId, true);
      String currentFromClause = FromClauseUtils.getFromClause(entityTypeDefinitionWithAllFields, tenantId);

      var currentFieldsToSelect = getSqlFields(entityTypeDefinition, fields);
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

    return recordToMap(baseEntityType, result);
  }

  // TODO: update for GROUP BY functionality
  public List<Map<String, Object>> getResultSetSync(UUID entityTypeId,
                                                    Fql fql,
                                                    List<String> fields,
                                                    int limit,
                                                    List<String> tenantsToQuery,
                                                    boolean ecsEnabled) {
    if (CollectionUtils.isEmpty(fields)) {
      log.info("List of fields to retrieve is empty. Returning empty results list.");
      return List.of();
    }

    EntityType baseEntityType = getEntityType(executionContext.getTenantId(), entityTypeId);
    List<String> idColumnNames = EntityTypeUtils.getIdColumnNames(baseEntityType);
    List<Select<Record>> partialQueries = new ArrayList<>();

    for (int i = 0; i < tenantsToQuery.size(); i++) {
      // Below is a very hackish way to get around valueGetter issues in FqlToSqlConverterServiceIT
      // (due to the fact the that integration test does not select from an actual table, and instead creates a subquery
      // on the fly. Once the value getter for that test is handled better, then the ternary condition below can be removed
      String tenantId = tenantsToQuery.size() > 1 ? tenantsToQuery.get(i) : executionContext.getTenantId();
      EntityType entityTypeDefinition = tenantId != null && tenantId.equals(executionContext.getTenantId()) ? baseEntityType : getEntityType(tenantId, entityTypeId);
      Condition currentCondition = FqlToSqlConverterService.getSqlCondition(fql.fqlCondition(), baseEntityType);

      if (!CollectionUtils.isEmpty(baseEntityType.getFilterConditions())) {
        for (String condition : baseEntityType.getFilterConditions()) {
          currentCondition = currentCondition.and(condition);
        }
      }
      if (ecsEnabled && !CollectionUtils.isEmpty(baseEntityType.getAdditionalEcsConditions())) {
        for (String condition : baseEntityType.getAdditionalEcsConditions()) {
          currentCondition = currentCondition.and(condition);
        }
      }

      var currentFieldsToSelect = getSqlFields(entityTypeDefinition, fields);

      // We may have joins to columns which are filtered out via essentialOnly/etc. Therefore, we must re-fetch
      // the entity type with all columns preserved to build the from clause. However, we do not want to only
      // use this version, though, as we want to ensure excluded columns are not used in queries. so we need both.
      EntityType entityTypeDefinitionWithAllFields = entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, tenantId, true);
      String currentFromClause = FromClauseUtils.getFromClause(entityTypeDefinitionWithAllFields, tenantId);

      Select<Record> partialQuery = jooqContext.select(currentFieldsToSelect)
        .from(currentFromClause)
        .where(currentCondition);
      partialQueries.add(partialQuery);
    }

    if (partialQueries.isEmpty()) {
      return List.of();
    }

    String unionFormatter = "\"unioned\".\"%s\"";
    List<Field<Object>> sortCriteria = idColumnNames.stream()
      .map(f -> DSL.field(String.format(unionFormatter, f)))
      .toList();

    Select<Record> unionQuery = partialQueries.stream()
      .reduce(Select::unionAll)
      .orElseThrow();
    Table<?> unionTable = unionQuery.asTable("unioned");
    var limitedQuery = jooqContext.selectFrom(unionTable)
      .orderBy(sortCriteria)
      .limit(limit);

    Result<Record> result;
    if (isEmpty(baseEntityType.getGroupByFields())) {
      result = (Result<Record>) limitedQuery.fetch();
    } else {
      throw new IllegalArgumentException("Synchronous queries are not currently supported for entity types with GROUP BY clauses.");
    }

    return recordToMap(baseEntityType, result);
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
    return entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, tenantId, false);
  }

  private List<Map<String, Object>> recordToMap(EntityType entityType, Result<Record> result) {
    List<Map<String, Object>> resultList = new ArrayList<>();
    Set<String> jsonbArrayColumnsInEntityType = entityType.getColumns()
      .stream()
      .filter(col -> col.getDataType() instanceof JsonbArrayType)
      .map(org.folio.querytool.domain.dto.Field::getName)
      .collect(Collectors.toSet());
    Set<String> jsonbArrayColumnsInResults = result.isEmpty() ? Collections.emptySet() : result.get(0).intoMap()
      .keySet()
      .stream()
      .filter(jsonbArrayColumnsInEntityType::contains)
      .collect(Collectors.toSet());
    result.forEach(row -> {
      Map<String, Object> recordMap = row.intoMap();
      for (Map.Entry<String, Object> entry : recordMap.entrySet()) {
        // Turn SQL arrays into Java arrays
        if (entry.getValue() instanceof PgArray pgArray) {
          try {
            recordMap.put(entry.getKey(), pgArray.getArray());
          } catch (SQLException e) {
            log.error("Error unpacking {} field of record: {}", entry.getKey(), e);
            recordMap.remove(entry.getKey());
          }
        }
        if (jsonbArrayColumnsInResults.contains(entry.getKey()) && entry.getValue() instanceof PGobject pgobject && "jsonb".equals(pgobject.getType())) {
          try {
            JSONB jsonb = JSONB.valueOf(pgobject.getValue());
            recordMap.put(entry.getKey(), objectMapper.readValue(jsonb.toString(), String[].class));
          } catch (Exception e) {
            log.error("Failed to parse jsonb value: {}", e.getMessage(), e);
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
      Condition idFilterCondition;
      if (columnDataType.equals("rangedUUIDType") || columnDataType.equals("openUUIDType")) {
        List<UUID> idColumnValuesAsUUIDs = idColumnValues
          .stream()
          .map(val -> val != null ? UUID.fromString(val) : null)
          .toList();
        idFilterCondition = field(idColumnValueGetter).in(idColumnValuesAsUUIDs);
        if (idColumnValuesAsUUIDs.contains(null)) {
          idFilterCondition = idFilterCondition.or(field(idColumnValueGetter).isNull());
        }
      } else {
        idFilterCondition = field(idColumnValueGetter).in(idColumnValues);
        if (idColumnValues.contains(null)) {
          idFilterCondition = idFilterCondition.or(field(idColumnValueGetter).isNull());
        }
      }
      whereClause = whereClause.and(idFilterCondition);
    }

    return whereClause;
  }
}
