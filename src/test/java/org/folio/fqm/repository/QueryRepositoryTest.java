package org.folio.fqm.repository;

import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.querytool.domain.dto.QueryIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.util.StringUtils.hasText;

@ActiveProfiles("db-test")
@SpringBootTest
class QueryRepositoryTest {

  @Autowired
  private QueryRepository repo;

  private UUID queryId;

  @BeforeEach
  public void setUp() {
    queryId = UUID.randomUUID();
  }

  @AfterEach
  public void cleanUp() {
    repo.deleteQueries(List.of(queryId));
  }

  @Test
  void shouldSaveQueryDetails() {
    UUID entityTypeId = UUID.randomUUID();
    UUID createdBy = UUID.randomUUID();
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    List<String> fields = List.of("id", "field1", "field2");

    Query expectedQuery = new Query(queryId, entityTypeId, fqlQuery, fields,
      createdBy, OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null);
    QueryIdentifier queryIdentifier = repo.saveQuery(expectedQuery);
    Query actualQuery = repo.getQuery(queryIdentifier.getQueryId(), false).orElse(null);
    assertEquals(expectedQuery.queryId(), actualQuery.queryId());
    assertEquals(expectedQuery.entityTypeId(), actualQuery.entityTypeId());
    assertEquals(expectedQuery.createdBy(), actualQuery.createdBy());
    assertEquals(expectedQuery.fqlQuery(), actualQuery.fqlQuery());
    assertEquals(expectedQuery.fields(), actualQuery.fields());
    assertEquals(expectedQuery.status(), actualQuery.status());
  }

  @Test
  void shouldUpdateQuery() {
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    List<String> fields = List.of("id", "field1", "field2");
    UUID createdBy = UUID.randomUUID();
    UUID entityTypeId = UUID.randomUUID();
    OffsetDateTime endDate = OffsetDateTime.now();
    Query query = new Query(queryId, entityTypeId, fqlQuery, fields,
      createdBy, OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null);
    repo.saveQuery(query);
    assertFalse(hasText(query.failureReason()));
    Query updatedQuery = new Query(queryId, entityTypeId, fqlQuery, fields,
      createdBy, null, endDate, QueryStatus.FAILED, "something went wrong");
    repo.updateQuery(updatedQuery.queryId(), updatedQuery.status(), updatedQuery.endDate(), updatedQuery.failureReason());
    assertEquals("FAILED", updatedQuery.status().toString());
    assertEquals("something went wrong", updatedQuery.failureReason());
    assertNotNull(updatedQuery.endDate());
  }

  @Test
  void shouldGetQueriesForDeletion() throws InterruptedException {
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    List<String> fields = List.of("id", "field1");
    Query queryToDelete = new Query(queryId, UUID.randomUUID(), fqlQuery, fields,
      UUID.randomUUID(), OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null);
    repo.saveQuery(queryToDelete);
    Query updatedQuery = new Query(queryId, UUID.randomUUID(), fqlQuery, fields,
      UUID.randomUUID(), null, OffsetDateTime.now(), QueryStatus.SUCCESS, null);

    UUID queryId2 = UUID.randomUUID();
    Query queryToNotDelete = new Query(queryId2, UUID.randomUUID(), fqlQuery, fields,
      UUID.randomUUID(), OffsetDateTime.now().plusHours(1), null, QueryStatus.IN_PROGRESS, null);
    repo.saveQuery(queryToNotDelete);

    repo.updateQuery(updatedQuery.queryId(), updatedQuery.status(), updatedQuery.endDate(), updatedQuery.failureReason());
    UUID expectedId = queryId;
    List<UUID> actualIds = repo.getQueryIdsForDeletion(Duration.ofMillis(0));

    assertTrue(actualIds.contains(expectedId));
    assertFalse(actualIds.contains(queryId2));

    // Clean up
    repo.deleteQueries(List.of(queryId2));
  }

  @Test
  void shouldDeleteQueries() {
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    List<String> fields = List.of("id", "field1");
    Query query = new Query(queryId, UUID.randomUUID(), fqlQuery, fields,
      UUID.randomUUID(), OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null);
    QueryIdentifier queryIdentifier = repo.saveQuery(query);
    assertFalse(repo.getQuery(queryIdentifier.getQueryId(), false).isEmpty());
    repo.deleteQueries(List.of(queryId));
    assertTrue(repo.getQuery(queryIdentifier.getQueryId(), false).isEmpty());
  }
}
