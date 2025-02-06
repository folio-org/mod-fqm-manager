package org.folio.fqm.repository;

import static org.folio.fqm.utils.flattening.FromClauseUtils.getFromClause;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.folio.fqm.service.EntityTypeFlatteningService;
import org.folio.fqm.utils.flattening.FromClauseUtils;
import org.folio.spring.FolioExecutionContext;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    entityTypeFlatteningService = mock(EntityTypeFlatteningService.class);
    this.repo = new ResultSetRepository(context, entityTypeFlatteningService, mock(FolioExecutionContext.class), new ObjectMapper());
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
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), any(), anyBoolean()))
      .thenReturn(ResultSetRepositoryArrayTestDataProvider.ARRAY_ENTITY_TYPE);

    try (MockedStatic<FromClauseUtils> fromClauseUtils = mockStatic(FromClauseUtils.class)) {
      fromClauseUtils
        .when(() -> getFromClause(ResultSetRepositoryArrayTestDataProvider.ARRAY_ENTITY_TYPE, null))
        .thenReturn("TEST_ENTITY_TYPE");
      fromClauseUtils
        .when(() -> getFromClause(ResultSetRepositoryArrayTestDataProvider.ARRAY_ENTITY_TYPE, "tenant_01"))
        .thenReturn("TEST_ENTITY_TYPE");

      List<Map<String, Object>> actualList = repo.getResultSet(entityTypeId, fields, listIds, List.of("tenant_01"));

      assertEquals(expectedList.get(0).get("id"), actualList.get(0).get("id"));
      assertEquals(expectedList.get(0).get("arrayField"), actualList.get(0).get("arrayField"));
    }
  }

  @Test
  void getResultSetShouldHandleJsonbArray() {
    UUID entityTypeId = UUID.randomUUID();
    List<List<String>> listIds = List.of(
      List.of(UUID.randomUUID().toString())
    );
    List<String> fields = List.of("id", "testJsonbArrayField");
    List<Map<String, Object>> expectedFullList = ResultSetRepositoryArrayTestDataProvider.TEST_ENTITY_WITH_ARRAY_CONTENTS;
    List<Map<String, Object>> expectedList = List.of(
      Map.of("id", expectedFullList.get(0).get("id"), "testField", List.of("value1"))
    );
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), any(), anyBoolean()))
      .thenReturn(ResultSetRepositoryArrayTestDataProvider.ARRAY_ENTITY_TYPE);

    try (MockedStatic<FromClauseUtils> fromClauseUtils = mockStatic(FromClauseUtils.class)) {
      fromClauseUtils
        .when(() -> getFromClause(ResultSetRepositoryArrayTestDataProvider.ARRAY_ENTITY_TYPE, null))
        .thenReturn("TEST_ENTITY_TYPE");
      fromClauseUtils
        .when(() -> getFromClause(ResultSetRepositoryArrayTestDataProvider.ARRAY_ENTITY_TYPE, "tenant_01"))
        .thenReturn("TEST_ENTITY_TYPE");

      List<Map<String, Object>> actualList = repo.getResultSet(entityTypeId, fields, listIds, List.of("tenant_01"));

      assertEquals(expectedList.get(0).get("id"), actualList.get(0).get("id"));
      assertEquals(expectedList.get(0).get("jsonbArrayField"), actualList.get(0).get("jsonbArrayField"));
    }
  }
}
