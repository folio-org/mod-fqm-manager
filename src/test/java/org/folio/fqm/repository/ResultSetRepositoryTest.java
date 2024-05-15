package org.folio.fqm.repository;

import org.folio.fql.model.EqualsCondition;
import org.folio.fql.model.Fql;
import org.folio.fql.model.field.FqlField;
import org.folio.fqm.service.EntityTypeFlatteningService;
import org.folio.fqm.service.LocalizationService;
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

import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NOTE - Tests in this class depends on the mock results returned from {@link ResultSetRepositoryTestDataProvider} class
 */
class ResultSetRepositoryTest {

  private ResultSetRepository repo;

  @BeforeEach
  void setup() {
    DSLContext readerContext = DSL.using(new MockConnection(
      new ResultSetRepositoryTestDataProvider()), SQLDialect.POSTGRES);
    DSLContext context = DSL.using(new MockConnection(
      new ResultSetRepositoryTestDataProvider()), SQLDialect.POSTGRES);
    LocalizationService localizationService = mock(LocalizationService.class);

    ObjectMapper objectMapper = new ObjectMapper();
    EntityTypeRepository entityTypeRepository = new EntityTypeRepository(readerContext, context, objectMapper);
    EntityTypeFlatteningService entityTypeFlatteningService = new EntityTypeFlatteningService(entityTypeRepository, objectMapper, localizationService);
    this.repo = new ResultSetRepository(context, entityTypeFlatteningService);
  }

  @Test
  void getResultSetWithNullFieldsShouldReturnEmptyList() {
    List<List<String>> listIds = List.of(
      List.of(UUID.randomUUID().toString()),
      List.of(UUID.randomUUID().toString())
    );
    List<Map<String, Object>> expectedList = List.of();
    List<Map<String, Object>> actualList = repo.getResultSet(UUID.randomUUID(), null, listIds);
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
    List<Map<String, Object>> actualList = repo.getResultSet(UUID.randomUUID(), fields, listIds);
    assertEquals(expectedList, actualList);
  }

  @Test
  void getResultSetShouldReturnResultsWithRequestedFields() {
    List<List<String>> listIds = List.of(
      List.of(UUID.randomUUID().toString()),
      List.of(UUID.randomUUID().toString()),
      List.of(UUID.randomUUID().toString())
    );
    List<String> fields = List.of("source1_id", "source1_key1");
    List<Map<String, Object>> expectedFullList = ResultSetRepositoryTestDataProvider.TEST_ENTITY_CONTENTS;
    // Since we are only asking for "id" and "key2" fields, create expected list without key1 included
    List<Map<String, Object>> expectedList = List.of(
      Map.of("source1_id", expectedFullList.get(0).get("id"), "source1_key1", "value1"),
      Map.of("source1_id", expectedFullList.get(1).get("id"), "source1_key1", "value3"),
      Map.of("source1_id", expectedFullList.get(2).get("id"), "source1_key1", "value5")
    );
    List<Map<String, Object>> actualList = repo.getResultSet(UUID.randomUUID(), fields, listIds);
    assertEquals(expectedList, actualList);
  }

  @Test
  void shouldRunSynchronousQueryAndReturnContents() {
    UUID entityTypeId = UUID.randomUUID();
    List<String> afterId = List.of(UUID.randomUUID().toString());
    int limit = 100;
    Fql fql = new Fql(new EqualsCondition(new FqlField("source1_key1"), "value1"));
    List<String> fields = List.of("source1_id", "source1_key1");
    List<Map<String, Object>> expectedFullList = ResultSetRepositoryTestDataProvider.TEST_ENTITY_CONTENTS;
    // Since we are only asking for "id" and "key1" fields, create expected list without key2 included
    List<Map<String, Object>> expectedList = List.of(
      Map.of("source1_id", expectedFullList.get(0).get("id"), "source1_key1", "value1"),
      Map.of("source1_id", expectedFullList.get(1).get("id"), "source1_key1", "value3"),
      Map.of("source1_id", expectedFullList.get(2).get("id"), "source1_key1", "value5")
    );
    List<Map<String, Object>> actualList = repo.getResultSet(entityTypeId, fql, fields, afterId, limit);
    assertEquals(expectedList, actualList);
  }

  @Test
  void shouldReturnEmptyResultSetForSynchronousQueryWithEmptyFields() {
    UUID entityTypeId = UUID.randomUUID();
    List<String> afterId = List.of(UUID.randomUUID().toString());
    int limit = 100;
    Fql fql = new Fql(new EqualsCondition(new FqlField("key1"), "value1"));
    List<String> fields = List.of();
    List<Map<String, Object>> expectedList = List.of();
    List<Map<String, Object>> actualList = repo.getResultSet(entityTypeId, fql, fields, afterId, limit);
    assertEquals(expectedList, actualList);
  }

  @Test
  void shouldReturnEmptyResultSetForSynchronousQueryWithNullFields() {
    UUID entityTypeId = UUID.randomUUID();
    List<String> afterId = List.of(UUID.randomUUID().toString());
    int limit = 100;
    Fql fql = new Fql(new EqualsCondition(new FqlField("key1"), "value1"));
    List<Map<String, Object>> expectedList = List.of();
    List<Map<String, Object>> actualList = repo.getResultSet(entityTypeId, fql, null, afterId, limit);
    assertEquals(expectedList, actualList);
  }

  @Test
  void shouldRunSynchronousQueryAndHandleNullAfterIdParameter() {
    UUID entityTypeId = UUID.randomUUID();
    int limit = 100;
    Fql fql = new Fql(new EqualsCondition(new FqlField("source1_key1"), "value1"));
    List<String> fields = List.of("source1_id", "source1_key1", "source1_key2");
    List<Map<String, Object>> expectedList = List.of(
      Map.of("source1_id", ResultSetRepositoryTestDataProvider.TEST_ENTITY_CONTENTS.get(0).get("id"), "source1_key1", "value1", "source1_key2", "value2"),
      Map.of("source1_id", ResultSetRepositoryTestDataProvider.TEST_ENTITY_CONTENTS.get(1).get("id"), "source1_key1", "value3", "source1_key2", "value4"),
      Map.of("source1_id", ResultSetRepositoryTestDataProvider.TEST_ENTITY_CONTENTS.get(2).get("id"), "source1_key1", "value5", "source1_key2", "value6")
    );
    List<Map<String, Object>> actualList = repo.getResultSet(entityTypeId, fql, fields, null, limit);
    assertEquals(expectedList, actualList);
  }
}
