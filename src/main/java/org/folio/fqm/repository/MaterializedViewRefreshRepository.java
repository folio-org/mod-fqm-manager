package org.folio.fqm.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
@Log4j2
public class MaterializedViewRefreshRepository {
  private static final String REFRESH_MATERIALIZED_VIEW_SQL = "REFRESH MATERIALIZED VIEW CONCURRENTLY ";

  private static final List<String> materializedViewNames = List.of(
    "drv_circulation_loan_status",
    "drv_inventory_item_status",
    "drv_pol_payment_status",
    "drv_pol_receipt_status",
    "drv_inventory_statistical_code_full"
  );

  private final DSLContext jooqContext;

  public void refreshMaterializedViews(String tenantId) {
    for (String matViewName : materializedViewNames) {
      String fullName = tenantId + "_mod_fqm_manager." + matViewName;
      log.info("Refreshing materialized view {}", fullName);
      jooqContext.execute(REFRESH_MATERIALIZED_VIEW_SQL + fullName);
    }
  }
}
