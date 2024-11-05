package org.folio.fqm.service;

import org.folio.fqm.domain.dto.DataRefreshResponse;
import org.folio.fqm.repository.DataRefreshRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.folio.fqm.repository.DataRefreshRepository.EXCHANGE_RATE_TABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataRefreshServiceTest {
  @InjectMocks
  private DataRefreshService dataRefreshService;
  @Mock
  private DataRefreshRepository dataRefreshRepository;

  @Test
  void refreshDataTest() {
    String tenantId = "tenant_01";
    List<String> refreshList = new ArrayList<>(List.of(EXCHANGE_RATE_TABLE));
    DataRefreshResponse successRefresh = new DataRefreshResponse()
      .successfulRefresh(refreshList)
      .failedRefresh(List.of());
    DataRefreshResponse failedRefresh = new DataRefreshResponse()
      .successfulRefresh(List.of())
      .failedRefresh(refreshList);
    when(dataRefreshRepository.refreshExchangeRates(tenantId)).thenReturn(true);
    DataRefreshResponse actualDataRefreshResponse =  dataRefreshService.refreshData(tenantId);
    assertEquals(successRefresh, actualDataRefreshResponse);

    when(dataRefreshRepository.refreshExchangeRates(tenantId)).thenReturn(false);
    actualDataRefreshResponse =  dataRefreshService.refreshData(tenantId);
    assertEquals(failedRefresh, actualDataRefreshResponse);
  }
}
