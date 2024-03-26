package org.folio.fqm.service;

import org.folio.fqm.repository.DataRefreshRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DataRefreshServiceTest {
  @InjectMocks
  private DataRefreshService dataRefreshService;
  @Mock
  private DataRefreshRepository dataRefreshRepository;

  @Test
  void refreshDataTest() {
    String tenantId = "tenant_01";
    doNothing().when(dataRefreshRepository).refreshMaterializedViews(tenantId);
    doNothing().when(dataRefreshRepository).refreshExchangeRates(tenantId);
    dataRefreshService.refreshData(tenantId);
    verify(dataRefreshRepository, times(1)).refreshMaterializedViews(tenantId);
    verify(dataRefreshRepository, times(1)).refreshExchangeRates(tenantId);
  }
}
