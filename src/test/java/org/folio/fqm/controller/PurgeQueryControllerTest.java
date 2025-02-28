package org.folio.fqm.controller;

import org.folio.fqm.resource.PurgeQueryController;
import org.folio.fqm.service.QueryManagementService;
import org.folio.spring.integration.XOkapiHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.folio.fqm.domain.dto.PurgedQueries;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PurgeQueryController.class)
class PurgeQueryControllerTest {
  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private QueryManagementService queryManagementService;

  @Test
  void shouldReturn200ForDeletingQueries() throws Exception {
    List<UUID> queryIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    String tenant_Id = "tenant_01";
    PurgedQueries purgedQueries = new PurgedQueries().deletedQueryIds(queryIds);
    RequestBuilder requestBuilder = MockMvcRequestBuilders.post("/query/purge")
      .header(XOkapiHeaders.TENANT, tenant_Id)
      .contentType(APPLICATION_JSON);

    when(queryManagementService.deleteOldQueries()).thenReturn(purgedQueries);
    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.deletedQueryIds[0]", is(queryIds.get(0).toString())))
      .andExpect(jsonPath("$.deletedQueryIds[1]", is(queryIds.get(1).toString())));
  }
}
