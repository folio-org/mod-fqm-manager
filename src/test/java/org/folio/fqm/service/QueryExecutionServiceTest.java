package org.folio.fqm.service;

import org.folio.fqm.domain.Query;
import org.folio.fqm.lib.model.FqlQueryWithContext;
import org.folio.fqm.lib.service.QueryProcessorService;
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

  @Test
  void testQueryExecutionService() {
    String tenantId = "Tenant_01";
    UUID entityTypeId = UUID.randomUUID();
    UUID createdById = UUID.randomUUID();
    String fqlQuery = "{“item_status“: {“$in“: [\"missing\", \"lost\"]}}";
    List<String> fields = List.of();
    Query query = Query.newQuery(entityTypeId, fqlQuery, fields, createdById);
    when(executionContext.getTenantId()).thenReturn(tenantId);
    queryExecutionService.executeQueryAsync(query);
    FqlQueryWithContext fqlQueryWithContext = new FqlQueryWithContext(tenantId, entityTypeId, fqlQuery, false);
    verify(queryProcessorService, times(1)).getIdsInBatch(
      eq(fqlQueryWithContext),
      anyInt(),
      any(),
      any(),
      any());
  }
}
