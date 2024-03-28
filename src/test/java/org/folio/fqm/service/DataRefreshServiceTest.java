package org.folio.fqm.service;

import org.folio.fqm.repository.MaterializedViewRefreshRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MaterializedViewRefreshServiceTest {
  @InjectMocks
  private MaterializedViewRefreshService materializedViewRefreshService;
  @Mock
  private MaterializedViewRefreshRepository materializedViewRefreshRepository;

  @Test
  void refreshMaterializedViewsTest() {
    String tenantId = "tenant_01";
    doNothing().when(materializedViewRefreshRepository).refreshMaterializedViews(tenantId);
    materializedViewRefreshService.refreshMaterializedViews(tenantId);
    verify(materializedViewRefreshRepository, times(1)).refreshMaterializedViews(tenantId);
  }
}
