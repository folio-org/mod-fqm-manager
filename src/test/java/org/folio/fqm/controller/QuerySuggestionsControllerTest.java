package org.folio.fqm.controller;

import tools.jackson.databind.ObjectMapper;
import org.folio.fqm.exceptionhandler.FqmExceptionHandler;
import org.folio.fqm.resource.QuerySuggestionsController;
import org.folio.fql.service.FqlValidationService;
import org.folio.fqm.service.StubIntentInterpreter;
import org.folio.fqm.service.ClockService;
import org.folio.fqm.service.QuerySuggestionFqlBuilder;
import org.folio.fqm.service.QuerySuggestionMetadataContextBuilder;
import org.folio.fqm.service.QuerySuggestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuerySuggestionsControllerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    QuerySuggestionMetadataContextBuilder metadataContextBuilder = new QuerySuggestionMetadataContextBuilder(
      new StubEntityTypeFlatteningService(),
      new TestFolioExecutionContext("tenant_01")
    );
    QuerySuggestionFqlBuilder fqlBuilder = new QuerySuggestionFqlBuilder(
      new StubEntityTypeService(),
      new StubFqlValidationService(),
      new ClockService()
    );
    QuerySuggestionsController controller = new QuerySuggestionsController(
      new QuerySuggestionService(metadataContextBuilder, new StubIntentInterpreter(), fqlBuilder)
    );
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
      .setControllerAdvice(new FqmExceptionHandler())
      .build();
  }

  @Test
  void shouldReturnDraftQuerySuggestions() throws Exception {
    UUID entityTypeId = UUID.randomUUID();
    String request = """
      {
        "naturalLanguageQuery": "show me open orders older than 30 days without invoices",
        "entityTypeId": "%s",
        "maxSuggestions": 2
      }
      """.formatted(entityTypeId);

    mockMvc.perform(post("/fqm-query-suggestions")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .content(request))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.suggestions", hasSize(1)))
      .andExpect(jsonPath("$.suggestions[0].entityTypeId", is(entityTypeId.toString())))
      .andExpect(jsonPath("$.suggestions[0].summary", is("Draft suggestion for: show me open orders older than 30 days without invoices")))
      .andExpect(jsonPath("$.suggestions[0].validationStatus", is("NEEDS_REVIEW")))
      .andExpect(jsonPath("$.assumptions[1]", is("Metadata context loaded for 2 entity types.")))
      .andExpect(jsonPath("$.clarificationQuestions", hasSize(2)));
  }

  @Test
  void shouldRejectRequestMissingNaturalLanguageQuery() throws Exception {
    mockMvc.perform(post("/fqm-query-suggestions")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
      .content(objectMapper.writeValueAsString(new Object())))
      .andExpect(status().isBadRequest());
  }

  private static class StubEntityTypeFlatteningService extends org.folio.fqm.service.EntityTypeFlatteningService {
    StubEntityTypeFlatteningService() {
      super(null, null, null, null, null);
    }

    @Override
    public org.folio.querytool.domain.dto.EntityType getFlattenedEntityType(java.util.UUID entityTypeId, String tenantId, boolean preserveAllColumns) {
      return new org.folio.querytool.domain.dto.EntityType()
        .id(entityTypeId.toString())
        .name("stub")
        .labelAlias("Stub")
        .columns(java.util.List.of());
    }
  }

  private record TestFolioExecutionContext(String tenantId) implements org.folio.spring.FolioExecutionContext {
    @Override
    public String getTenantId() {
      return tenantId;
    }
  }

  private static class StubEntityTypeService extends org.folio.fqm.service.EntityTypeService {
    StubEntityTypeService() {
      super(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Override
    public org.folio.querytool.domain.dto.EntityType getEntityTypeDefinition(java.util.UUID entityTypeId, boolean includeHidden) {
      return new org.folio.querytool.domain.dto.EntityType()
        .id(entityTypeId.toString())
        .name("stub")
        .labelAlias("Stub")
        .columns(java.util.List.of(
          new org.folio.querytool.domain.dto.EntityTypeColumn().name("status").dataType(new org.folio.querytool.domain.dto.StringType()),
          new org.folio.querytool.domain.dto.EntityTypeColumn().name("invoiceNumber").dataType(new org.folio.querytool.domain.dto.StringType()),
          new org.folio.querytool.domain.dto.EntityTypeColumn().name("createdDate").dataType(new org.folio.querytool.domain.dto.DateTimeType())
        ));
    }
  }

  private static class StubFqlValidationService extends FqlValidationService {
    StubFqlValidationService() {
      super(null);
    }

    @Override
    public java.util.Map<String, String> validateFql(org.folio.querytool.domain.dto.EntityType entityType, String fqlQuery) {
      return java.util.Map.of();
    }
  }
}
