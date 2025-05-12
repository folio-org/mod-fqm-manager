package org.folio.fqm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.warnings.DeprecatedEntityWarning;
import org.folio.fqm.migration.warnings.Warning;
import org.folio.fqm.resource.MigrationController;
import org.folio.fqm.service.MigrationService;
import org.folio.querytool.domain.dto.FqmMigrateRequest;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.i18n.service.TranslationService;
import org.folio.spring.integration.XOkapiHeaders;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MigrationController.class)
class MigrationControllerTest {
  private static final String GET_VERSION_URL = "/fqm/version";

  private static final String POST_FQM_MIGRATE_REQUEST = "/fqm/migrate";
  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private MigrationService migrationService;

  @MockitoBean
  private FolioExecutionContext executionContext;

  @MockitoBean
  private TranslationService translationService;

  @Test
  void shouldReturnVersion() throws Exception {
    String tenantId = "tenant_01";
    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(migrationService.getLatestVersion()).thenReturn("1");
    RequestBuilder builder = MockMvcRequestBuilders
      .get(GET_VERSION_URL)
      .accept(APPLICATION_JSON);
    mockMvc
      .perform(builder)
      .andExpect(status().isOk())
      .andExpect(content().string("1"));
  }

  @Test
  void shouldMigrateFqmSuccessfully() throws Exception {
    String tenantId = "tenant_01";
    UUID entityTypeID = UUID.randomUUID();
    FqmMigrateRequest fqmMigrateRequest = getFqmMigrateRequest(entityTypeID);

    MigratableQueryInformation migratableQueryInformation = mock(MigratableQueryInformation.class);

    Warning deprecatedEntityWarning = mock(DeprecatedEntityWarning.class);
    List<Warning> warningList = List.of(deprecatedEntityWarning);

    when(deprecatedEntityWarning.getType()).thenReturn(Warning.WarningType.DEPRECATED_ENTITY);
    when(deprecatedEntityWarning.getDescription(any())).thenReturn("def");

    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(migratableQueryInformation.entityTypeId()).thenReturn(entityTypeID);
    when(migratableQueryInformation.fqlQuery()).thenReturn("users.active");
    when(migratableQueryInformation.fields()).thenReturn(List.of("field1", "field2"));
    when(migratableQueryInformation.warnings()).thenReturn(warningList);

    when(migrationService.migrate(any(MigratableQueryInformation.class))).thenReturn(migratableQueryInformation);

    RequestBuilder builder = MockMvcRequestBuilders
      .post(POST_FQM_MIGRATE_REQUEST)
      .header(XOkapiHeaders.TENANT, tenantId)
      .contentType(APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString(fqmMigrateRequest));

    mockMvc
      .perform(builder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.entityTypeId", is(entityTypeID.toString())))
      .andExpect(jsonPath("$.fqlQuery", is("users.active")))
      .andExpect(jsonPath("$.fields", is(List.of("field1", "field2"))))
      .andExpect(jsonPath("$.warnings[0].type", is("DEPRECATED_ENTITY")))  // Matching the warning type
      .andExpect(jsonPath("$.warnings[0].description", is("def")));  // Matching the warning description
  }
  @NotNull
  private static FqmMigrateRequest getFqmMigrateRequest(UUID entityTypeID) {
    FqmMigrateRequest fqmMigrateRequest = new FqmMigrateRequest();
    fqmMigrateRequest.setEntityTypeId(entityTypeID);
    fqmMigrateRequest.setFqlQuery("users.active");
    fqmMigrateRequest.setFields(List.of("field1", "field2"));
    return fqmMigrateRequest;
  }
}
