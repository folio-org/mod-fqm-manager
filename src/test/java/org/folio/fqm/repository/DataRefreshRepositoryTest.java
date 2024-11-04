package org.folio.fqm.repository;

import org.folio.fqm.client.SimpleHttpClient;
import org.jooq.DSLContext;
import org.jooq.InsertOnConflictWhereIndexPredicateStep;
import org.jooq.InsertOnDuplicateSetMoreStep;
import org.jooq.InsertOnDuplicateSetStep;
import org.jooq.InsertValuesStep2;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.Collection;
import java.util.Map;

import static org.folio.fqm.repository.DataRefreshRepository.CURRENCY_FIELD;
import static org.folio.fqm.repository.DataRefreshRepository.EXCHANGE_RATE_FIELD;
import static org.jooq.impl.DSL.table;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataRefreshRepositoryTest {
  @InjectMocks
  private DataRefreshRepository dataRefreshRepository;
  @Mock
  private DSLContext jooqContext;
  @Mock
  private SimpleHttpClient simpleHttpClient;

  @Test
  void shouldRefreshExchangeRates() {
    String tenantId = "tenant_01";
    String localeSettingsPath = "configurations/entries";
    String exchangeRatePath = "finance/exchange-rate";
    String fullTableName = "tenant_01_mod_fqm_manager.currency_exchange_rates";
    Map<String, String> localeSettingsParams = Map.of(
      "query", "(module==ORG and configName==localeSettings)"
    );
    when(simpleHttpClient.get(localeSettingsPath, localeSettingsParams)).thenReturn("""
           {
             "configs": [
               {
                 "id":"2a132a01-623b-4d3a-9d9a-2feb777665c2",
                 "module":"ORG",
                 "configName":"localeSettings",
                 "enabled":true,
                 "value":"{\\"locale\\":\\"en-US\\",\\"timezone\\":\\"UTC\\",\\"currency\\":\\"USD\\"}","metadata":{"createdDate":"2024-03-25T17:37:22.309+00:00","createdByUserId":"db760bf8-e05a-4a5d-a4c3-8d49dc0d4e48"}
               }
             ],
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

    Record2<String, Double> exchangeRateMock = mock(Record2.class);
    when(jooqContext.newRecord(CURRENCY_FIELD, EXCHANGE_RATE_FIELD))
      .thenAnswer((Answer<Record2<String, Double>>) invocation -> exchangeRateMock);
    when(exchangeRateMock.value1(anyString())).thenReturn(exchangeRateMock);
    when(exchangeRateMock.value2(1.25)).thenReturn(exchangeRateMock);

    InsertValuesStep2<Record, String, Double> insertValuesStep2Mock = mock(InsertValuesStep2.class);
    InsertOnConflictWhereIndexPredicateStep<Record> insertOnConflictWhereIndexPredicateStep = mock(InsertOnConflictWhereIndexPredicateStep.class);
    InsertOnDuplicateSetStep<Record> insertOnDuplicateSetStepMock = mock(InsertOnDuplicateSetStep.class);
    InsertOnDuplicateSetMoreStep<Record> insertOnDuplicateSetMoreStepMock = mock(InsertOnDuplicateSetMoreStep.class);
    when(jooqContext.insertInto(table(fullTableName), CURRENCY_FIELD, EXCHANGE_RATE_FIELD)).thenReturn(insertValuesStep2Mock);
    when(insertValuesStep2Mock.valuesOfRecords((Collection) any())).thenReturn(insertValuesStep2Mock);
    when(insertValuesStep2Mock.onConflict(CURRENCY_FIELD)).thenReturn(insertOnConflictWhereIndexPredicateStep);
    when(insertOnConflictWhereIndexPredicateStep.doUpdate()).thenReturn(insertOnDuplicateSetStepMock);
    when(insertOnDuplicateSetStepMock.set(EXCHANGE_RATE_FIELD, DSL.field("EXCLUDED." + EXCHANGE_RATE_FIELD.getName(), Double.class))).thenReturn(insertOnDuplicateSetMoreStepMock);
    when(insertOnDuplicateSetMoreStepMock.execute()).thenReturn(1);
    assertDoesNotThrow(() -> dataRefreshRepository.refreshExchangeRates(tenantId));
    verify(insertOnDuplicateSetMoreStepMock, times(1)).execute();
  }

  @Test
  void shouldDoNothingIfSystemCurrencyIsNotSupported() {
    String tenantId = "tenant_01";
    String localeSettingsPath = "configurations/entries";
    String fullTableName = "tenant_01_mod_fqm_manager.currency_exchange_rates";
    Map<String, String> localeSettingsParams = Map.of(
      "query", "(module==ORG and configName==localeSettings)"
    );
    when(simpleHttpClient.get(localeSettingsPath, localeSettingsParams)).thenReturn("""
           {
             "configs": [
               {
                 "id":"2a132a01-623b-4d3a-9d9a-2feb777665c2",
                 "module":"ORG",
                 "configName":"localeSettings",
                 "enabled":true,
                 "value":"{\\"locale\\":\\"en-US\\",\\"timezone\\":\\"UTC\\",\\"currency\\":\\"ZWD\\"}","metadata":{"createdDate":"2024-03-25T17:37:22.309+00:00","createdByUserId":"db760bf8-e05a-4a5d-a4c3-8d49dc0d4e48"}
               }
             ],
             "totalRecords": 1,
             "resultInfo": {"totalRecords":1,"facets":[],"diagnostics":[]}
           }
      """);
    assertDoesNotThrow(() -> dataRefreshRepository.refreshExchangeRates(tenantId));
    verify(jooqContext, times(0)).insertInto(table(fullTableName));
  }

  @Test
  void shouldUseUSDAsDefaultCurrencyIfSystemCurrencyNotDefined() {
    String tenantId = "tenant_01";
    String localeSettingsPath = "configurations/entries";
    String exchangeRatePath = "finance/exchange-rate";
    String fullTableName = "tenant_01_mod_fqm_manager.currency_exchange_rates";
    Map<String, String> exchangeRateParams = Map.of(
      "from", "USD",
      "to", "USD"
    );
    Map<String, String> localeSettingsParams = Map.of(
      "query", "(module==ORG and configName==localeSettings)"
    );
    when(simpleHttpClient.get(localeSettingsPath, localeSettingsParams)).thenReturn("""
           {
             "configs": [],
             "totalRecords": 0,
             "resultInfo": {"totalRecords":0,"facets":[],"diagnostics":[]}
           }
      """);
    when(simpleHttpClient.get(exchangeRatePath, exchangeRateParams)).thenReturn("""
       {
         "from": "ZAR",
         "to": "USD",
         "exchangeRate": 1.1234567890123456789
       }
      """);

    Record2<String, Double> exchangeRateMock = mock(Record2.class);
    when(jooqContext.newRecord(CURRENCY_FIELD, EXCHANGE_RATE_FIELD))
      .thenAnswer((Answer<Record2<String, Double>>) invocation -> exchangeRateMock);
    when(exchangeRateMock.value1(anyString())).thenReturn(exchangeRateMock);
    when(exchangeRateMock.value2(1.1234567890123456789)).thenReturn(exchangeRateMock);

    InsertValuesStep2<Record, String, Double> insertValuesStep2Mock = mock(InsertValuesStep2.class);
    InsertOnConflictWhereIndexPredicateStep<Record> insertOnConflictWhereIndexPredicateStep = mock(InsertOnConflictWhereIndexPredicateStep.class);
    InsertOnDuplicateSetStep<Record> insertOnDuplicateSetStepMock = mock(InsertOnDuplicateSetStep.class);
    InsertOnDuplicateSetMoreStep<Record> insertOnDuplicateSetMoreStepMock = mock(InsertOnDuplicateSetMoreStep.class);
    when(jooqContext.insertInto(table(fullTableName), CURRENCY_FIELD, EXCHANGE_RATE_FIELD)).thenReturn(insertValuesStep2Mock);
    when(insertValuesStep2Mock.valuesOfRecords((Collection) any())).thenReturn(insertValuesStep2Mock);
    when(insertValuesStep2Mock.onConflict(CURRENCY_FIELD)).thenReturn(insertOnConflictWhereIndexPredicateStep);
    when(insertOnConflictWhereIndexPredicateStep.doUpdate()).thenReturn(insertOnDuplicateSetStepMock);
    when(insertOnDuplicateSetStepMock.set(EXCHANGE_RATE_FIELD, DSL.field("EXCLUDED." + EXCHANGE_RATE_FIELD.getName(), Double.class))).thenReturn(insertOnDuplicateSetMoreStepMock);
    when(insertOnDuplicateSetMoreStepMock.execute()).thenReturn(1);
    assertDoesNotThrow(() -> dataRefreshRepository.refreshExchangeRates(tenantId));
    verify(insertOnDuplicateSetMoreStepMock, times(1)).execute();
  }
}
