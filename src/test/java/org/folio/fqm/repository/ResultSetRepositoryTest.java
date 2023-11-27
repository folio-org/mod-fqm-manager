package org.folio.fqm.repository;

import org.folio.fql.model.EqualsCondition;
import org.folio.fql.model.Fql;
import org.folio.fql.service.FqlService;
import org.folio.fqm.service.FqlToSqlConverterService;
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
 * NOTE - Tests in this class depends on the mock results returned from {@link ResultSetRepositoryTestDataProvider} class
 */
class ResultSetRepositoryTest {

  private ResultSetRepository repo;

  @BeforeEach
  void setup() {
    DSLContext context = DSL.using(new MockConnection(
      new ResultSetRepositoryTestDataProvider()), SQLDialect.POSTGRES);

    EntityTypeRepository entityTypeRepository = new EntityTypeRepository(context, new ObjectMapper());
    this.repo = new ResultSetRepository(context, entityTypeRepository, new FqlToSqlConverterService(new FqlService()));
  }

  @Test
  void getResultSetWithNullFieldsShouldReturnEmptyList() {
    List<UUID> listIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    List<Map<String, Object>> expectedList = List.of();
    List<Map<String, Object>> actualList = repo.getResultSet(UUID.randomUUID(), null, listIds);
    assertEquals(expectedList, actualList);
  }

  @Test
  void getResultSetWithEmptyFieldsShouldEmptyList() {
    List<UUID> listIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    List<String> fields = List.of();
    List<Map<String, Object>> expectedList = List.of();
    List<Map<String, Object>> actualList = repo.getResultSet(UUID.randomUUID(), fields, listIds);
    assertEquals(expectedList, actualList);
  }

  @Test
  void getResultSetShouldReturnResultsWithRequestedFields() {
    List<UUID> listIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    List<String> fields = List.of("id", "key1");
    List<Map<String, Object>> expectedFullList = ResultSetRepositoryTestDataProvider.TEST_ENTITY_CONTENTS;
    // Since we are only asking for "id" and "key2" fields, create expected list without key1 included
    List<Map<String, Object>> expectedList = List.of(
      Map.of("id", expectedFullList.get(0).get("id"), "key1", "value1"),
      Map.of("id", expectedFullList.get(1).get("id"), "key1", "value3"),
      Map.of("id", expectedFullList.get(2).get("id"), "key1", "value5")
    );
    List<Map<String, Object>> actualList = repo.getResultSet(UUID.randomUUID(), fields, listIds);
    assertEquals(expectedList, actualList);
  }

  @Test
  void shouldRunSynchronousQueryAndReturnContents() {
    UUID entityTypeId = UUID.randomUUID();
    UUID afterId = UUID.randomUUID();
    int limit = 100;
    Fql fql = new Fql(new EqualsCondition("key1", "value1"));
    List<String> fields = List.of("id", "key1");
    List<Map<String, Object>> expectedFullList = ResultSetRepositoryTestDataProvider.TEST_ENTITY_CONTENTS;
    // Since we are only asking for "id" and "key1" fields, create expected list without key2 included
    List<Map<String, Object>> expectedList = List.of(
      Map.of("id", expectedFullList.get(0).get("id"), "key1", "value1"),
      Map.of("id", expectedFullList.get(1).get("id"), "key1", "value3"),
      Map.of("id", expectedFullList.get(2).get("id"), "key1", "value5")
    );
    List<Map<String, Object>> actualList = repo.getResultSet(entityTypeId, fql, fields, afterId, limit);
    assertEquals(expectedList, actualList);
  }

  @Test
  void shouldReturnEmptyResultSetForSynchronousQueryWithEmptyFields() {
    UUID entityTypeId = UUID.randomUUID();
    UUID afterId = UUID.randomUUID();
    int limit = 100;
    Fql fql = new Fql(new EqualsCondition("key1", "value1"));
    List<String> fields = List.of();
    List<Map<String, Object>> expectedList = List.of();
    List<Map<String, Object>> actualList = repo.getResultSet(entityTypeId, fql, fields, afterId, limit);
    assertEquals(expectedList, actualList);
  }

  @Test
  void shouldReturnEmptyResultSetForSynchronousQueryWithNullFields() {
    UUID entityTypeId = UUID.randomUUID();
    UUID afterId = UUID.randomUUID();
    int limit = 100;
    Fql fql = new Fql(new EqualsCondition("key1", "value1"));
    List<Map<String, Object>> expectedList = List.of();
    List<Map<String, Object>> actualList = repo.getResultSet(entityTypeId, fql, null, afterId, limit);
    assertEquals(expectedList, actualList);
  }

  @Test
  void shouldRunSynchronousQueryAndHandleNullAfterIdParameter() {
    UUID entityTypeId = UUID.randomUUID();
    int limit = 100;
    Fql fql = new Fql(new EqualsCondition("key1", "value1"));
    List<String> fields = List.of("id", "key1", "key2");
    List<Map<String, Object>> actualList = repo.getResultSet(entityTypeId, fql, fields, null, limit);
    assertEquals(ResultSetRepositoryTestDataProvider.TEST_ENTITY_CONTENTS, actualList);
  }
}
