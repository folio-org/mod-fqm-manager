package org.folio.fqm.controller;

import org.folio.fqm.domain.dto.DataRefreshResponse;
import org.folio.fqm.resource.DataRefreshController;
import org.folio.fqm.service.DataRefreshService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.integration.XOkapiHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DataRefreshController.class)
class DataRefreshControllerTest {
  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private FolioExecutionContext executionContext;
  @MockitoBean
  private DataRefreshService dataRefreshService;

  @Test
  void refreshDataTest() throws Exception {
    String tenantId = "tenant_01";
    DataRefreshResponse expectedResponse = new DataRefreshResponse()
      .successfulRefresh(List.of())
      .failedRefresh(List.of());
    RequestBuilder requestBuilder = MockMvcRequestBuilders.post("/entity-types/materialized-views/refresh")
      .header(XOkapiHeaders.TENANT, tenantId)
      .contentType(APPLICATION_JSON);
    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(dataRefreshService.refreshData(tenantId)).thenReturn(expectedResponse);
    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk());
    verify(dataRefreshService, times(1)).refreshData(tenantId);
  }
}
