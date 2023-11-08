package org.folio.fqm.repository;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterializedViewRefreshRepositoryTest {
  @InjectMocks
  private MaterializedViewRefreshRepository materializedViewRefreshRepository;
  @Mock
  private DSLContext jooqContext;

  @Test
  void refreshMaterializedViewsTest() {
    String tenantId = "tenant_01";
    String expectedItemStatusSql = "REFRESH MATERIALIZED VIEW CONCURRENTLY tenant_01_mod_fqm_manager.drv_inventory_item_status";
    String expectedLoanStatusSql = "REFRESH MATERIALIZED VIEW CONCURRENTLY tenant_01_mod_fqm_manager.drv_circulation_loan_status";
    when(jooqContext.execute(anyString())).thenReturn(1);
    materializedViewRefreshRepository.refreshMaterializedViews(tenantId);
    verify(jooqContext, times(1)).execute(expectedItemStatusSql);
    verify(jooqContext, times(1)).execute(expectedLoanStatusSql);
  }
}
