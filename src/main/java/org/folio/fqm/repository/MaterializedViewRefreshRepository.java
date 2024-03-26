package org.folio.fqm.repository;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.SimpleHttpClient;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

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

  // TODO: need to get this from somewhere
  private static final List<String> SYSTEM_SUPPORTED_CURRENCIES = List.of(
    "USD",
    "GBP",
    "INR"
  );

  private final DSLContext jooqContext;

  private final SimpleHttpClient simpleHttpClient;

  public void refreshMaterializedViews(String tenantId) {
    for (String matViewName : materializedViewNames) {
      String fullName = tenantId + "_mod_fqm_manager." + matViewName;
      log.info("Refreshing materialized view {}", fullName);
      jooqContext.execute(REFRESH_MATERIALIZED_VIEW_SQL + fullName);
    }
  }

  // TODO: Also need to handle situation where there is no localeSettings defined
  public void refreshExchangeRates(String tenantId) {
    String localeSettingsPath = "configurations/entries";
    Map<String, String> localSettingsParams = Map.of(
      "query", "(module==ORG and configName==localeSettings)"
    );
    log.info("Refreshing exchange rates");
    var rawJson = simpleHttpClient.get(localeSettingsPath, localSettingsParams);
    String defaultCurrencyCode = "USD"; // TODO: have to get this from somewhere
    DocumentContext localeSettings = JsonPath.parse(rawJson);
    var value = localeSettings.read("configs[0].value");
    DocumentContext locale = JsonPath.parse((String) value);
    var currency = locale.read("currency");
//    JsonPath.
    for (String currencyCode : SYSTEM_SUPPORTED_CURRENCIES) {
      log.info("Getting currency exchange rate from {} to {}", defaultCurrencyCode, currencyCode);

    }
  }
}
