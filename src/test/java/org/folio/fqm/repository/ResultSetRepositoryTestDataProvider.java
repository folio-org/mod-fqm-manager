package org.folio.fqm.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.folio.querytool.domain.dto.EntityDataType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeDefaultSort;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.RangedUUIDType;
import org.folio.querytool.domain.dto.StringType;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.folio.fqm.repository.EntityTypeRepository.ID_FIELD_NAME;
import static org.jooq.impl.DSL.field;

/**
 * Mock data provider that returns query results for Repository tests.
 */
public class ResultSetRepositoryTestDataProvider implements MockDataProvider {
  public static final EntityType TEST_ENTITY_TYPE_DEFINITION = new EntityType()
    .id("6b08439b-4f8e-4468-8046-ea620f5cfb74")
    .columns(
      List.of(
        new EntityTypeColumn().name(EntityTypeRepository.ID_FIELD_NAME).dataType(new EntityDataType().dataType("stringType")).valueGetter(":sourceAlias." + EntityTypeRepository.ID_FIELD_NAME).isIdColumn(true).sourceAlias("source1"),
        new EntityTypeColumn().name("field1").dataType(new EntityDataType().dataType("stringType")).valueGetter(":sourceAlias.field1").sourceAlias("source1")
      )
    )
    .defaultSort(List.of(new EntityTypeDefaultSort().columnName(EntityTypeRepository.ID_FIELD_NAME)))
    .name("TEST_ENTITY_TYPE")
    .fromClause("TEST_ENTITY_TYPE")
    .sources(List.of(
      new EntityTypeSourceDatabase()
        .type("db")
        .alias("source1")
        .target("target1"))
    );
  public static final EntityType TEST_GROUP_BY_ENTITY_TYPE_DEFINITION = new EntityType()
    .id(UUID.randomUUID().toString())
    .columns(
      List.of(
        new EntityTypeColumn().name(EntityTypeRepository.ID_FIELD_NAME).dataType(new EntityDataType().dataType("stringType")).valueGetter(":sourceAlias." + EntityTypeRepository.ID_FIELD_NAME).isIdColumn(true).sourceAlias("source1"),
        new EntityTypeColumn().name("field1").dataType(new EntityDataType().dataType("stringType")).valueGetter(":sourceAlias.field1").sourceAlias("source1")
      )
    )
    .name("TEST_GROUP_BY_ENTITY_TYPE")
    .groupByFields(
      List.of(
        "id", "field1"
      )
    )
    .fromClause("TEST_GROUP_BY_ENTITY_TYPE")
    .sources(List.of(
      new EntityTypeSourceDatabase()
        .type("db")
        .alias("source1")
        .target("target1"))
    );
  public static final List<Map<String, Object>> TEST_ENTITY_CONTENTS = List.of(
    Map.of(ID_FIELD_NAME, UUID.randomUUID(), "key1", "value1", "key2", "value2"),
    Map.of(ID_FIELD_NAME, UUID.randomUUID(), "key1", "value3", "key2", "value4"),
    new HashMap<>() {{
      put(ID_FIELD_NAME, null);
      put("key1", "value5");
      put("key2", "value6");
    }}
  );

  public static final EntityType ENTITY_TYPE = new EntityType()
    .columns(List.of(
      new EntityTypeColumn().name(ID_FIELD_NAME).dataType(new RangedUUIDType().dataType("rangedUUIDType")).valueGetter(":source1." + ID_FIELD_NAME).isIdColumn(true).sourceAlias("source1"),
      new EntityTypeColumn().name("key1").dataType(new StringType().dataType("stringType")).valueGetter(":source1.key1"),
      new EntityTypeColumn().name("key2").dataType(new StringType().dataType("stringType")).valueGetter(":source1.key2")
    ))
    .id("6b08439b-4f8e-4468-8046-ea620f5cfb74")
    .name("TEST_ENTITY_TYPE")
    .fromClause("TEST_ENTITY_TYPE")
    .sources(
      List.of(
        new EntityTypeSourceDatabase()
          .type("db")
          .alias("source1")
          .target("target1")
      )
    )
    .defaultSort(
      List.of(
        new EntityTypeDefaultSort()
          .direction(EntityTypeDefaultSort.DirectionEnum.ASC)
          .columnName("key1")
      )
    )
    .filterConditions(List.of("true = true"))
    .additionalEcsConditions(List.of("condition 1"));

  private static final String DERIVED_TABLE_NAME_QUERY_REGEX = "SELECT DERIVED_TABLE_NAME FROM ENTITY_TYPE_DEFINITION WHERE ID = .*";
  private static final String LIST_CONTENTS_BY_ID_SELECTOR_REGEX = "SELECT .* FROM .* JOIN \\(SELECT CONTENT_ID, SORT_SEQ FROM .* ORDER BY SORT_SEQ";
  private static final String LIST_CONTENTS_BY_IDS_REGEX = "SELECT .* FROM .* WHERE .*.ID IN .*";
  private static final String GET_RESULT_SET_SYNC_REGEX = "SELECT .* FROM .* WHERE .* ORDER BY .* FETCH NEXT .*";
  private static final String ENTITY_TYPE_DEFINITION_REGEX = "SELECT DEFINITION FROM ENTITY_TYPE_DEFINITION WHERE ID = .*";
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
    } else if (sql.matches(LIST_CONTENTS_BY_ID_SELECTOR_REGEX) || sql.matches(LIST_CONTENTS_BY_IDS_REGEX) ||
      sql.matches(GET_RESULT_SET_SYNC_REGEX)) {
      var fields = TEST_ENTITY_CONTENTS.get(0).keySet().stream().sorted().map(DSL::field).toList();
      Result<Record> result = create.newResult(fields.toArray(org.jooq.Field[]::new));
      result.addAll(
        TEST_ENTITY_CONTENTS.stream().map(row -> {
            Record record = create.newRecord(fields);
            row.keySet().stream().sorted().forEach(k -> record.set(field(k), row.get(k)));
            return record;
          })
          .toList()
      );
      mockResult = new MockResult(1, result);
    } else if (sql.matches(ENTITY_TYPE_DEFINITION_REGEX)) {
      var definitionField = field("definition");
      Result<Record1<Object>> result = create.newResult(definitionField);
      result.add(create.newRecord(definitionField).values(writeValueAsString(ENTITY_TYPE)));
      mockResult = new MockResult(1, result);
    }
    return new MockResult[]{mockResult};
  }

  @SneakyThrows
  private String writeValueAsString(Object value) {
    return OBJECT_MAPPER.writeValueAsString(value);
  }
}
