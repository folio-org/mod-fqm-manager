package org.folio.fqm.repository;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.SimpleHttpClient;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jooq.impl.DSL.table;

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
    "JPY",
    "BGN",
    "CZK",
    "DKK",
    "GBP",
    "HUF",
    "PLN",
    "RON",
    "SEK",
    "CHF",
    "ISK",
    "NOK",
    "HRK",
    "RUB",
    "TRY",
    "AUD",
    "BRL",
    "CAD",
    "CNY",
    "HKD",
    "IDR",
    "ILS",
    "INR",
    "KRW",
    "MXN",
    "MYR",
    "NZD",
    "PHP",
    "SGD",
    "THB",
    "ZAR"
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
  // TODO: What to do if default currency is not a system-supported one?
  public void refreshExchangeRates(String tenantId) {
    String localeSettingsPath = "configurations/entries";
    Map<String, String> localSettingsParams = Map.of(
      "query", "(module==ORG and configName==localeSettings)"
    );
    log.info("Refreshing exchange rates");
    String systemCurrencyCode;
    try {
      var rawJson = simpleHttpClient.get(localeSettingsPath, localSettingsParams);
      Object value = JsonPath.parse(rawJson).read("configs[0].value");
      DocumentContext locale = JsonPath.parse((String) value);
      systemCurrencyCode = locale.read("currency");
    } catch(Exception e) {
      log.info("No system currency defined, defaulting to USD");
      systemCurrencyCode = "USD";
    }

    String exchangeRatePath = "finance/exchange-rate";
    Map<String, Float> exchangeRates = new HashMap<>();
    for (String currencyCode : SYSTEM_SUPPORTED_CURRENCIES) {
      log.info("Getting currency exchange rate from {} to {}", currencyCode, systemCurrencyCode);

      Map<String, String> exchangeRateParams = Map.of(
        "from", currencyCode,
      "to", systemCurrencyCode
      );
      Float exchangeRate;
      try {
        var exchangeRateResponse = simpleHttpClient.get(exchangeRatePath, exchangeRateParams);
        var exchangeRateInfo = JsonPath.parse(exchangeRateResponse);
        exchangeRate = exchangeRateInfo.read("exchangeRate");
        log.info("Exchange rate: {}", exchangeRate);
      } catch (Exception e) {
        log.info("Failed to get exchange rate from {} to {}", currencyCode, systemCurrencyCode);
        exchangeRate = null;
      }
      exchangeRates.put(currencyCode, exchangeRate);

    }
    String fullTableName = tenantId + "_mod_fqm_manager." + "currency_exchange_rates";
    jooqContext
      .insertInto(table(fullTableName))
      .values(exchangeRates)
      .execute();
  }
}
