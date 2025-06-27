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
import java.time.ZoneOffset;
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
    Query actualQuery = repo.getQuery(queryIdentifier.getQueryId(), false).get();
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
  void shouldGetQueriesForDeletion() {
    String fqlQuery = """
    {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5"] }}
    """;
    List<String> fields = List.of("id", "field1");

    UUID queryIdToDelete = UUID.randomUUID();
    UUID queryIdToKeep = UUID.randomUUID();
    UUID entityTypeId = UUID.randomUUID();
    UUID createdBy = UUID.randomUUID();

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    Query queryToDelete = new Query(queryIdToDelete, entityTypeId, fqlQuery, fields, createdBy, now.minusHours(2), null,
      QueryStatus.IN_PROGRESS, null);
    repo.saveQuery(queryToDelete);

    OffsetDateTime endDate = now.minusHours(1);
    repo.updateQuery(queryIdToDelete, QueryStatus.SUCCESS, endDate, null);

    Query queryToKeep = new Query(queryIdToKeep, UUID.randomUUID(), fqlQuery, fields,
      UUID.randomUUID(), now.plusMinutes(10), null, QueryStatus.IN_PROGRESS, null);
    repo.saveQuery(queryToKeep);

    List<UUID> actualIds = repo.getQueryIdsForDeletion(Duration.ofMinutes(1));

    assertTrue(actualIds.contains(queryIdToDelete), "Expected queryIdToDelete to be eligible for deletion");
    assertFalse(actualIds.contains(queryIdToKeep), "Expected queryIdToKeep to not be eligible for deletion");

    repo.deleteQueries(List.of(queryIdToDelete, queryIdToKeep));
  }

  @Test
  void shouldDeleteQueries() {
    OffsetDateTime testNowUtc = OffsetDateTime.now(ZoneOffset.UTC);
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    List<String> fields = List.of("id", "field1");
    Query query = new Query(queryId, UUID.randomUUID(), fqlQuery, fields,
      UUID.randomUUID(), testNowUtc, null, QueryStatus.IN_PROGRESS, null);
    QueryIdentifier queryIdentifier = repo.saveQuery(query);
    assertFalse(repo.getQuery(queryIdentifier.getQueryId()).isEmpty());
    repo.deleteQueries(List.of(queryId));
    assertTrue(repo.getQuery(queryIdentifier.getQueryId()).isEmpty());
  }

  @Test
  void shouldGetQueryPids() {
    assertNotNull(repo.getSelectQueryPids(queryId));
    assertNotNull(repo.getInsertQueryPids(queryId));
  }
}
