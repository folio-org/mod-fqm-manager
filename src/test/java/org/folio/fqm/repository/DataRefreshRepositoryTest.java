package org.folio.fqm.repository;

import org.folio.fqm.client.LocaleClient;
import org.folio.fqm.client.LocaleClient.LocaleSettings;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.service.SourceViewService;
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
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class DataRefreshRepositoryTest {
  @InjectMocks
  private DataRefreshRepository dataRefreshRepository;
  @Mock
  private SourceViewService sourceViewService;
  @Mock
  private DSLContext jooqContext;
  @Mock
  private SimpleHttpClient simpleHttpClient;
  @Mock
  private LocaleClient localeClient;

  @Test
  void shouldRefreshExchangeRates() {
    String tenantId = "tenant_01";
    String exchangeRatePath = "finance/exchange-rate";
    String fullTableName = "tenant_01_mod_fqm_manager.currency_exchange_rates";
    when(sourceViewService.isModFinanceInstalled()).thenReturn(true);
    when(localeClient.getLocaleSettings()).thenReturn(new LocaleSettings("en-US", "USD", "UTC", "latn"));
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
    String fullTableName = "tenant_01_mod_fqm_manager.currency_exchange_rates";
    when(sourceViewService.isModFinanceInstalled()).thenReturn(true);
    when(localeClient.getLocaleSettings()).thenReturn(new LocaleSettings("en-US", "ZWD", "UTC", "latn"));
    assertDoesNotThrow(() -> dataRefreshRepository.refreshExchangeRates(tenantId));
    verify(jooqContext, times(0)).insertInto(table(fullTableName));
  }

  @Test
  void shouldUseUSDAsDefaultCurrencyIfSystemCurrencyNotDefined() {
    String tenantId = "tenant_01";
    String exchangeRatePath = "finance/exchange-rate";
    String fullTableName = "tenant_01_mod_fqm_manager.currency_exchange_rates";
    Map<String, String> exchangeRateParams = Map.of(
      "from", "USD",
      "to", "USD"
    );
    when(sourceViewService.isModFinanceInstalled()).thenReturn(true);
    when(localeClient.getLocaleSettings()).thenReturn(new LocaleSettings("en-US", "USD", "UTC", "latn"));
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

  @Test
  void testDoesNotLoadExchangeRatesIfFinanceNotInstalled() {
    when(sourceViewService.isModFinanceInstalled()).thenReturn(false);

    assertTrue(dataRefreshRepository.refreshExchangeRates(null));

    verifyNoInteractions(simpleHttpClient, localeClient, jooqContext);
  }
}
