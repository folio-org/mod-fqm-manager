package org.folio.fqm.utils;

import static org.jooq.impl.DSL.field;

import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.EntityDataType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeDefaultSort;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;

/**
 * Mock data provider for IdStreamerTest
 */
public class IdStreamerTestDataProvider implements MockDataProvider {

  public static final List<UUID> TEST_CONTENT_IDS = List.of(UUID.randomUUID(), UUID.randomUUID());
  public static final EntityType TEST_ENTITY_TYPE_DEFINITION = new EntityType()
    .id(UUID.randomUUID().toString())
    .columns(
      List.of(
        new EntityTypeColumn().name(EntityTypeRepository.ID_FIELD_NAME),
        new EntityTypeColumn().name("field1").dataType(new EntityDataType().dataType("stringType"))
      )
    )
    .defaultSort(List.of(new EntityTypeDefaultSort().columnName(EntityTypeRepository.ID_FIELD_NAME)));

  private static final String DERIVED_TABLE_NAME_QUERY_REGEX = "SELECT DERIVED_TABLE_NAME FROM ENTITY_TYPE_DEFINITION WHERE ID = .*";
  private static final String ENTITY_TYPE_DEFINITION_REGEX = "SELECT DEFINITION FROM ENTITY_TYPE_DEFINITION WHERE ID = .*";
  private static final String GET_IDS_QUERY_REGEX = "SELECT ID FROM .* WHERE .* ORDER BY ID ASC";
  private static final String GET_SORTED_IDS_QUERY_REGEX = "SELECT ID FROM .* WHERE ID IN \\(SELECT RESULT_ID FROM .*_MOD_FQM_MANAGER.QUERY_RESULTS WHERE QUERY_ID = .*";
  private static final String GET_ENTITY_TYPE_ID_FROM_QUERY_ID_REGEX = "SELECT ENTITY_TYPE_ID FROM QUERY_DETAILS WHERE QUERY_ID = .*";
  private static final String GET_IDS_REGEX = ".*QUERY_RESULTS.*";
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
    } else if (sql.matches(ENTITY_TYPE_DEFINITION_REGEX)) {
      var definitionField = field("definition");
      Result<Record1<Object>> result = create.newResult(definitionField);
      result.add(create.newRecord(definitionField).values(writeValueAsString(TEST_ENTITY_TYPE_DEFINITION)));
      mockResult = new MockResult(1, result);
    } else if (sql.matches(GET_IDS_QUERY_REGEX) || sql.matches(GET_SORTED_IDS_QUERY_REGEX)) {
      Result<Record1<Object>> result = create.newResult(DSL.field(EntityTypeRepository.ID_FIELD_NAME));
      TEST_CONTENT_IDS.forEach(id -> result.add(create.newRecord(DSL.field(EntityTypeRepository.ID_FIELD_NAME)).values(id)));
      mockResult = new MockResult(1, result);
    } else if (sql.matches(GET_ENTITY_TYPE_ID_FROM_QUERY_ID_REGEX)) {
      var entityTypeIdField = field("entity_type_id", UUID.class);
      Result<Record1<UUID>> result = create.newResult(entityTypeIdField);
      result.add(create.newRecord(entityTypeIdField).values(UUID.fromString(TEST_ENTITY_TYPE_DEFINITION.getId())));
      mockResult = new MockResult(1, result);
    } else if (sql.matches(GET_IDS_REGEX)) {
      var entityTypeIdField = field("record_id", UUID.class);
      Result<Record1<UUID>> result = create.newResult(entityTypeIdField);
      result.add(create.newRecord(entityTypeIdField).values(UUID.fromString(TEST_ENTITY_TYPE_DEFINITION.getId())));
      mockResult = new MockResult(1, result);
    }
    return new MockResult[]{mockResult};
  }

  @SneakyThrows
  private String writeValueAsString(Object value) {
    return OBJECT_MAPPER.writeValueAsString(value);
  }
}
