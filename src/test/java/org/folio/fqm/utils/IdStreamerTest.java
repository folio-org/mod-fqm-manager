package org.folio.fqm.utils;

import static org.folio.fqm.utils.IdStreamerTestDataProvider.TEST_ENTITY_TYPE_DEFINITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.folio.fql.model.EqualsCondition;
import org.folio.fql.model.Fql;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.repository.IdStreamer;
import org.folio.fqm.repository.QueryDetailsRepository;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * NOTE - Tests in this class depends on the mock results returned from {@link IdStreamerTestDataProvider} class
 */
class IdStreamerTest {

  private static final UUID ENTITY_TYPE_ID = UUID.randomUUID();

  private IdStreamer idStreamer;

  @BeforeEach
  void setup() {
    DSLContext context = DSL.using(
      new MockConnection(new IdStreamerTestDataProvider()),
      SQLDialect.POSTGRES
    );

    EntityTypeRepository entityTypeRepository = new EntityTypeRepository(
      context,
      new ObjectMapper()
    );
    this.idStreamer =
      new IdStreamer(
        context,
        entityTypeRepository,
        new QueryDetailsRepository(context)
      );
  }

  @Test
  void shouldFetchIdStreamForQueryId() {
    UUID queryId = UUID.randomUUID();
    List<UUID> actualList = new ArrayList<>();
    Consumer<IdsWithCancelCallback> idsConsumer = idsWithCancelCallback ->
      actualList.addAll(idsWithCancelCallback.ids());
    int idsCount = idStreamer.streamIdsInBatch(
      queryId,
      true,
      2,
      idsConsumer
    );
    assertEquals(
      IdStreamerTestDataProvider.TEST_CONTENT_IDS,
      actualList,
      "Expected List should equal Actual List"
    );
    assertEquals(IdStreamerTestDataProvider.TEST_CONTENT_IDS.size(), idsCount);
  }

  @Test
  void shouldFetchIdStreamForFql() {
    Fql fql = new Fql(new EqualsCondition("field1", "value1"));
    List<UUID> actualList = new ArrayList<>();
    Consumer<IdsWithCancelCallback> idsConsumer = idsWithCancelCallback ->
      actualList.addAll(idsWithCancelCallback.ids());
    int idsCount = idStreamer.streamIdsInBatch(
      ENTITY_TYPE_ID,
      true,
      fql,
      2,
      idsConsumer
    );
    assertEquals(
      IdStreamerTestDataProvider.TEST_CONTENT_IDS,
      actualList,
      "Expected List should equal Actual List"
    );
    assertEquals(IdStreamerTestDataProvider.TEST_CONTENT_IDS.size(), idsCount);
  }

  @Test
  void shouldGetSortedIds() {
    UUID queryId = UUID.randomUUID();
    int offset = 0;
    int limit = 0;
    String derivedTableName = "query_results";
    List<UUID> actualIds = idStreamer.getSortedIds(
      derivedTableName,
      offset,
      limit,
      queryId
    );
    // Disabled for temporary workaround - This should assert that it's equal to IdStreamerTestDataProvider.TEST_CONTENT_IDS
    assertEquals(
      TEST_ENTITY_TYPE_DEFINITION.getId(),
      actualIds.get(0).toString()
    );
  }

  @Test
  void shouldThrowExceptionWhenEntityTypeNotFound() {
    Fql fql = new Fql(new EqualsCondition("field", "value"));
    Consumer<IdsWithCancelCallback> noop = idsWithCancelCallback -> {};
    EntityTypeRepository mockRepository = mock(EntityTypeRepository.class);
    IdStreamer idStreamerWithMockRepo = new IdStreamer(null, mockRepository, null);

    when(mockRepository.getEntityTypeDefinition(ENTITY_TYPE_ID))
      .thenReturn(Optional.empty());

    assertThrows(
      EntityTypeNotFoundException.class,
      () ->
        idStreamerWithMockRepo.streamIdsInBatch(
          ENTITY_TYPE_ID,
          true,
          fql,
          1,
          noop
        )
    );
  }
}
