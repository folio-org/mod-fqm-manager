package org.folio.fqm.repository;

import org.folio.fqm.service.EntityTypeFlatteningService;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * NOTE - Tests in this class depends on the mock results returned from {@link ResultSetRepositoryArrayTestDataProvider} class
 */
class ResultSetRepositoryArrayTest {

  private ResultSetRepository repo;

  EntityTypeFlatteningService entityTypeFlatteningService;


  @BeforeEach
  void setup() {
    DSLContext context = DSL.using(new MockConnection(
      new ResultSetRepositoryArrayTestDataProvider()), SQLDialect.POSTGRES);
    entityTypeFlatteningService = mock(EntityTypeFlatteningService.class);    this.repo = new ResultSetRepository(context, entityTypeFlatteningService);
  }

  @Test
  void getResultSetShouldHandleArray() {
    UUID entityTypeId = UUID.randomUUID();
    List<List<String>> listIds = List.of(
      List.of(UUID.randomUUID().toString())
    );
    List<String> fields = List.of("id", "testField");
    List<Map<String, Object>> expectedFullList = ResultSetRepositoryArrayTestDataProvider.TEST_ENTITY_WITH_ARRAY_CONTENTS;
    List<Map<String, Object>> expectedList = List.of(
      Map.of("id", expectedFullList.get(0).get("id"), "testField", List.of("value1"))
    );
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, true))
      .thenReturn(Optional.ofNullable(ResultSetRepositoryArrayTestDataProvider.ARRAY_ENTITY_TYPE));
    when(entityTypeFlatteningService.getJoinClause(ResultSetRepositoryArrayTestDataProvider.ARRAY_ENTITY_TYPE))
      .thenReturn("TEST_ENTITY_TYPE");
    List<Map<String, Object>> actualList = repo.getResultSet(entityTypeId, fields, listIds);
    assertEquals(expectedList.get(0).get("id"), actualList.get(0).get("id"));
    assertEquals(expectedList.get(0).get("arrayField"), actualList.get(0).get("arrayField"));
  }
}
