package org.folio.fqm.controller;

import org.folio.fqm.resource.DataRefreshController;
import org.folio.fqm.service.DataRefreshService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.integration.XOkapiHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DataRefreshController.class)
class DataRefreshControllerTest {
  @Autowired
  private MockMvc mockMvc;
  @MockBean
  private FolioExecutionContext executionContext;
  @MockBean
  private DataRefreshService dataRefreshService;

  @Test
  void refreshDataTest() throws Exception {
    String tenantId = "tenant_01";
    RequestBuilder requestBuilder = MockMvcRequestBuilders.post("/entity-types/materialized-views/refresh")
      .header(XOkapiHeaders.TENANT, tenantId)
      .contentType(APPLICATION_JSON);
    when(executionContext.getTenantId()).thenReturn(tenantId);
    doNothing().when(dataRefreshService).refreshData(tenantId);
    mockMvc.perform(requestBuilder)
      .andExpect(status().isNoContent());
    verify(dataRefreshService, times(1)).refreshData(tenantId);
  }
}
