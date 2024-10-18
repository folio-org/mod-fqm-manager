package org.folio.fqm.service;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.domain.dto.DataRefreshResponse;
import org.folio.fqm.repository.DataRefreshRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static org.folio.fqm.repository.DataRefreshRepository.EXCHANGE_RATE_TABLE;

@Service
@RequiredArgsConstructor
public class DataRefreshService {
  private final DataRefreshRepository dataRefreshRepository;

  static final List<String> MATERIALIZED_VIEW_NAMES = List.of(
    "drv_inventory_statistical_code_full"
  );

  public DataRefreshResponse refreshData(String tenantId) {
    List<String> failedConcurrentRefreshes = dataRefreshRepository.refreshMaterializedViews(tenantId, MATERIALIZED_VIEW_NAMES, true);
    List<String> failedRefreshes = dataRefreshRepository.refreshMaterializedViews(tenantId, failedConcurrentRefreshes, false);
    List<String> successRefreshes = new ArrayList<>(MATERIALIZED_VIEW_NAMES
      .stream()
      .filter(matView -> !failedRefreshes.contains(matView))
      .toList());
    if (dataRefreshRepository.refreshExchangeRates(tenantId)) {
      successRefreshes.add(EXCHANGE_RATE_TABLE);
    } else {
      failedRefreshes.add(EXCHANGE_RATE_TABLE);
    }
    return new DataRefreshResponse()
      .successfulRefresh(successRefreshes)
      .failedRefresh(failedRefreshes);
  }
}
