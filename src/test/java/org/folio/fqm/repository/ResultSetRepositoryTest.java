package org.folio.fqm.repository;

import org.folio.fql.model.EqualsCondition;
import org.folio.fql.model.Fql;
import org.folio.fql.model.field.FqlField;
import org.folio.fqm.service.EntityTypeFlatteningService;
import org.folio.spring.FolioExecutionContext;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.folio.fqm.repository.ResultSetRepositoryTestDataProvider.ENTITY_TYPE;
import static org.folio.fqm.utils.IdStreamerTestDataProvider.TEST_GROUP_BY_ENTITY_TYPE_DEFINITION;

/**
 * NOTE - Tests in this class depends on the mock results returned from {@link ResultSetRepositoryTestDataProvider} class
 */
class ResultSetRepositoryTest {

  EntityTypeFlatteningService entityTypeFlatteningService;

  private ResultSetRepository repo;

  @BeforeEach
  void setup() {
    DSLContext context = DSL.using(new MockConnection(
      new ResultSetRepositoryTestDataProvider()), SQLDialect.POSTGRES);

    entityTypeFlatteningService = mock(EntityTypeFlatteningService.class);
    this.repo = new ResultSetRepository(context, entityTypeFlatteningService, mock(FolioExecutionContext.class));
  }

  @Test
  void getResultSetWithNullFieldsShouldReturnEmptyList() {
    List<List<String>> listIds = List.of(
      List.of(UUID.randomUUID().toString()),
      List.of(UUID.randomUUID().toString())
    );
    List<Map<String, Object>> expectedList = List.of();
    List<Map<String, Object>> actualList = repo.getResultSet(UUID.randomUUID(), null, listIds, List.of("tenant_01"));
    assertEquals(expectedList, actualList);
  }

  @Test
  void getResultSetWithEmptyFieldsShouldEmptyList() {
    List<List<String>> listIds = List.of(
      List.of(UUID.randomUUID().toString()),
      List.of(UUID.randomUUID().toString())
    );
    List<String> fields = List.of();
    List<Map<String, Object>> expectedList = List.of();
    List<Map<String, Object>> actualList = repo.getResultSet(UUID.randomUUID(), fields, listIds, List.of("tenant_01"));
    assertEquals(expectedList, actualList);
  }

  @Test
  void getResultSetShouldReturnResultsWithRequestedFields() {
    UUID entityTypeId = UUID.randomUUID();
    List<List<String>> listIds = List.of(
      List.of(UUID.randomUUID().toString()),
      List.of(UUID.randomUUID().toString()),
      List.of(UUID.randomUUID().toString())
    );
    List<String> fields = List.of("id", "key1");
    List<Map<String, Object>> expectedFullList = ResultSetRepositoryTestDataProvider.TEST_ENTITY_CONTENTS;
    // Since we are only asking for "id" and "key2" fields, create expected list without key1 included
    List<Map<String, Object>> expectedList = List.of(
      Map.of("id", expectedFullList.get(0).get("id"), "key1", "value1"),
      Map.of("id", expectedFullList.get(1).get("id"), "key1", "value3"),
      Map.of("id", expectedFullList.get(2).get("id"), "key1", "value5")
    );
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null))
      .thenReturn(ResultSetRepositoryTestDataProvider.ENTITY_TYPE);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01"))
      .thenReturn(ResultSetRepositoryTestDataProvider.ENTITY_TYPE);
    when(entityTypeFlatteningService.getJoinClause(ResultSetRepositoryTestDataProvider.ENTITY_TYPE, null))
      .thenReturn("TEST_ENTITY_TYPE");
    when(entityTypeFlatteningService.getJoinClause(ResultSetRepositoryTestDataProvider.ENTITY_TYPE, "tenant_01"))
      .thenReturn("TEST_ENTITY_TYPE");
    List<Map<String, Object>> actualList = repo.getResultSet(entityTypeId, fields, listIds, List.of("tenant_01"));
    assertEquals(expectedList, actualList);
  }

  @Test
  void shouldRunSynchronousQueryAndReturnContents() {
    UUID entityTypeId = UUID.randomUUID();
    List<String> afterId = List.of(UUID.randomUUID().toString());
    int limit = 100;
    Fql fql = new Fql("", new EqualsCondition(new FqlField("key1"), "value1"));
    List<String> fields = List.of("id", "key1");
    List<Map<String, Object>> expectedFullList = ResultSetRepositoryTestDataProvider.TEST_ENTITY_CONTENTS;
    // Since we are only asking for "id" and "key1" fields, create expected list without key2 included
    List<Map<String, Object>> expectedList = List.of(
      Map.of("id", expectedFullList.get(0).get("id"), "key1", "value1"),
      Map.of("id", expectedFullList.get(1).get("id"), "key1", "value3"),
      Map.of("id", expectedFullList.get(2).get("id"), "key1", "value5")
    );
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null))
      .thenReturn(ResultSetRepositoryTestDataProvider.ENTITY_TYPE);
    when(entityTypeFlatteningService.getJoinClause(ResultSetRepositoryTestDataProvider.ENTITY_TYPE, null))
      .thenReturn("TEST_ENTITY_TYPE");
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01"))
      .thenReturn(ResultSetRepositoryTestDataProvider.ENTITY_TYPE);
    when(entityTypeFlatteningService.getJoinClause(ResultSetRepositoryTestDataProvider.ENTITY_TYPE, "tenant_01"))
      .thenReturn("TEST_ENTITY_TYPE");
    List<Map<String, Object>> actualList = repo.getResultSetSync(entityTypeId, fql, fields, afterId, limit, List.of("tenant_01"));
    assertEquals(expectedList, actualList);
  }

  @Test
  void shouldReturnEmptyResultSetForSynchronousQueryWithEmptyFields() {
    UUID entityTypeId = UUID.randomUUID();
    List<String> afterId = List.of(UUID.randomUUID().toString());
    int limit = 100;
    Fql fql = new Fql("", new EqualsCondition(new FqlField("key1"), "value1"));
    List<String> fields = List.of();
    List<Map<String, Object>> expectedList = List.of();
    List<Map<String, Object>> actualList = repo.getResultSetSync(entityTypeId, fql, fields, afterId, limit, null);
    assertEquals(expectedList, actualList);
  }

  @Test
  void shouldReturnEmptyResultSetForSynchronousQueryWithNullFields() {
    UUID entityTypeId = UUID.randomUUID();
    List<String> afterId = List.of(UUID.randomUUID().toString());
    int limit = 100;
    Fql fql = new Fql("", new EqualsCondition(new FqlField("key1"), "value1"));
    List<Map<String, Object>> expectedList = List.of();
    List<Map<String, Object>> actualList = repo.getResultSetSync(entityTypeId, fql, null, afterId, limit, null);
    assertEquals(expectedList, actualList);
  }

  @Test
  void shouldRunSynchronousQueryAndHandleNullAfterIdParameter() {
    UUID entityTypeId = UUID.randomUUID();
    int limit = 100;
    Fql fql = new Fql("", new EqualsCondition(new FqlField("key1"), "value1"));
    List<String> fields = List.of("id", "key1", "key2");
    List<String> tenantIds = List.of("tenant_01");
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null))
      .thenReturn(ResultSetRepositoryTestDataProvider.ENTITY_TYPE);
    when(entityTypeFlatteningService.getJoinClause(ResultSetRepositoryTestDataProvider.ENTITY_TYPE, null))
      .thenReturn("TEST_ENTITY_TYPE");
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01"))
      .thenReturn(ResultSetRepositoryTestDataProvider.ENTITY_TYPE);
    when(entityTypeFlatteningService.getJoinClause(ResultSetRepositoryTestDataProvider.ENTITY_TYPE, "tenant_01"))
      .thenReturn("TEST_ENTITY_TYPE");
    List<Map<String, Object>> actualList = repo.getResultSetSync(entityTypeId, fql, fields, null, limit, tenantIds);
    assertEquals(ResultSetRepositoryTestDataProvider.TEST_ENTITY_CONTENTS, actualList);
  }

  @Test
  void shouldThrowExceptionForSynchronousQueryWithGroupByFields() {
    UUID entityTypeId = UUID.randomUUID();
    int limit = 100;
    Fql fql = new Fql("", new EqualsCondition(new FqlField("id"), "value1"));
    List<String> fields = List.of("id");
    List<String> tenantIds = List.of("tenant_01");
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null))
      .thenReturn(TEST_GROUP_BY_ENTITY_TYPE_DEFINITION);
    when(entityTypeFlatteningService.getJoinClause(TEST_GROUP_BY_ENTITY_TYPE_DEFINITION, null))
      .thenReturn("TEST_GROUP_BY_ENTITY_TYPE");
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01"))
      .thenReturn(ResultSetRepositoryTestDataProvider.ENTITY_TYPE);
    when(entityTypeFlatteningService.getJoinClause(TEST_GROUP_BY_ENTITY_TYPE_DEFINITION, "tenant_01"))
      .thenReturn("TEST_GROUP_BY_ENTITY_TYPE");
    assertThrows(IllegalArgumentException.class, () -> repo.getResultSetSync(entityTypeId, fql, fields, null, limit, tenantIds));
  }

  @Test
  void shouldHandleCrossTenantSynchronousQuery() {
    UUID entityTypeId = UUID.fromString(ENTITY_TYPE.getId());
    int limit = 100;
    Fql fql = new Fql("", new EqualsCondition(new FqlField("id"), "value1"));
    List<String> fields = List.of("id", "key1", "key2");
    List<String> tenantIds = List.of("tenant_01", "tenant_02", "tenant_03");
    List<Map<String, Object>> expectedResults = ResultSetRepositoryTestDataProvider.TEST_ENTITY_CONTENTS;
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null))
      .thenReturn(ENTITY_TYPE);
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), any(String.class)))
      .thenReturn(ENTITY_TYPE);
    when(entityTypeFlatteningService.getJoinClause(eq(ENTITY_TYPE), any(String.class)))
      .thenReturn("TEST_ENTITY_TYPE");

    List<Map<String, Object>> actualResults = repo.getResultSetSync(entityTypeId, fql, fields, null, limit, tenantIds);
    assertEquals(expectedResults, actualResults);
  }

  @Test
  void shouldHandleCrossTenantAsynchronousQuery() {
    UUID entityTypeId = UUID.randomUUID();
    List<List<String>> listIds = List.of(
      List.of(UUID.randomUUID().toString()),
      List.of(UUID.randomUUID().toString()),
      List.of(UUID.randomUUID().toString())
    );
    List<String> tenantIds = List.of("tenant_01", "tenant_02", "tenant_03");
    List<String> fields = List.of("id", "key1");
    List<Map<String, Object>> expectedFullList = ResultSetRepositoryTestDataProvider.TEST_ENTITY_CONTENTS;
    List<Map<String, Object>> expectedList = List.of(
      Map.of("id", expectedFullList.get(0).get("id"), "key1", "value1"),
      Map.of("id", expectedFullList.get(1).get("id"), "key1", "value3"),
      Map.of("id", expectedFullList.get(2).get("id"), "key1", "value5")
    );
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null))
      .thenReturn(ENTITY_TYPE);
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), any(String.class)))
      .thenReturn(ENTITY_TYPE);
    when(entityTypeFlatteningService.getJoinClause(eq(ENTITY_TYPE), any(String.class)))
      .thenReturn("TEST_ENTITY_TYPE");
    List<Map<String, Object>> actualList = repo.getResultSet(entityTypeId, fields, listIds, tenantIds);
    assertEquals(expectedList, actualList);
  }

  @Test
  void shouldHandleGroupByFieldsForAsynchronousQuery() {
    UUID entityTypeId = UUID.randomUUID();
    List<List<String>> listIds = List.of(
      List.of(UUID.randomUUID().toString()),
      List.of(UUID.randomUUID().toString()),
      List.of(UUID.randomUUID().toString())
    );
    List<String> fields = List.of("id");
    List<String> tenantIds = List.of("tenant_01");
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null))
      .thenReturn(TEST_GROUP_BY_ENTITY_TYPE_DEFINITION);
    when(entityTypeFlatteningService.getJoinClause(TEST_GROUP_BY_ENTITY_TYPE_DEFINITION, null))
      .thenReturn("TEST_GROUP_BY_ENTITY_TYPE");
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01"))
      .thenReturn(TEST_GROUP_BY_ENTITY_TYPE_DEFINITION);
    when(entityTypeFlatteningService.getJoinClause(TEST_GROUP_BY_ENTITY_TYPE_DEFINITION, "tenant_01"))
      .thenReturn("TEST_GROUP_BY_ENTITY_TYPE");
    assertDoesNotThrow(() -> repo.getResultSet(entityTypeId, fields, listIds, tenantIds));
  }
}
