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

  @Test
  void shouldExtractJsonWhenModelWrapsItInExplanatoryText() {
    UUID entityTypeId = UUID.randomUUID();
    LlmIntentInterpreter interpreter = new LlmIntentInterpreter(
      new CapturingLlmIntentClient("""
        This looks like the best interpretation:
        ```json
        {
          "entityTypeId": "%s",
          "entityTypeLabel": "Orders",
          "filters": [],
          "assumptions": ["Used the only matching entity type."],
          "clarificationQuestions": []
        }
        ```
        """.formatted(entityTypeId)),
      new ObjectMapper()
    );

    QuerySuggestionIntent result = interpreter.interpret(
      "show me orders",
      null,
      new QuerySuggestionMetadataContext(List.of(
        new QuerySuggestionEntityTypeContext(entityTypeId, "orders", "Orders", List.of())
      ))
    );

    assertEquals(entityTypeId, result.entityTypeId());
    assertEquals("Orders", result.entityTypeLabel());
  }

  @Test
  void shouldDefaultMissingListsToEmpty() {
    UUID entityTypeId = UUID.randomUUID();
    LlmIntentInterpreter interpreter = new LlmIntentInterpreter(
      new CapturingLlmIntentClient("""
        {
          "entityTypeId": "%s",
          "entityTypeLabel": "Orders"
        }
        """.formatted(entityTypeId)),
      new ObjectMapper()
    );

    QuerySuggestionIntent result = interpreter.interpret(
      "show me orders",
      null,
      new QuerySuggestionMetadataContext(List.of(
        new QuerySuggestionEntityTypeContext(entityTypeId, "orders", "Orders", List.of())
      ))
    );

    assertTrue(result.filters().isEmpty());
    assertTrue(result.assumptions().isEmpty());
    assertTrue(result.clarificationQuestions().isEmpty());
  }

  @Test
  void shouldResolveEntityTypeIdFromApproximateLabelWhenLlmOmitsId() {
    UUID usersId = UUID.fromString("bb058933-cd06-4539-bd3a-6f248ff98ee2");
    UUID instancesId = UUID.fromString("8fc4a9d2-7ccf-4233-afb8-796911839862");
    LlmIntentInterpreter interpreter = new LlmIntentInterpreter(
      new CapturingLlmIntentClient("""
        {
          "entityTypeId": null,
          "entityTypeLabel": "User",
          "filters": [
            {
              "fieldName": "active",
              "fieldLabel": "Active",
              "operator": "$eq",
              "value": "true"
            }
          ],
          "assumptions": [],
          "clarificationQuestions": ["What type of user are you looking for?"]
        }
        """),
      new ObjectMapper()
    );

    QuerySuggestionIntent result = interpreter.interpret(
      "list active users",
      null,
      new QuerySuggestionMetadataContext(List.of(
        new QuerySuggestionEntityTypeContext(usersId, "simple_user_details", "Simple Users", List.of()),
        new QuerySuggestionEntityTypeContext(instancesId, "simple_instance", "Instances", List.of())
      ))
    );

    assertEquals(usersId, result.entityTypeId());
    assertEquals("Simple Users", result.entityTypeLabel());
    assertEquals(1, result.filters().size());
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
