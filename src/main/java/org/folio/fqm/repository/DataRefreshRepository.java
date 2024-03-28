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
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.table;

@Repository
@RequiredArgsConstructor
@Log4j2
public class DataRefreshRepository {

  private static final String REFRESH_MATERIALIZED_VIEW_SQL = "REFRESH MATERIALIZED VIEW CONCURRENTLY ";
  private static final String GET_EXCHANGE_RATE_PATH = "finance/exchange-rate";
  private static final String GET_LOCALE_SETTINGS_PATH = "configurations/entries";
  private static final Map<String, String> GET_LOCALE_SETTINGS_PARAMS = Map.of(
    "query", "(module==ORG and configName==localeSettings)"
  );

  private static final List<String> MATERIALIZED_VIEW_NAMES = List.of(
    "drv_circulation_loan_status",
    "drv_inventory_item_status",
    "drv_pol_payment_status",
    "drv_pol_receipt_status",
    "drv_inventory_statistical_code_full",
    "drv_languages"
  );

  static final List<String> SYSTEM_SUPPORTED_CURRENCIES = List.of(
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
    for (String matViewName : MATERIALIZED_VIEW_NAMES) {
      String fullName = tenantId + "_mod_fqm_manager." + matViewName;
      log.info("Refreshing materialized view {}", fullName);
      jooqContext.execute(REFRESH_MATERIALIZED_VIEW_SQL + fullName);
    }
  }

  // TODO: Also need to handle situation where there is no localeSettings defined
  // TODO: What to do if default currency is not a system-supported one?
  public void refreshExchangeRates(String tenantId) {
    log.info("Refreshing exchange rates");
    String fullTableName = tenantId + "_mod_fqm_manager." + "currency_exchange_rates";
    String systemCurrency = getSystemCurrencyCode();
    Map<String, Double> exchangeRates = SYSTEM_SUPPORTED_CURRENCIES
      .stream()
      .collect(Collectors.toMap(currency -> currency, currency -> getExchangeRate(currency, systemCurrency)));
//    Map<String, Double> exchangeRates = new HashMap<>();
//    SYSTEM_SUPPORTED_CURRENCIES.forEach(currency -> exchangeRates.put(currency, getExchangeRate(currency, systemCurrency)));
    jooqContext
      .insertInto(table(fullTableName))
      .values(exchangeRates)
      .execute();
    //    var step1 = jooqContext.insertInto(table(fullTableName));
//    var step2 = step1.values(exchangeRates);
//    var step3 = step2.execute();
  }

  private String getSystemCurrencyCode() {
    log.info("Getting system currency");
    try {
      String localeSettingsResponse = simpleHttpClient.get(GET_LOCALE_SETTINGS_PATH, GET_LOCALE_SETTINGS_PARAMS);
      Object localeSettings = JsonPath.parse(localeSettingsResponse).read("configs[0].value");
      DocumentContext locale = JsonPath.parse((String) localeSettings);
      return locale.read("currency");
    } catch(Exception e) {
      log.info("No system currency defined, defaulting to USD");
      return "USD";
    }
  }

  private Double getExchangeRate(String fromCurrency, String toCurrency) {
    log.info("Getting currency exchange rate from {} to {}", fromCurrency, toCurrency);
    Map<String, String> exchangeRateParams = Map.of(
      "from", fromCurrency,
      "to", toCurrency
    );
    try {
      var exchangeRateResponse = simpleHttpClient.get(GET_EXCHANGE_RATE_PATH, exchangeRateParams);
      var exchangeRateInfo = JsonPath.parse(exchangeRateResponse);
      return exchangeRateInfo.read("exchangeRate");
    } catch (Exception e) {
      log.info("Failed to get exchange rate from {} to {}", fromCurrency, toCurrency);
      return null;
    }
  }
}
