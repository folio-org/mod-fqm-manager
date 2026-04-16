package org.folio.fqm.service;

import org.folio.fqm.model.QuerySuggestionEntityTypeContext;
import org.folio.fqm.model.QuerySuggestionIntent;
import org.folio.fqm.model.QuerySuggestionMetadataContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmIntentInterpreterTest {

  @Test
  void shouldParseStructuredIntentFromLlmResponse() {
    UUID entityTypeId = UUID.randomUUID();
    CapturingLlmIntentClient llmIntentClient = new CapturingLlmIntentClient("""
      {
        "entityTypeId": "%s",
        "entityTypeLabel": "Orders",
        "filters": [
          {
            "fieldName": "status",
            "fieldLabel": "Status",
            "operator": "$eq",
            "value": "Open"
          }
        ],
        "assumptions": ["Status was inferred as open."],
        "clarificationQuestions": []
      }
      """.formatted(entityTypeId));
    LlmIntentInterpreter interpreter = new LlmIntentInterpreter(llmIntentClient, new ObjectMapper());

    QuerySuggestionIntent result = interpreter.interpret(
      "show me open orders",
      null,
      new QuerySuggestionMetadataContext(List.of(
        new QuerySuggestionEntityTypeContext(entityTypeId, "orders", "Orders", List.of())
      ))
    );

    assertEquals(entityTypeId, result.entityTypeId());
    assertEquals("Orders", result.entityTypeLabel());
    assertEquals(1, result.filters().size());
    assertTrue(llmIntentClient.systemPrompt.contains("Return JSON only"));
    assertTrue(llmIntentClient.userPrompt.contains("show me open orders"));
    assertTrue(llmIntentClient.userPrompt.contains("Metadata context"));
  }

  private static final class CapturingLlmIntentClient implements LlmIntentClient {
    private final String response;
    private String systemPrompt;
    private String userPrompt;

    private CapturingLlmIntentClient(String response) {
      this.response = response;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
      this.systemPrompt = systemPrompt;
      this.userPrompt = userPrompt;
      return response;
    }
  }
}
