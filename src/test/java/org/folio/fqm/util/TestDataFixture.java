package org.folio.fqm.util;

import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.querytool.domain.dto.QueryIdentifier;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class TestDataFixture {

  private static final String EXAMPLE_FQL = "{\"field1\": {\"$in\": [\"value1\", \"value2\", \"value3\", \"value4\", \"value5\" ] }}";

  private static final UUID EXAMPLE_QUERY_IDENTIFIER = UUID.randomUUID();

  private static final QueryIdentifier mockQueryIdentifier = new QueryIdentifier().queryId(EXAMPLE_QUERY_IDENTIFIER);

  private static final Query mockQuery = new Query(UUID.randomUUID(), UUID.randomUUID(), EXAMPLE_FQL, List.of("id", "field1", "field2"),
    UUID.randomUUID(), OffsetDateTime.now(), null, QueryStatus.IN_PROGRESS, null);

  public static Query getMockQuery(QueryStatus status) {
    return new Query(mockQuery.queryId(), mockQuery.entityTypeId(), mockQuery.fqlQuery(), mockQuery.fields(),
      mockQuery.createdBy(), mockQuery.startDate(), mockQuery.endDate(), status, mockQuery.failureReason());
  }

  public static Query getMockQuery(List<String> fields) {
    return new Query(mockQuery.queryId(), mockQuery.entityTypeId(), mockQuery.fqlQuery(), fields,
      mockQuery.createdBy(), mockQuery.startDate(), mockQuery.endDate(), QueryStatus.IN_PROGRESS, mockQuery.failureReason());
  }

  private static final List<UUID> mockResultIds = List.of(UUID.randomUUID(), UUID.randomUUID());

  public static QueryIdentifier getMockQueryIdentifier() {
    return mockQueryIdentifier;
  }

  public static Query getMockQuery() {
    return mockQuery;
  }

  public static List<UUID> getMockResultIds() {
    return mockResultIds;
  }

  public static List<UUID> getQueryIds() {
    return List.of(mockQuery.queryId());
  }
}
