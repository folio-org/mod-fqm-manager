package org.folio.fqm.repository;

import tools.jackson.databind.ObjectMapper;
import org.folio.fql.model.EqualsCondition;
import org.folio.fql.model.Fql;
import org.folio.fql.model.field.FqlField;
import org.folio.fqm.service.EntityTypeFlatteningService;
import org.folio.fqm.service.EntityTypeInitializationService;
import org.folio.fqm.utils.flattening.FromClauseUtils;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.JsonbArrayType;
import org.folio.querytool.domain.dto.MarcType;
import org.folio.spring.FolioExecutionContext;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.postgresql.util.PGobject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.folio.fqm.repository.ResultSetRepositoryTestDataProvider.TEST_GROUP_BY_ENTITY_TYPE_DEFINITION;
import static org.folio.fqm.utils.flattening.FromClauseUtils.getFromClause;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.folio.fqm.repository.ResultSetRepositoryTestDataProvider.ENTITY_TYPE;

/**
 * NOTE - Tests in this class depends on the mock results returned from {@link ResultSetRepositoryTestDataProvider} class
 */
class ResultSetRepositoryTest {

  EntityTypeFlatteningService entityTypeFlatteningService;
  EntityTypeInitializationService entityTypeInitializationService;

  private ResultSetRepository repo;

  @BeforeEach
  void setup() {
    DSLContext context = DSL.using(new MockConnection(
      new ResultSetRepositoryTestDataProvider()), SQLDialect.POSTGRES);

    entityTypeFlatteningService = mock(EntityTypeFlatteningService.class);
    entityTypeInitializationService = mock(EntityTypeInitializationService.class);

    lenient().when(entityTypeInitializationService.runWithRecovery(any(), any()))
      .thenAnswer(invocation -> {
        var callable = invocation.getArgument(1, Supplier.class);
        return callable.get();
      });

    this.repo =
      new ResultSetRepository(
        context,
        entityTypeFlatteningService,
        entityTypeInitializationService,
        mock(FolioExecutionContext.class),
        new ObjectMapper()
      );
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
  void getResultSetWithEmptyIdsShouldReturnEmptyList() {
    List<String> fields = List.of("field1");
    List<Map<String, Object>> expectedList = List.of();
    List<Map<String, Object>> actualList = repo.getResultSet(UUID.randomUUID(), fields, List.of(), List.of("tenant_01"));
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
      new ArrayList<>() {{
        add(null);
      }}
    );
    List<String> fields = List.of("id", "key1");
    List<Map<String, Object>> expectedFullList = ResultSetRepositoryTestDataProvider.TEST_ENTITY_CONTENTS;
    // Since we are only asking for "id" and "key2" fields, create expected list without key1 included
    List<Map<String, Object>> expectedList = List.of(
      Map.of("id", expectedFullList.get(0).get("id"), "key1", "value1"),
      Map.of("id", expectedFullList.get(1).get("id"), "key1", "value3"),
      new HashMap<>() {{
        put("id", null);
        put("key1", "value5");
      }}
    );
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), eq(null), anyBoolean()))
      .thenReturn(ResultSetRepositoryTestDataProvider.ENTITY_TYPE);
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), eq("tenant_01"), anyBoolean()))
      .thenReturn(ResultSetRepositoryTestDataProvider.ENTITY_TYPE);

    try (MockedStatic<FromClauseUtils> fromClauseUtils = mockStatic(FromClauseUtils.class)) {
      fromClauseUtils.when(() -> getFromClause(ResultSetRepositoryTestDataProvider.ENTITY_TYPE, null))
        .thenReturn("TEST_ENTITY_TYPE");
      fromClauseUtils.when(() -> getFromClause(ResultSetRepositoryTestDataProvider.ENTITY_TYPE, "tenant_01"))
        .thenReturn("TEST_ENTITY_TYPE");

      List<Map<String, Object>> actualList = repo.getResultSet(entityTypeId, fields, listIds, List.of("tenant_01"));

      assertEquals(expectedList, actualList);
    }
  }

  @Test
  void shouldRunSynchronousQueryAndReturnContents() {
    UUID entityTypeId = UUID.randomUUID();
    int limit = 100;
    Fql fql = new Fql("", new EqualsCondition(new FqlField("key1"), "value1"));
    List<String> fields = List.of("id", "key1");
    List<Map<String, Object>> expectedFullList = ResultSetRepositoryTestDataProvider.TEST_ENTITY_CONTENTS;
    // Since we are only asking for "id" and "key1" fields, create expected list without key2 included
    List<Map<String, Object>> expectedList = List.of(
      Map.of("id", expectedFullList.get(0).get("id"), "key1", "value1"),
      Map.of("id", expectedFullList.get(1).get("id"), "key1", "value3"),
      new HashMap<>() {{
        put("id", null);
        put("key1", "value5");
      }}
    );
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), eq(null), anyBoolean()))
      .thenReturn(ResultSetRepositoryTestDataProvider.ENTITY_TYPE);
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), eq("tenant_01"), anyBoolean()))
      .thenReturn(ResultSetRepositoryTestDataProvider.ENTITY_TYPE);
    try (MockedStatic<FromClauseUtils> fromClauseUtils = mockStatic(FromClauseUtils.class)) {
      fromClauseUtils.when(() -> getFromClause(ResultSetRepositoryTestDataProvider.ENTITY_TYPE, null))
        .thenReturn("TEST_ENTITY_TYPE");
      fromClauseUtils.when(() -> getFromClause(ResultSetRepositoryTestDataProvider.ENTITY_TYPE, "tenant_01"))
        .thenReturn("TEST_ENTITY_TYPE");

      List<Map<String, Object>> actualList = repo.getResultSetSync(entityTypeId, fql, fields, limit, List.of("tenant_01"), false);

      assertEquals(expectedList, actualList);
    }
  }

  @Test
  void shouldReturnEmptyResultSetForSynchronousQueryWithEmptyFields() {
    UUID entityTypeId = UUID.randomUUID();
    int limit = 100;
    Fql fql = new Fql("", new EqualsCondition(new FqlField("key1"), "value1"));
    List<String> fields = List.of();
    List<Map<String, Object>> expectedList = List.of();
    List<Map<String, Object>> actualList = repo.getResultSetSync(entityTypeId, fql, fields, limit, null, false);
    assertEquals(expectedList, actualList);
  }

  @Test
  void shouldReturnEmptyResultSetForSynchronousQueryWithNullFields() {
    UUID entityTypeId = UUID.randomUUID();
    int limit = 100;
    Fql fql = new Fql("", new EqualsCondition(new FqlField("key1"), "value1"));
    List<Map<String, Object>> expectedList = List.of();
    List<Map<String, Object>> actualList = repo.getResultSetSync(entityTypeId, fql, null, limit, null, false);
    assertEquals(expectedList, actualList);
  }

  @Test
  void shouldThrowExceptionForSynchronousQueryWithGroupByFields() {
    UUID entityTypeId = UUID.randomUUID();
    int limit = 100;
    Fql fql = new Fql("", new EqualsCondition(new FqlField("id"), "value1"));
    List<String> fields = List.of("id");
    List<String> tenantIds = List.of("tenant_01");
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), eq(null), anyBoolean()))
      .thenReturn(TEST_GROUP_BY_ENTITY_TYPE_DEFINITION);
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), eq("tenant_01"), anyBoolean()))
      .thenReturn(ResultSetRepositoryTestDataProvider.ENTITY_TYPE);
    try (MockedStatic<FromClauseUtils> fromClauseUtils = mockStatic(FromClauseUtils.class)) {
      fromClauseUtils.when(() -> getFromClause(TEST_GROUP_BY_ENTITY_TYPE_DEFINITION, null))
        .thenReturn("TEST_GROUP_BY_ENTITY_TYPE");
      fromClauseUtils.when(() -> getFromClause(TEST_GROUP_BY_ENTITY_TYPE_DEFINITION, "tenant_01"))
        .thenReturn("TEST_GROUP_BY_ENTITY_TYPE");
      assertThrows(IllegalArgumentException.class, () -> repo.getResultSetSync(entityTypeId, fql, fields, limit, tenantIds, false));
    }
  }

  @Test
  void shouldHandleCrossTenantSynchronousQuery() {
    UUID entityTypeId = UUID.fromString(ENTITY_TYPE.getId());
    int limit = 100;
    Fql fql = new Fql("", new EqualsCondition(new FqlField("id"), "value1"));
    List<String> fields = List.of("id", "key1", "key2");
    List<String> tenantIds = List.of("tenant_01", "tenant_02", "tenant_03");
    List<Map<String, Object>> expectedResults = ResultSetRepositoryTestDataProvider.TEST_ENTITY_CONTENTS;
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), eq(null), anyBoolean()))
      .thenReturn(ENTITY_TYPE);
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), any(String.class), anyBoolean()))
      .thenReturn(ENTITY_TYPE);
    try (MockedStatic<FromClauseUtils> fromClauseUtils = mockStatic(FromClauseUtils.class)) {
      fromClauseUtils.when(() -> getFromClause(eq(ENTITY_TYPE), any(String.class)))
        .thenReturn("TEST_ENTITY_TYPE");

      List<Map<String, Object>> actualResults = repo.getResultSetSync(entityTypeId, fql, fields, limit, tenantIds, true);
      assertEquals(expectedResults, actualResults);
    }
  }

  @Test
  void shouldHandleAdditionalEcsConditionsForSynchronousQuery() {
    UUID entityTypeId = UUID.randomUUID();
    int limit = 100;
    Fql fql = new Fql("", new EqualsCondition(new FqlField("key1"), "value1"));
    List<String> fields = List.of("id", "key1");
    List<Map<String, Object>> expectedFullList = ResultSetRepositoryTestDataProvider.TEST_ENTITY_CONTENTS;
    // Since we are only asking for "id" and "key1" fields, create expected list without key2 included
    List<Map<String, Object>> expectedList = List.of(
      Map.of("id", expectedFullList.get(0).get("id"), "key1", "value1"),
      Map.of("id", expectedFullList.get(1).get("id"), "key1", "value3"),
      new HashMap<>() {{
        put("id", null);
        put("key1", "value5");
      }}
    );
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), eq(null), anyBoolean()))
      .thenReturn(ResultSetRepositoryTestDataProvider.ENTITY_TYPE);
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), eq("tenant_01"), anyBoolean()))
      .thenReturn(ResultSetRepositoryTestDataProvider.ENTITY_TYPE);
    try (MockedStatic<FromClauseUtils> fromClauseUtils = mockStatic(FromClauseUtils.class)) {
      fromClauseUtils.when(() -> getFromClause(ResultSetRepositoryTestDataProvider.ENTITY_TYPE, null))
        .thenReturn("TEST_ENTITY_TYPE");
      fromClauseUtils.when(() -> getFromClause(ResultSetRepositoryTestDataProvider.ENTITY_TYPE, "tenant_01"))
        .thenReturn("TEST_ENTITY_TYPE");
      List<Map<String, Object>> actualList = repo.getResultSetSync(entityTypeId, fql, fields, limit, List.of("tenant_01"), true);
      assertEquals(expectedList, actualList);
    }
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
      new HashMap<>() {{
        put("id", null);
        put("key1", "value5");
      }}
    );
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), eq(null), anyBoolean()))
      .thenReturn(ENTITY_TYPE);
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), any(String.class), anyBoolean()))
      .thenReturn(ENTITY_TYPE);
    try (MockedStatic<FromClauseUtils> fromClauseUtils = mockStatic(FromClauseUtils.class)) {
      fromClauseUtils.when(() -> getFromClause(eq(ENTITY_TYPE), any(String.class)))
        .thenReturn("TEST_ENTITY_TYPE");
      List<Map<String, Object>> actualList = repo.getResultSet(entityTypeId, fields, listIds, tenantIds);
      assertEquals(expectedList, actualList);
    }
  }

  @Test
  void shouldHandleGroupByFieldsForAsynchronousQuery() {
    UUID entityTypeId = UUID.randomUUID();
    List<List<String>> listIds = List.of(
      List.of(UUID.randomUUID().toString()),
      List.of(UUID.randomUUID().toString()),
      new ArrayList<>() {{
        add(null);
      }}
    );
    List<String> fields = List.of("id");
    List<String> tenantIds = List.of("tenant_01");
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), eq(null), anyBoolean()))
      .thenReturn(TEST_GROUP_BY_ENTITY_TYPE_DEFINITION);
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), eq("tenant_01"), anyBoolean()))
      .thenReturn(TEST_GROUP_BY_ENTITY_TYPE_DEFINITION);
    try (MockedStatic<FromClauseUtils> fromClauseUtils = mockStatic(FromClauseUtils.class)) {
      fromClauseUtils.when(() -> getFromClause(TEST_GROUP_BY_ENTITY_TYPE_DEFINITION, null))
        .thenReturn("TEST_GROUP_BY_ENTITY_TYPE");
      fromClauseUtils.when(() -> getFromClause(TEST_GROUP_BY_ENTITY_TYPE_DEFINITION, "tenant_01"))
        .thenReturn("TEST_GROUP_BY_ENTITY_TYPE");
      assertDoesNotThrow(() -> repo.getResultSet(entityTypeId, fields, listIds, tenantIds));
    }
  }

  @Test
  void shouldReturnEmptyListWhenPartialQueriesIsEmpty() {
    UUID entityTypeId = UUID.randomUUID();
    int limit = 100;
    Fql fql = new Fql("", new EqualsCondition(new FqlField("key1"), "value1"));
    List<String> fields = List.of("id", "key1");
    List<String> emptyTenantIds = List.of();

    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), eq(null), anyBoolean()))
      .thenReturn(ENTITY_TYPE);
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(entityTypeId), any(String.class), anyBoolean()))
      .thenReturn(ENTITY_TYPE);

    List<Map<String, Object>> actualList = repo.getResultSetSync(entityTypeId, fql, fields, limit, emptyTenantIds, false);

    assertEquals(List.of(), actualList);
  }

  @Test
  void recordToMapDecodesJsonbEncodedColumns() throws Exception {
    EntityType entityType = marcEntityType();

    PGobject jsonb = new PGobject();
    jsonb.setType("jsonb");
    jsonb.setValue("[\"UAB\", \"eng\"]");

    List<Map<String, Object>> mapped = invokeRecordToMap(entityType, singleRecordResult(jsonb));

    assertEquals(1, mapped.size());
    assertArrayEquals(new String[] {"UAB", "eng"}, (String[]) mapped.get(0).get("marc_245_a"));
  }

  @Test
  void recordToMapRemovesColumnWhenJsonbDecodingFails() throws Exception {
    EntityType entityType = marcEntityType();

    PGobject malformed = new PGobject();
    malformed.setType("jsonb");
    malformed.setValue("not-json");

    List<Map<String, Object>> mapped = invokeRecordToMap(entityType, singleRecordResult(malformed));

    assertEquals(1, mapped.size());
    assertFalse(mapped.get(0).containsKey("marc_245_a"));
  }

  @Test
  void recordToMapHandlesJsonbArrayColumnAndEmptyResult() throws Exception {
    // A JsonbArrayType column exercises the first instanceof branch; an empty result exercises the
    // result.isEmpty() branch of the ternary.
    EntityType entityType = new EntityType()
      .id(UUID.randomUUID().toString())
      .name("jsonb-array-et")
      .columns(List.of(
        new EntityTypeColumn().name("arr").dataType(new JsonbArrayType().dataType("jsonbArrayType"))
      ));

    DSLContext ctx = DSL.using(SQLDialect.POSTGRES);
    Result<Record> emptyResult = ctx.newResult(List.of(DSL.field(DSL.name("arr"))));

    List<Map<String, Object>> mapped = invokeRecordToMap(entityType, emptyResult);

    assertEquals(0, mapped.size());
  }

  @Test
  void recordToMapLeavesNonJsonbValuesUnchanged() throws Exception {
    EntityType entityType = marcEntityType();

    // Value is not a PGobject -> the "instanceof PGobject" check is false.
    List<Map<String, Object>> mappedString = invokeRecordToMap(entityType, singleRecordResult("plain-value"));
    assertEquals("plain-value", mappedString.get(0).get("marc_245_a"));

    // Value is a PGobject, but its type is not "jsonb" -> the "jsonb".equals(...) check is false.
    PGobject nonJsonb = new PGobject();
    nonJsonb.setType("text");
    nonJsonb.setValue("hello");
    List<Map<String, Object>> mappedPgobject = invokeRecordToMap(entityType, singleRecordResult(nonJsonb));
    assertEquals(nonJsonb, mappedPgobject.get(0).get("marc_245_a"));
  }

  private static EntityType marcEntityType() {
    return new EntityType()
      .id(UUID.randomUUID().toString())
      .name("marc-et")
      .columns(List.of(
        new EntityTypeColumn().name("marc_245_a").dataType(new MarcType().dataType("marcType"))
      ));
  }

  private static Result<Record> singleRecordResult(Object marcValue) {
    DSLContext ctx = DSL.using(SQLDialect.POSTGRES);
    Field<Object> marcField = DSL.field(DSL.name("marc_245_a"));
    List<Field<?>> fields = List.of(marcField);
    Result<Record> result = ctx.newResult(fields);
    Record record = ctx.newRecord(fields);
    record.set(marcField, marcValue);
    result.add(record);
    return result;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> invokeRecordToMap(EntityType entityType, Result<Record> result) throws Exception {
    Method method = ResultSetRepository.class.getDeclaredMethod("recordToMap", EntityType.class, Result.class);
    method.setAccessible(true);
    return (List<Map<String, Object>>) method.invoke(repo, entityType, result);
  }
}
