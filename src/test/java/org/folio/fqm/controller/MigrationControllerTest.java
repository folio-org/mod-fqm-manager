package org.folio.fqm.controller;

import org.folio.fqm.resource.MigrationController;
import org.folio.fqm.service.MigrationService;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@WebMvcTest(MigrationController.class)
class MigrationControllerTest {
  private static final String GET_VERSION_URL = "/fqm/version";
  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private MigrationService migrationService;

  @MockBean
  private FolioExecutionContext executionContext;

  @Test
  void shouldReturnVersion() throws Exception {
    String tenantId = "tenant_01";
    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(migrationService.getLatestVersion()).thenReturn("1");
    RequestBuilder builder = MockMvcRequestBuilders
      .get(GET_VERSION_URL)
      .accept(MediaType.APPLICATION_JSON);
    mockMvc
      .perform(builder)
      .andExpect(status().isOk())
      .andExpect(content().string("1"));
  }
}
