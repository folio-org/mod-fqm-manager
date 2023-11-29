package org.folio.fqm.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.folio.querytool.domain.dto.EntityType;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;

import java.util.UUID;

import static org.jooq.impl.DSL.field;

/**
 * Mock data provider for MetaDataRepositoryTest.
 */
public class EntityTypeRepositoryTestDataProvider implements MockDataProvider {
  public static final String TEST_DERIVED_TABLE_NAME = "derived_table_01";
  public static final EntityType TEST_ENTITY_DEFINITION = new EntityType()
    .id(UUID.randomUUID().toString())
    .name(TEST_DERIVED_TABLE_NAME)
    .labelAlias("derived_table_alias_01");

  private static final String DERIVED_TABLE_NAME_QUERY_REGEX = "SELECT DERIVED_TABLE_NAME FROM .*\\.ENTITY_TYPE_DEFINITION WHERE ID = .*";
  private static final String ENTITY_TYPE_DEFINITION_REGEX = "SELECT DEFINITION FROM .*\\.ENTITY_TYPE_DEFINITION WHERE ID = .*";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  public MockResult[] execute(MockExecuteContext ctx) {
    DSLContext create = DSL.using(SQLDialect.POSTGRES);
    MockResult mockResult = new MockResult();

    String sql = ctx.sql().toUpperCase();

    if (sql.matches(DERIVED_TABLE_NAME_QUERY_REGEX)) {
      var derivedTableNameField = field("derived_table_name");
      Result<Record1<Object>> result = create.newResult(derivedTableNameField);
      result.add(create.newRecord(derivedTableNameField).values(TEST_DERIVED_TABLE_NAME));
      mockResult = new MockResult(1, result);
    } else if (sql.matches(ENTITY_TYPE_DEFINITION_REGEX)) {
      var definitionField = field("definition");
      Result<Record1<Object>> result = create.newResult(definitionField);
      result.add(create.newRecord(definitionField).values(writeValueAsString(TEST_ENTITY_DEFINITION)));
      mockResult = new MockResult(1, result);
    }
    return new MockResult[]{mockResult};
  }

  @SneakyThrows
  private String writeValueAsString(Object value) {
    return OBJECT_MAPPER.writeValueAsString(value);
  }
}
