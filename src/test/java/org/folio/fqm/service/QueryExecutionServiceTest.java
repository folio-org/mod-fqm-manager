package org.folio.fqm.service;

import org.folio.fql.deserializer.FqlParsingException;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.model.FqlQueryWithContext;
import org.folio.fqm.repository.QueryRepository;
import org.folio.fqm.testutil.TestDataFixture;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryExecutionServiceTest {

  @InjectMocks
  private QueryExecutionService queryExecutionService;
  @Mock
  private QueryProcessorService queryProcessorService;
  @Mock
  private FolioExecutionContext executionContext;
  @Mock
  private QueryRepository queryRepository;


  @Test
  void testQueryExecutionService() {
    String tenantId = "Tenant_01";
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType();
    UUID createdById = UUID.randomUUID();
    String fqlQuery = "{“item_status“: {“$in“: [\"missing\", \"lost\"]}}";
    List<String> fields = List.of();
    Query query = Query.newQuery(entityTypeId, fqlQuery, fields, createdById);
    int maxSize = 100;
    when(executionContext.getTenantId()).thenReturn(tenantId);

    queryExecutionService.executeQueryAsync(query, entityType, maxSize);
    FqlQueryWithContext fqlQueryWithContext = new FqlQueryWithContext(tenantId, entityType, fqlQuery, false);
    verify(queryProcessorService, times(1)).getIdsInBatch(
      eq(fqlQueryWithContext),
      anyInt(),
      anyInt(),
      any());
  }

  @Test
  void shouldHandleFailure() {
    Query query = TestDataFixture.getMockQuery();
    Throwable throwable = new FqlParsingException("field1", "Field not present");
    queryExecutionService.handleFailure(query, throwable);
    verify(queryRepository, times(1)).updateQuery(eq(query.queryId()), eq(QueryStatus.FAILED), any(), any());
  }
}
