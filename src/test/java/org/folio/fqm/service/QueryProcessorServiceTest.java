package org.folio.fqm.service;

import org.folio.fql.model.EqualsCondition;
import org.folio.fql.model.Fql;
import org.folio.fql.model.field.FqlField;
import org.folio.fql.service.FqlService;
import org.folio.fqm.model.FqlQueryWithContext;
import org.folio.fqm.model.IdsWithCancelCallback;
import org.folio.fqm.repository.IdStreamer;
import org.folio.fqm.repository.ResultSetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryProcessorServiceTest {

  public static final int DEFAULT_BATCH_SIZE = 1000;
  private QueryProcessorService service;
  private ResultSetRepository resultSetRepository;
  private IdStreamer idStreamer;
  private FqlService fqlService;

  @BeforeEach
  public void setup() {
    resultSetRepository = mock(ResultSetRepository.class);
    idStreamer = mock(IdStreamer.class);
    fqlService = mock(FqlService.class);
    this.service = new QueryProcessorService(resultSetRepository, idStreamer, fqlService);
  }

  @Test
  void shouldGetIdsInBatch() {
    Fql fql = new Fql(new EqualsCondition(new FqlField("status"), "missing"));
    String tenantId = "tenant_01";
    String fqlCriteria = "{\"status\": {\"$eq\": \"missing\"}}";
    UUID entityTypeId = UUID.randomUUID();
    Consumer<IdsWithCancelCallback> idsConsumer = mock(Consumer.class);
    IntConsumer totalCountConsumer = mock(IntConsumer.class);
    Consumer<Throwable> errorConsumer = mock(Consumer.class);

    when(fqlService.getFql(fqlCriteria)).thenReturn(fql);
    FqlQueryWithContext fqlQueryWithContext = new FqlQueryWithContext(tenantId, entityTypeId, fqlCriteria, true);
    service.getIdsInBatch(fqlQueryWithContext,
      DEFAULT_BATCH_SIZE,
      idsConsumer,
      totalCountConsumer,
      errorConsumer
    );

    verify(idStreamer, times(1))
      .streamIdsInBatch(
        fqlQueryWithContext.entityTypeId(),
        fqlQueryWithContext.sortResults(),
        fql,
        DEFAULT_BATCH_SIZE,
        idsConsumer
      );
  }

  @Test
  void shouldConsumeButNotThrowError() {
    Fql fql = new Fql(new EqualsCondition(new FqlField("status"), "missing"));
    String tenantId = "tenant_01";
    String fqlCriteria = "{\"status\": {\"$eq\": \"missing\"}}";
    UUID entityTypeId = UUID.randomUUID();
    Consumer<IdsWithCancelCallback> idsConsumer = mock(Consumer.class);
    IntConsumer totalCountConsumer = mock(IntConsumer.class);
    AtomicInteger actualErrorCount = new AtomicInteger(0);
    Consumer<Throwable> errorConsumer = err -> actualErrorCount.getAndIncrement();
    int expectedErrorCount = 1;

    when(fqlService.getFql(fqlCriteria)).thenReturn(fql);
    FqlQueryWithContext fqlQueryWithContext = new FqlQueryWithContext(tenantId, entityTypeId, fqlCriteria, true);

    doThrow(new RuntimeException("something went wrong"))
      .when(idStreamer)
      .streamIdsInBatch(
        fqlQueryWithContext.entityTypeId(),
        fqlQueryWithContext.sortResults(),
        fql,
        1000,
        idsConsumer
      );
    service.getIdsInBatch(fqlQueryWithContext,
      DEFAULT_BATCH_SIZE,
      idsConsumer,
      totalCountConsumer,
      errorConsumer
    );
    assertEquals(expectedErrorCount, actualErrorCount.get(), "Actual Error Count should be 1 when an exception occurs");
  }

  @Test
  void shouldRunSynchronousQueryAndReturnPaginatedResults() {
    UUID entityTypeId = UUID.randomUUID();
    String fqlQuery = """
      {"field1": {"eq": "value1" }}
      """;
    List<String> afterId = List.of(UUID.randomUUID().toString());
    int limit = 100;
    Fql expectedFql = new Fql(new EqualsCondition(new FqlField("status"), "value1"));
    List<String> fields = List.of("field1", "field2");
    List<Map<String, Object>> expectedContent = List.of(
      Map.of("field1", "value1", "field2", "value2"),
      Map.of("field1", "value1", "field2", "value4")
    );
    when(fqlService.getFql(fqlQuery)).thenReturn(expectedFql);
    when(resultSetRepository.getResultSet(entityTypeId, expectedFql, fields, afterId, limit)).thenReturn(expectedContent);
    List<Map<String, Object>> actualContent = service.processQuery(entityTypeId, fqlQuery, fields, afterId, limit);
    assertEquals(expectedContent, actualContent);
  }
}
