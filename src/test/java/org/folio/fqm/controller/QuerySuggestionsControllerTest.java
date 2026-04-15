package org.folio.fqm.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.folio.fqm.domain.dto.EntityTypeSummary;
import org.folio.fqm.exceptionhandler.FqmExceptionHandler;
import org.folio.fqm.resource.QuerySuggestionsController;
import org.folio.fqm.service.EntityTypeService;
import org.folio.fqm.service.FqmMcpService;
import org.folio.fqm.service.MigrationService;
import org.folio.fqm.service.QuerySuggestionService;
import org.folio.querytool.domain.dto.ColumnValues;
import org.folio.querytool.domain.dto.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class QuerySuggestionsControllerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    FqmMcpService fqmMcpService = new FqmMcpService(
      new TestEntityTypeService(),
      new TestMigrationService(),
      objectMapper,
      "2025-03-26",
      "mod-fqm-manager",
      "test",
      "Test MCP instructions"
    );
    QuerySuggestionsController controller = new QuerySuggestionsController(new QuerySuggestionService(fqmMcpService));
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
      .setControllerAdvice(new FqmExceptionHandler())
      .build();
  }

  @Test
  void shouldReturnStubResponseUntilLlmIntegrationExists() throws Exception {
    String request = """
      {
        "naturalLanguageQuery": "show me open orders older than 30 days without invoices",
        "maxSuggestions": 2
      }
      """;

    mockMvc.perform(post("/fqm-query-suggestions")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .content(request))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.suggestions", hasSize(0)))
      .andExpect(jsonPath("$.assumptions", hasSize(3)))
      .andExpect(jsonPath("$.assumptions[0]", containsString("LLM integration is not yet implemented")))
      .andExpect(jsonPath("$.assumptions[1]", containsString(FqmMcpService.TOOL_LIST_ENTITY_TYPES)))
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

  private static final class TestEntityTypeService extends EntityTypeService {
    private TestEntityTypeService() {
      super(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Override
    public List<EntityTypeSummary> getEntityTypeSummary(Set<UUID> entityTypeIds, boolean includeInaccessible, boolean includeAll) {
      return List.of();
    }

    @Override
    public EntityType getEntityTypeDefinition(UUID entityTypeId, boolean includeHidden) {
      return new EntityType();
    }

    @Override
    public ColumnValues getFieldValues(UUID entityTypeId, String fieldName, String searchText) {
      return new ColumnValues().content(List.of());
    }
  }

  private static final class TestMigrationService extends MigrationService {
    private TestMigrationService() {
      super(null, null, null, null, null, null, null);
    }

    @Override
    public String getLatestVersion() {
      return "0";
    }
  }
}
