package org.folio.fqm.service;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.repository.MaterializedViewRefreshRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MaterializedViewRefreshService {
  private final MaterializedViewRefreshRepository materializedViewRefreshRepository;

  public void refreshMaterializedViews(String tenantId) {
    materializedViewRefreshRepository.refreshMaterializedViews(tenantId);
  }
}
