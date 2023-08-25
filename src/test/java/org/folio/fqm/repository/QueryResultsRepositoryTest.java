package org.folio.fqm.repository;

import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("db-test")
@SpringBootTest
class QueryResultsRepositoryTest {

  @Autowired
  private QueryResultsRepository queryResultsRepository;

  @Autowired
  private QueryRepository queryRepository;

  @Test
  void shouldSaveQueryResults() {
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    List<String> fields = List.of("id", "field1");
    UUID queryId = UUID.randomUUID();
    List<UUID> expectedResultIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    Query query = new Query(queryId, UUID.randomUUID(), fqlQuery, fields,
      UUID.randomUUID(), OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null);
    queryRepository.saveQuery(query);
    queryResultsRepository.saveQueryResults(queryId, expectedResultIds);
    List<UUID> actualResultIds = queryResultsRepository.getQueryResultIds(queryId, 0, 100);
    assertThat(expectedResultIds).containsExactlyInAnyOrderElementsOf(actualResultIds);
  }

  @Test
  void shouldGetQueryResultCount() {
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    List<String> fields = List.of("id", "field1");
    UUID queryId = UUID.randomUUID();
    List<UUID> resultIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    Query mockQuery = new Query(queryId, UUID.randomUUID(), fqlQuery, fields,
      UUID.randomUUID(), OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null);
    queryRepository.saveQuery(mockQuery);
    queryResultsRepository.saveQueryResults(queryId, resultIds);
    int actualCount = queryResultsRepository.getQueryResultsCount(queryId);
    assertEquals(resultIds.size(), actualCount);
  }

  @Test
  void shouldDeleteQueryResults() {
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    List<String> fields = List.of("id", "field1");
    UUID queryId = UUID.randomUUID();
    List<UUID> resultIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    Query mockQuery = new Query(queryId, UUID.randomUUID(), fqlQuery, fields,
      UUID.randomUUID(), OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null);
    queryRepository.saveQuery(mockQuery);
    queryResultsRepository.saveQueryResults(queryId, resultIds);
    assertFalse(queryResultsRepository.getQueryResultIds(queryId, 0, 100).isEmpty());
    queryResultsRepository.deleteQueryResults(List.of(queryId));
    assertTrue(queryResultsRepository.getQueryResultIds(queryId, 0, 100).isEmpty());
  }
}
