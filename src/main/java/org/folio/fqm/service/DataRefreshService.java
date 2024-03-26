package org.folio.fqm.service;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.repository.DataRefreshRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataRefreshService {
  private final DataRefreshRepository dataRefreshRepository;

  public void refreshData(String tenantId) {
    dataRefreshRepository.refreshMaterializedViews(tenantId);
    dataRefreshRepository.refreshExchangeRates(tenantId);
  }
}
