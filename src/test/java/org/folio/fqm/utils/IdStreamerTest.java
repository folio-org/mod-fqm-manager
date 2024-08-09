package org.folio.fqm.utils;

import static org.folio.fqm.utils.IdStreamerTestDataProvider.TEST_CONTENT_IDS;
import static org.folio.fqm.utils.IdStreamerTestDataProvider.TEST_GROUP_BY_ENTITY_TYPE_DEFINITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.folio.fql.model.EqualsCondition;
import org.folio.fql.model.Fql;
import org.folio.fql.model.field.FqlField;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.repository.IdStreamer;
import org.folio.fqm.service.CrossTenantQueryService;
import org.folio.fqm.service.EntityTypeFlatteningService;
import org.folio.fqm.service.LocalizationService;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * NOTE - Tests in this class depends on the mock results returned from {@link IdStreamerTestDataProvider} class
 */
@RunWith(MockitoJUnitRunner.class)
class IdStreamerTest {

  private IdStreamer idStreamer;
  private LocalizationService localizationService;
  private FolioExecutionContext executionContext;
  private SimpleHttpClient ecsClient;

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
    localizationService = mock(LocalizationService.class);
    ecsClient = mock(SimpleHttpClient.class);
    executionContext = mock(FolioExecutionContext.class);
    EntityTypeFlatteningService entityTypeFlatteningService = new EntityTypeFlatteningService(entityTypeRepository, new ObjectMapper(), localizationService, ecsClient);
    CrossTenantQueryService crossTenantQueryService = new CrossTenantQueryService(ecsClient, executionContext, entityTypeFlatteningService, null);
    this.idStreamer =
      new IdStreamer(
        context,
        entityTypeFlatteningService,
        crossTenantQueryService,
        mock(FolioExecutionContext.class)
      );
  }

  @Test
  void shouldFetchIdStreamForFql() {
    Fql fql = new Fql("", new EqualsCondition(new FqlField("field1"), "value1"));
    List<List<String>> expectedIds = new ArrayList<>();
    TEST_CONTENT_IDS.forEach(contentId -> expectedIds.add(List.of(contentId.toString())));
    List<List<String>> actualIds = new ArrayList<>();
    Consumer<IdsWithCancelCallback> idsConsumer = idsWithCancelCallback -> {
      List<String[]> ids = idsWithCancelCallback.ids();
      ids.forEach(idSet -> actualIds.add(Arrays.asList(idSet)));
    };
    when(localizationService.localizeEntityType(any(EntityType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(executionContext.getTenantId()).thenReturn("tenant_01");
    int idsCount = idStreamer.streamIdsInBatch(
      IdStreamerTestDataProvider.TEST_ENTITY_TYPE_DEFINITION,
      true,
      fql,
      2,
      idsConsumer
    );
    assertEquals(expectedIds, actualIds);
    assertEquals(IdStreamerTestDataProvider.TEST_CONTENT_IDS.size(), idsCount);
  }

  @Test
  void shouldUseUnionAllForCrossTenantQuery() {
    String configurationJson = """
        {
          "centralTenantId": "tenant_01"
        }
      """;
    String consortiaJson = """
        {
          "consortia": [
            {
              "id": "bdaa4720-5e11-4632-bc10-d4455cf252df"
            }
          ]
        }
      """;
    String tenantJson = """
      {
        "tenants": [
          {
            "id": "tenant_01"
          },
          {
            "id": "tenant_02"
          },
          {
            "id": "tenant_03"
          },
        ]
      }
      """;
    when(executionContext.getTenantId()).thenReturn("tenant_01");
    when(ecsClient.get("consortia-configuration", Map.of("limit", String.valueOf(100)))).thenReturn(configurationJson);
    when(ecsClient.get("consortia", Map.of("limit", String.valueOf(100)))).thenReturn(consortiaJson);
    when(ecsClient.get("consortia/bdaa4720-5e11-4632-bc10-d4455cf252df/tenants", Map.of("limit", String.valueOf(100)))).thenReturn(tenantJson);
    Fql fql = new Fql("", new EqualsCondition(new FqlField("field1"), "value1"));
    List<List<String>> expectedIds = new ArrayList<>();
    TEST_CONTENT_IDS.forEach(contentId -> expectedIds.add(List.of(contentId.toString())));
    List<List<String>> actualIds = new ArrayList<>();
    Consumer<IdsWithCancelCallback> idsConsumer = idsWithCancelCallback -> {
      List<String[]> ids = idsWithCancelCallback.ids();
      ids.forEach(idSet -> actualIds.add(Arrays.asList(idSet)));
    };
    when(localizationService.localizeEntityType(any(EntityType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(executionContext.getTenantId()).thenReturn("tenant_01");
    int idsCount = idStreamer.streamIdsInBatch(
      IdStreamerTestDataProvider.TEST_ENTITY_TYPE_DEFINITION,
      true,
      fql,
      2,
      idsConsumer
    );
    assertEquals(expectedIds, actualIds);
    assertEquals(IdStreamerTestDataProvider.TEST_CONTENT_IDS.size(), idsCount);
  }

  @Test
  void shouldHandleGroupByFields() {
    Fql fql = new Fql("", new EqualsCondition(new FqlField("field1"), "value1"));
    List<List<String>> expectedIds = new ArrayList<>();
    TEST_CONTENT_IDS.forEach(contentId -> expectedIds.add(List.of(contentId.toString())));
    List<List<String>> actualIds = new ArrayList<>();
    Consumer<IdsWithCancelCallback> idsConsumer = idsWithCancelCallback -> {
      List<String[]> ids = idsWithCancelCallback.ids();
      ids.forEach(idSet -> actualIds.add(Arrays.asList(idSet)));
    };
    when(localizationService.localizeEntityType(any(EntityType.class))).thenReturn(TEST_GROUP_BY_ENTITY_TYPE_DEFINITION);
    when(executionContext.getTenantId()).thenReturn("tenant_01");
    int idsCount = idStreamer.streamIdsInBatch(
      IdStreamerTestDataProvider.TEST_GROUP_BY_ENTITY_TYPE_DEFINITION,
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
}
