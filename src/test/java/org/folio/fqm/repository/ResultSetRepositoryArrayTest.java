package org.folio.fqm.repository;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NOTE - Tests in this class depends on the mock results returned from {@link ResultSetRepositoryArrayTestDataProvider} class
 */
class ResultSetRepositoryArrayTest {

  private ResultSetRepository repo;

  @BeforeEach
  void setup() {
    DSLContext context = DSL.using(new MockConnection(
      new ResultSetRepositoryArrayTestDataProvider()), SQLDialect.POSTGRES);
    DSLContext readerContext = DSL.using(new MockConnection(
      new ResultSetRepositoryArrayTestDataProvider()), SQLDialect.POSTGRES);

    EntityTypeRepository entityTypeRepository = new EntityTypeRepository(readerContext, context, new ObjectMapper());
    this.repo = new ResultSetRepository(context, entityTypeRepository);
  }

  @Test
  void getResultSetShouldHandleArray() {
    List<List<String>> listIds = List.of(
      List.of(UUID.randomUUID().toString())
    );
    List<String> fields = List.of("id", "testField");
    List<Map<String, Object>> expectedFullList = ResultSetRepositoryArrayTestDataProvider.TEST_ENTITY_WITH_ARRAY_CONTENTS;
    List<Map<String, Object>> expectedList = List.of(
      Map.of("id", expectedFullList.get(0).get("id"), "testField", List.of("value1"))
    );
    List<Map<String, Object>> actualList = repo.getResultSet(UUID.randomUUID(), fields, listIds);
    assertEquals(expectedList.get(0).get("id"), actualList.get(0).get("id"));
    assertEquals(expectedList.get(0).get("arrayField"), actualList.get(0).get("arrayField"));
  }
}
