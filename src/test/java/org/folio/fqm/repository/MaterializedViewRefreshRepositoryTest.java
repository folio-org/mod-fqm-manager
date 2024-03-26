package org.folio.fqm.repository;

import org.folio.fqm.client.SimpleHttpClient;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

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
    Map<String, String> localSettingsParams = Map.of(
      "query", "(module==ORG and configName==localeSettings)"
    );
    when(simpleHttpClient.get(localeSettingsPath, localSettingsParams)).thenReturn("""
           {
             "what": {
               "ever": {
                 "dude": [
                   {
                     "theValue": "who",
                     "theLabel": "cares?"
                   },
                   {
                     "theValue": "so",
                     "theLabel": "lame"
                   },
                   {
                     "theValue": "yeah",
                     "theLabel": "right"
                   }
                 ]
               }
             }
           }
      """);
  }
}
