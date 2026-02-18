package org.folio.fqm.repository;

import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.domain.dto.QueryStatusSummary;
import org.folio.querytool.domain.dto.QueryIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.util.StringUtils.hasText;

@ActiveProfiles("db-test")
@SpringBootTest
class QueryRepositoryTest {

  @Autowired
  private QueryRepository repo;

  @Autowired
  private DataSource dataSource;

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

    Query expectedQuery = new Query(queryId, entityTypeId, "", fqlQuery, fields,
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
    Query query = new Query(queryId, entityTypeId, "", fqlQuery, fields,
      createdBy, OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null);
    repo.saveQuery(query);
    assertFalse(hasText(query.failureReason()));
    Query updatedQuery = new Query(queryId, entityTypeId, "", fqlQuery, fields,
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
    Query queryToDelete = new Query(queryIdToDelete, entityTypeId, "", fqlQuery, fields, createdBy,
      now.minusHours(2), null,QueryStatus.IN_PROGRESS, null);
    repo.saveQuery(queryToDelete);

    OffsetDateTime endDate = now.minusHours(1);
    repo.updateQuery(queryIdToDelete, QueryStatus.SUCCESS, endDate, null);

    Query queryToKeep = new Query(queryIdToKeep, UUID.randomUUID(), "", fqlQuery, fields,
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
    Query query = new Query(queryId, UUID.randomUUID(), "", fqlQuery, fields,
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

  @Test
  void shouldCancelRunningQuery() throws Exception {
    AtomicReference<SQLException> exception = new AtomicReference<>();
    Thread queryThread = new Thread(() -> {
      try (
        Connection conn = dataSource.getConnection();
        PreparedStatement statement = conn.prepareStatement("/* Query ID: " + queryId + " */ SELECT pg_sleep(30)")
      ) {
        statement.executeQuery();
      } catch (SQLException e) {
        exception.set(e);
      }
    });

    queryThread.start();
    Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> !repo.getSelectQueryPids(queryId).isEmpty());

    repo.cancelQueries(List.of(queryId));
    queryThread.join(5000);

    assertFalse(queryThread.isAlive());
    assertNotNull(exception.get(), "Expected SQLException due to query cancellation");
  }

  @Test
  void testGetStatusSummaries() {
    Query query = new Query(
      UUID.fromString("6f0656f1-649a-56dd-8d03-31aaa191b11d"),
      UUID.fromString("6c41058e-c719-5d74-8720-a8cefd85e48b"),
      "",
      "",
      List.of("a", "b", "c"),
      UUID.fromString("91c0465e-3ff9-5ff3-9a90-dc7d8a25851b"),
      OffsetDateTime.now(ZoneOffset.UTC),
      OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5),
      QueryStatus.QUEUED,
      null
    );
    repo.saveQuery(query);
    repo.updateQuery(query.queryId(), QueryStatus.QUEUED, query.endDate(), null);

    List<QueryStatusSummary> summaries = repo.getStatusSummaries();
    QueryStatusSummary summary = summaries.stream().filter(s -> s.getQueryId().equals(query.queryId())).findFirst().orElseThrow();
    assertEquals(query.queryId(), summary.getQueryId());
    assertEquals(QueryStatus.QUEUED.toString(), summary.getStatus());
    assertEquals(query.entityTypeId().toString(), summary.getEntityTypeId());
    assertTrue(query.startDate().toEpochSecond() - summary.getStartedAt().toInstant().atOffset(ZoneOffset.UTC).toEpochSecond() < 5);
    assertTrue(query.endDate().toEpochSecond() - summary.getEndedAt().toInstant().atOffset(ZoneOffset.UTC).toEpochSecond() < 5);
    assertEquals(query.fields().size(), summary.getNumFields());
    assertEquals(0, summary.getTotalRecords());
  }
}
