package org.folio.fqm.testutil;

import static org.folio.fqm.repository.EntityTypeRepository.ID_FIELD_NAME;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeDefaultSort;
import org.folio.querytool.domain.dto.QueryIdentifier;
import org.folio.querytool.domain.dto.StringType;

public class TestDataFixture {

  private static final String EXAMPLE_FQL =
    "{\"field1\": {\"$in\": [\"value1\", \"value2\", \"value3\", \"value4\", \"value5\" ] }}";

  private static final UUID EXAMPLE_QUERY_IDENTIFIER = UUID.randomUUID();

  private static final QueryIdentifier mockQueryIdentifier = new QueryIdentifier()
    .queryId(EXAMPLE_QUERY_IDENTIFIER);

  private static final Query mockQuery = new Query(
    UUID.randomUUID(),
    UUID.randomUUID(),
    EXAMPLE_FQL,
    List.of("id", "field1", "field2"),
    UUID.randomUUID(),
    OffsetDateTime.now(),
    null,
    QueryStatus.IN_PROGRESS,
    null
  );

  public static Query getMockQuery(QueryStatus status) {
    return new Query(
      mockQuery.queryId(),
      mockQuery.entityTypeId(),
      mockQuery.fqlQuery(),
      mockQuery.fields(),
      mockQuery.createdBy(),
      mockQuery.startDate(),
      mockQuery.endDate(),
      status,
      mockQuery.failureReason()
    );
  }

  public static Query getMockQuery(List<String> fields) {
    return new Query(
      mockQuery.queryId(),
      mockQuery.entityTypeId(),
      mockQuery.fqlQuery(),
      fields,
      mockQuery.createdBy(),
      mockQuery.startDate(),
      mockQuery.endDate(),
      QueryStatus.IN_PROGRESS,
      mockQuery.failureReason()
    );
  }

  private static final List<UUID> mockResultIds = List.of(
    UUID.randomUUID(),
    UUID.randomUUID()
  );

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

  private static final String derivedTableName = "testTable";

  private static final List<Map<String, Object>> entityContents = List.of(
    Map.of(
      ID_FIELD_NAME,
      UUID.randomUUID(),
      "key1",
      "value1",
      "key2",
      "value2"
    ),
    Map.of(
      ID_FIELD_NAME,
      UUID.randomUUID(),
      "key1",
      "value3",
      "key2",
      "value4"
    ),
    Map.of(ID_FIELD_NAME, UUID.randomUUID(), "key1", "value5", "key2", "value6")
  );

  private static final EntityTypeColumn col1 = new EntityTypeColumn()
    .name(ID_FIELD_NAME)
    .dataType(new StringType().dataType("stringType"))
    .labelAlias("column_001_label")
    .visibleByDefault(false);

  private static final EntityTypeColumn col2 = new EntityTypeColumn()
    .name("key1")
    .dataType(new StringType().dataType("stringType"))
    .labelAlias("key1_label")
    .visibleByDefault(false);

  private static final EntityTypeColumn col3 = new EntityTypeColumn()
    .name("key2")
    .dataType(new StringType().dataType("stringType"))
    .labelAlias("key2_label")
    .visibleByDefault(false)
    .ecsOnly(true);

  private static final EntityTypeDefaultSort sort = new EntityTypeDefaultSort()
    .columnName(col1.getName());

  private static final EntityType mockDefinition = new EntityType()
    .id(UUID.randomUUID().toString())
    .name(derivedTableName)
    .labelAlias("derived_table_alias_01")
    .columns(List.of(col1, col2, col3))
    .defaultSort(List.of(sort));

  public static List<Map<String, Object>> getEntityContents() {
    return entityContents;
  }

  public static EntityType getEntityDefinition() {
    return mockDefinition;
  }

  public static EntityTypeColumn column1() {
    return col1;
  }

  public static EntityTypeColumn column2() {
    return col2;
  }

  public static EntityTypeColumn column3() {
    return col3;
  }
}
