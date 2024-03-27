package org.folio.fqm.repository;

import org.folio.fqm.client.SimpleHttpClient;
import org.jooq.DSLContext;
import org.jooq.InsertSetStep;
import org.jooq.InsertValuesStepN;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static org.folio.fqm.repository.MaterializedViewRefreshRepository.SYSTEM_SUPPORTED_CURRENCIES;
import static org.jooq.impl.DSL.table;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterializedViewRefreshRepositoryTest {
  @InjectMocks
  private MaterializedViewRefreshRepository materializedViewRefreshRepository;
  @Mock
  private DSLContext jooqContext;
  @Mock
  private SimpleHttpClient simpleHttpClient;

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

  @Test
  void shouldRefreshExchangeRates() {
    String tenantId = "tenant_01";
    String localeSettingsPath = "configurations/entries";
    String exchangeRatePath = "finance/exchange-rate";
    String fullTableName = "tenant_01_mod_fqm_manager.currency_exchange_rates";
    Map<String, String> localSettingsParams = Map.of(
      "query", "(module==ORG and configName==localeSettings)"
    );
    Map<String, Double> expectedExchangeRates = SYSTEM_SUPPORTED_CURRENCIES
      .stream()
      .collect(Collectors.toMap(currency -> currency, currency -> 1.25));
    when(simpleHttpClient.get(localeSettingsPath, localSettingsParams)).thenReturn("""
           {
             "configs": [
           {"id":"2a132a01-623b-4d3a-9d9a-2feb777665c2","module":"ORG","configName":"localeSettings","enabled":true,"value":"{\\"locale\\":\\"en-US\\",\\"timezone\\":\\"UTC\\",\\"currency\\":\\"USD\\"}","metadata":{"createdDate":"2024-03-25T17:37:22.309+00:00","createdByUserId":"db760bf8-e05a-4a5d-a4c3-8d49dc0d4e48"}}],
             "totalRecords": 1,
             "resultInfo": {"totalRecords":1,"facets":[],"diagnostics":[]}
           }
      """);
    when(simpleHttpClient.get(eq(exchangeRatePath), any())).thenReturn("""
       {
         "from": "someCurrency",
         "to": "USD",
         "exchangeRate": 1.25
       }
      """);
    InsertSetStep insertSetStepMock = mock(InsertSetStep.class);
    InsertValuesStepN insertValuesStepNMock = mock(InsertValuesStepN.class);
    when(jooqContext.insertInto(table(fullTableName))).thenReturn(insertSetStepMock);
    when(insertSetStepMock.values(expectedExchangeRates)).thenReturn(insertValuesStepNMock);
    when(insertValuesStepNMock.execute()).thenReturn(1);
    assertDoesNotThrow(() -> materializedViewRefreshRepository.refreshExchangeRates(tenantId));
    verify(insertValuesStepNMock, times(1)).execute();
  }

  @Test
  void shouldUseUSDAsDefaultCurrencyIfSystemCurrencyNotDefined() {
//    String tenantId = "tenant_01";
//    String localeSettingsPath = "configurations/entries";
//    Map<String, String> localSettingsParams = Map.of(
//      "query", "(module==ORG and configName==localeSettings)"
//    );
//    String exchangeRatePath = "finance/exchange-rate";
//    when(simpleHttpClient.get(localeSettingsPath, localSettingsParams)).thenReturn(null);
//    when(simpleHttpClient.get(eq(exchangeRatePath), any())).thenReturn("""
//       {
//         "from": "someCurrency",
//         "to": "USD",
//         "exchangeRate": 1.25
//      """);
//    assertDoesNotThrow(() -> materializedViewRefreshRepository.refreshExchangeRates(tenantId));

  }
}
