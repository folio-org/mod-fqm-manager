package org.folio.fqm.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.SimpleHttpClient;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
@RequiredArgsConstructor
@Log4j2
public class DataRefreshRepository {

  public static final Field<String> CURRENCY_FIELD = field("currency", String.class);
  public static final Field<Double> EXCHANGE_RATE_FIELD = field("exchange_rate", Double.class);
  public static final String EXCHANGE_RATE_TABLE = "currency_exchange_rates";
  private static final String GET_EXCHANGE_RATE_PATH = "finance/exchange-rate";
  private static final String GET_LOCALE_SETTINGS_PATH = "configurations/entries";
  private static final Map<String, String> GET_LOCALE_SETTINGS_PARAMS = Map.of(
    "query", "(module==ORG and configName==localeSettings)"
  );

  private static final List<String> SYSTEM_SUPPORTED_CURRENCIES = List.of(
    "USD",
    "EUR",
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

  /**
   * Refresh the currency exchange rates for a tenant, based on the tenant's default system currency.
   *
   * @param tenantId    Tenant ID
   * @return            True if refresh successful, false otherwise
   */
  public boolean refreshExchangeRates(String tenantId) {
    log.info("Refreshing exchange rates");
    String fullTableName = tenantId + "_mod_fqm_manager." + EXCHANGE_RATE_TABLE;
    String systemCurrency = getSystemCurrencyCode();
    if (!SYSTEM_SUPPORTED_CURRENCIES.contains(systemCurrency)) {
      log.info("System currency does not support automatic exchange rate calculation");
      return false;
    }

    List<Record2<String, Double>> exchangeRates = new ArrayList<>();
    for (String currency : SYSTEM_SUPPORTED_CURRENCIES) {
      Double exchangeRate = getExchangeRate(currency, systemCurrency);
      if (!Double.isNaN(exchangeRate)) {
        Record2<String, Double> currencyExchangeRate = jooqContext
          .newRecord(CURRENCY_FIELD, EXCHANGE_RATE_FIELD)
          .value1(currency)
          .value2(exchangeRate);
        exchangeRates.add(currencyExchangeRate);
      }
    }

    jooqContext.insertInto(table(fullTableName), CURRENCY_FIELD, EXCHANGE_RATE_FIELD)
      .valuesOfRecords(exchangeRates)
      .onConflict(CURRENCY_FIELD)
      .doUpdate()
      .set(EXCHANGE_RATE_FIELD, DSL.field("EXCLUDED." + EXCHANGE_RATE_FIELD.getName(), Double.class))
      .execute();
    return true;
  }

  private String getSystemCurrencyCode() {
    log.info("Getting system currency");
    try {
      String localeSettingsResponse = simpleHttpClient.get(GET_LOCALE_SETTINGS_PATH, GET_LOCALE_SETTINGS_PARAMS);
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode localeSettingsNode = objectMapper.readTree(localeSettingsResponse);
      String valueString = localeSettingsNode
        .path("configs")
        .get(0)
        .path("value")
        .asText();
      JsonNode valueNode = objectMapper.readTree(valueString);
      return valueNode.path("currency").asText();
    } catch (Exception e) {
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
      String exchangeRateResponse = simpleHttpClient.get(GET_EXCHANGE_RATE_PATH, exchangeRateParams);
      DocumentContext exchangeRateInfo = JsonPath.parse(exchangeRateResponse);
      var exchangeRate = exchangeRateInfo.read("exchangeRate");
      if (exchangeRate instanceof BigDecimal bd) {
        return bd.doubleValue();
      }
      return (Double) exchangeRate;
    } catch (Exception e) {
      log.info("Failed to get exchange rate from {} to {}", fromCurrency, toCurrency);
      return Double.NaN;
    }
  }
}
