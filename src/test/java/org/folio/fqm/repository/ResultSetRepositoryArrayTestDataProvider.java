package org.folio.fqm.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import org.folio.querytool.domain.dto.EntityDataType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.RangedUUIDType;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.mockito.Mockito;
import org.postgresql.jdbc.PgArray;
import org.postgresql.util.PGobject;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.folio.fqm.repository.EntityTypeRepository.ID_FIELD_NAME;
import static org.jooq.impl.DSL.field;
import static org.mockito.Mockito.when;

/**
 * Mock data provider that returns query results for Repository tests.
 */
public class ResultSetRepositoryArrayTestDataProvider implements MockDataProvider {
  public static final List<Map<String, Object>> TEST_ENTITY_CONTENTS = List.of(
    Map.of(ID_FIELD_NAME, UUID.randomUUID(), "key1", "value1", "key2", "value2"),
    Map.of(ID_FIELD_NAME, UUID.randomUUID(), "key1", "value3", "key2", "value4"),
    Map.of(ID_FIELD_NAME, UUID.randomUUID(), "key1", "value5", "key2", "value6")
  );

  public static final List<Map<String, Object>> TEST_ENTITY_WITH_ARRAY_CONTENTS = List.of(
    Map.of(ID_FIELD_NAME, UUID.randomUUID(), "testField", getPgArray(), "testJsonbArrayField", getJsonbArray()));

  public static final EntityType ARRAY_ENTITY_TYPE = new EntityType()
    .columns(List.of(
      new EntityTypeColumn()
        .name(ID_FIELD_NAME)
        .dataType(new RangedUUIDType().dataType("rangedUUIDType"))
        .valueGetter(ID_FIELD_NAME)
        .isIdColumn(true)
        .sourceAlias("source1"),
      new EntityTypeColumn()
        .name("testField")
        .dataType(new EntityDataType().dataType("arrayType"))
        .sourceAlias("source1")
        .valueGetter(":sourceAlias.testField"),
      new EntityTypeColumn()
        .name("testJsonbArrayField")
        .dataType(new EntityDataType().dataType("jsonbArrayType"))
        .sourceAlias("source1")
        .valueGetter(":sourceAlias.testJsonbArrayField")
    ))
    .name("TEST_ARRAY_ENTITY_TYPE")
    .fromClause("TEST_ARRAY_ENTITY_TYPE")
    .sources(List.of(
      new EntityTypeSourceDatabase()
        .type("db")
        .alias("source1")
        .target("target1")
      )
    );

  private static final String DERIVED_TABLE_NAME_QUERY_REGEX = "SELECT DERIVED_TABLE_NAME FROM ENTITY_TYPE_DEFINITION WHERE ID = .*";
  private static final String LIST_CONTENTS_BY_IDS_WITH_ARRAY_REGEX = "SELECT .* FROM .* WHERE ID IN .*";
  private static final String ENTITY_TYPE_DEFINITION_WITH_ARRAY_REGEX = "SELECT DEFINITION FROM ENTITY_TYPE_DEFINITION WHERE ID = .*";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  public MockResult[] execute(MockExecuteContext ctx) {
    DSLContext create = DSL.using(SQLDialect.POSTGRES);
    MockResult mockResult = new MockResult();

    String sql = ctx.sql().toUpperCase();

    if (sql.matches(DERIVED_TABLE_NAME_QUERY_REGEX)) {
      var derivedTableNameField = field("derived_table_name");
      Result<Record1<Object>> result = create.newResult(derivedTableNameField);
      result.add(create.newRecord(derivedTableNameField).values("derived_table_01"));
      mockResult = new MockResult(1, result);
    } else if (sql.matches(LIST_CONTENTS_BY_IDS_WITH_ARRAY_REGEX)) {
      var fields = TEST_ENTITY_WITH_ARRAY_CONTENTS.get(0).keySet().stream().sorted().map(DSL::field).toList();
      Result<Record> result = create.newResult(fields.toArray(Field[]::new));
      result.addAll(
        TEST_ENTITY_WITH_ARRAY_CONTENTS.stream().map(row -> {
            Record record = create.newRecord(fields);
            row.keySet().stream().sorted().forEach(k -> {
              record.set(field(k), row.get(k));
            });
            return record;
          })
          .toList()
      );
      mockResult = new MockResult(1, result);
    } else if (sql.matches(ENTITY_TYPE_DEFINITION_WITH_ARRAY_REGEX)) {
      var definitionField = field("definition");
      Result<Record1<Object>> result = create.newResult(definitionField);
      result.add(create.newRecord(definitionField).values(writeValueAsString(ARRAY_ENTITY_TYPE)));
      mockResult = new MockResult(1, result);
    }
    return new MockResult[]{mockResult};
  }

  private static PgArray getPgArray() {
    try {
      PgArray mockPgArray = Mockito.mock(PgArray.class);
      String[] stringArray = {"value1"};
      when(mockPgArray.getArray()).thenReturn(stringArray);
      return mockPgArray;
    } catch(Exception e) {
      return null;
    }
  }

  private static PGobject getJsonbArray() {
    try {
      PGobject mockPgArray = Mockito.mock(PGobject.class);
      when(mockPgArray.getType()).thenReturn("jsonb");
      when(mockPgArray.getValue()).thenReturn("[\"value1\"]");
      return mockPgArray;
    } catch(Exception e) {
      return null;
    }
  }

  @SneakyThrows
  private String writeValueAsString(Object value) {
    return OBJECT_MAPPER.writeValueAsString(value);
  }
}
