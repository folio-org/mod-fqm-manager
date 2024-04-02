package org.folio.fqm.utils;

import static org.folio.fqm.utils.IdStreamerTestDataProvider.TEST_CONTENT_IDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.folio.fql.model.EqualsCondition;
import org.folio.fql.model.Fql;
import org.folio.fql.model.field.FqlField;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.repository.IdStreamer;
import org.folio.fqm.repository.QueryDetailsRepository;
import org.folio.fqm.service.EntityTypeFlatteningService;
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
  EntityTypeFlatteningService entityTypeFlatteningService;

  @BeforeEach
  void setup() {
    DSLContext readerContext = DSL.using(
      new MockConnection(new IdStreamerTestDataProvider()),
      SQLDialect.POSTGRES
    );
    DSLContext context = DSL.using(
      new MockConnection(new IdStreamerTestDataProvider()),
      SQLDialect.POSTGRES
    );

    EntityTypeRepository entityTypeRepository = new EntityTypeRepository(
      readerContext,
      context,
      new ObjectMapper()
    );
    entityTypeFlatteningService = new EntityTypeFlatteningService(entityTypeRepository, new ObjectMapper());
    this.idStreamer =
      new IdStreamer(

        context,
        new QueryDetailsRepository(context),
        entityTypeFlatteningService
      );
  }

  @Test
  void shouldFetchIdStreamForQueryId() {
    UUID queryId = UUID.randomUUID();
    List<List<String>> expectedIds = new ArrayList<>();
    TEST_CONTENT_IDS.forEach(contentId -> expectedIds.add(List.of(contentId.toString())));
    List<List<String>> actualIds = new ArrayList<>();
    Consumer<IdsWithCancelCallback> idsConsumer = idsWithCancelCallback -> {
      List<String[]> ids = idsWithCancelCallback.ids();
      ids.forEach(idSet -> actualIds.add(Arrays.asList(idSet)));
    };
    int idsCount = idStreamer.streamIdsInBatch(
      queryId,
      true,
      2,
      idsConsumer
    );
    assertEquals(expectedIds, actualIds);
    assertEquals(IdStreamerTestDataProvider.TEST_CONTENT_IDS.size(), idsCount);
  }

  @Test
  void shouldFetchIdStreamForFql() {
    Fql fql = new Fql(new EqualsCondition(new FqlField("source1_field1"), "value1"));
    List<List<String>> expectedIds = new ArrayList<>();
    TEST_CONTENT_IDS.forEach(contentId -> expectedIds.add(List.of(contentId.toString())));
    List<List<String>> actualIds = new ArrayList<>();
    Consumer<IdsWithCancelCallback> idsConsumer = idsWithCancelCallback -> {
      List<String[]> ids = idsWithCancelCallback.ids();
      ids.forEach(idSet -> actualIds.add(Arrays.asList(idSet)));
    };
    int idsCount = idStreamer.streamIdsInBatch(
      ENTITY_TYPE_ID,
      true,
      fql,
      2,
      idsConsumer
    );
    assertEquals(expectedIds, actualIds);
    assertEquals(IdStreamerTestDataProvider.TEST_CONTENT_IDS.size(), idsCount);
  }

  @Test
  void shouldGetSortedIds() {
    UUID queryId = UUID.randomUUID();
    int offset = 0;
    int limit = 0;
    String derivedTableName = "query_results";
    List<List<String>> expectedIds = new ArrayList<>();
    TEST_CONTENT_IDS.forEach(contentId -> expectedIds.add(List.of(contentId.toString())));
    List<List<String>> actualIds = idStreamer.getSortedIds(
      derivedTableName,
      offset,
      limit,
      queryId
    );
    assertEquals(expectedIds, actualIds);
  }

  // TODO: this test is kind of butchered. Clean up if possible
  @Test
  void shouldThrowExceptionWhenEntityTypeNotFound() {
    Fql fql = new Fql(new EqualsCondition(new FqlField("field"), "value"));
    Consumer<IdsWithCancelCallback> noop = idsWithCancelCallback -> {
    };
    EntityTypeRepository mockRepository = mock(EntityTypeRepository.class);


    when(mockRepository.getEntityTypeDefinition(ENTITY_TYPE_ID))
      .thenReturn(Optional.empty());

    entityTypeFlatteningService = new EntityTypeFlatteningService(mockRepository, new ObjectMapper());

    IdStreamer idStreamerWithMockRepo = new IdStreamer(null,null, entityTypeFlatteningService);

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
