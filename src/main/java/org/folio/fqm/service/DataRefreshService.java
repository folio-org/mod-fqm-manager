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

  public DataRefreshResponse refreshData(String tenantId) {
    List<String> successRefreshes = new ArrayList<>();
    List<String> failedRefreshes = new ArrayList<>();
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
