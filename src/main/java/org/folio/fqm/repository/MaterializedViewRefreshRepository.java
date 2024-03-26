package org.folio.fqm.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.SimpleHttpClient;
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
    "drv_inventory_statistical_code_full",
    "drv_languages"
  );

  private static final List<String> SYSTEM_SUPPORTED_CURRENCIES = List.of(
    "USD",
    "GBP"
  );

  private final DSLContext jooqContext;

  private final SimpleHttpClient simpleHttpClient;

  public void refreshMaterializedViews(String tenantId) {
//    for (String matViewName : materializedViewNames) {
//      String fullName = tenantId + "_mod_fqm_manager." + matViewName;
//      log.info("Refreshing materialized view {}", fullName);
//      jooqContext.execute(REFRESH_MATERIALIZED_VIEW_SQL + fullName);
//    }
    refreshExchangeRates(tenantId);
  }

  public void refreshExchangeRates(String tenantId) {
    String localeSettingsGetter = "configurations/entries?query=(module==ORG and configName==localeSettings)";
    log.info("Refreshing exchange rates");
    String defaultCurrencyCode = "USD"; // TODO: have to get this from somewhere
    for (String currencyCode : SYSTEM_SUPPORTED_CURRENCIES) {
      log.info("Getting currency exchange rate from {} to {}", defaultCurrencyCode, currencyCode);

    }
  }
}
