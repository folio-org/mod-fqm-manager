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
import static org.folio.fqm.service.DataRefreshService.MATERIALIZED_VIEW_NAMES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    List<String> expectedSuccessRefreshViews = new ArrayList<>(MATERIALIZED_VIEW_NAMES);
    expectedSuccessRefreshViews.add(EXCHANGE_RATE_TABLE);
    DataRefreshResponse expectedDataRefreshResponse = new DataRefreshResponse()
      .successfulRefresh(expectedSuccessRefreshViews)
      .failedRefresh(List.of());
    when(dataRefreshRepository.refreshMaterializedViews(tenantId, MATERIALIZED_VIEW_NAMES, true)).thenReturn(List.of());
    when(dataRefreshRepository.refreshExchangeRates(tenantId)).thenReturn(true);
    DataRefreshResponse actualDataRefreshResponse =  dataRefreshService.refreshData(tenantId);
    assertEquals(expectedDataRefreshResponse, actualDataRefreshResponse);
  }

  @Test
  void shouldRetryRefreshIfConcurrentRefreshFails() {
    String tenantId = "tenant_01";
    List<String> expectedSuccessRefreshViews = new ArrayList<>(MATERIALIZED_VIEW_NAMES);
    expectedSuccessRefreshViews.add(EXCHANGE_RATE_TABLE);
    DataRefreshResponse expectedDataRefreshResponse = new DataRefreshResponse()
      .successfulRefresh(expectedSuccessRefreshViews)
      .failedRefresh(List.of());
    when(dataRefreshRepository.refreshMaterializedViews(tenantId, MATERIALIZED_VIEW_NAMES, true)).thenReturn(List.of("drv_languages"));
    when(dataRefreshRepository.refreshMaterializedViews(tenantId, List.of("drv_languages"), false)).thenReturn(List.of());
    when(dataRefreshRepository.refreshExchangeRates(tenantId)).thenReturn(true);
    DataRefreshResponse actualDataRefreshResponse =  dataRefreshService.refreshData(tenantId);
    verify(dataRefreshRepository, times(1)).refreshMaterializedViews(tenantId, List.of("drv_languages"), false);
    assertEquals(expectedDataRefreshResponse, actualDataRefreshResponse);
  }

  @Test
  void shouldReturnFailedRefreshes() {
    String tenantId = "tenant_01";
    List<String> expectedSuccessRefreshes = MATERIALIZED_VIEW_NAMES
      .stream()
      .filter(view -> !view.equals("drv_languages"))
      .toList();
    List<String> expectedFailedRefreshes = List.of("drv_languages", "currency_exchange_rates");
    DataRefreshResponse expectedDataRefreshResponse = new DataRefreshResponse()
      .successfulRefresh(expectedSuccessRefreshes)
      .failedRefresh(expectedFailedRefreshes);
    when(dataRefreshRepository.refreshMaterializedViews(tenantId, MATERIALIZED_VIEW_NAMES, true))
      .thenReturn(new ArrayList<>(List.of("drv_languages")));
    when(dataRefreshRepository.refreshMaterializedViews(tenantId, List.of("drv_languages"), false))
      .thenReturn(new ArrayList<>(List.of("drv_languages")));
    when(dataRefreshRepository.refreshExchangeRates(tenantId)).thenReturn(false);
    DataRefreshResponse actualDataRefreshResponse =  dataRefreshService.refreshData(tenantId);
    verify(dataRefreshRepository, times(1)).refreshMaterializedViews(tenantId, List.of("drv_languages"), false);
    assertEquals(expectedDataRefreshResponse, actualDataRefreshResponse);
  }
}
