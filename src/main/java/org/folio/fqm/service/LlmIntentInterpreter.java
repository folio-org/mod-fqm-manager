package org.folio.fqm.service;

import org.folio.fqm.model.QuerySuggestionIntent;
import org.folio.fqm.model.QuerySuggestionMetadataContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
public class LlmIntentInterpreter implements IntentInterpreter {

  private final LlmIntentClient llmIntentClient;
  private final ObjectMapper objectMapper;

  public LlmIntentInterpreter(LlmIntentClient llmIntentClient, ObjectMapper objectMapper) {
    this.llmIntentClient = llmIntentClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public QuerySuggestionIntent interpret(
    String naturalLanguageQuery,
    UUID preselectedEntityTypeId,
    QuerySuggestionMetadataContext metadataContext
  ) {
    try {
      String response = llmIntentClient.complete(buildSystemPrompt(), buildUserPrompt(naturalLanguageQuery, preselectedEntityTypeId, metadataContext));
      return objectMapper.readValue(response, QuerySuggestionIntent.class);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to interpret natural-language query with configured LLM.", e);
    }
  }

  private String buildSystemPrompt() {
    return """
      You are an assistant that converts natural-language reporting requests into structured FQM query intent.
      Return JSON only. Do not include markdown, comments, or explanatory text.
      Use this exact schema:
      {
        "entityTypeId": "uuid or null",
        "entityTypeLabel": "string or null",
        "filters": [
          {
            "fieldName": "string",
            "fieldLabel": "string",
            "operator": "$eq | $isNull | $olderThanDays",
            "value": "string"
          }
        ],
        "assumptions": ["string"],
        "clarificationQuestions": ["string"]
      }
      Rules:
      - Only use field names and entityTypeIds that are present in the provided metadata context.
      - Prefer $eq for direct value matches.
      - Use $isNull with value "true" for requests like "without invoices", "missing vendor", or "no status".
      - Use $olderThanDays for relative age filters like "older than 30 days".
      - If entity type is ambiguous, set entityTypeId to null and add a clarification question.
      - If a field is ambiguous, omit that filter and add a clarification question.
      - Keep assumptions and clarificationQuestions concise.
      """;
  }

  private String buildUserPrompt(
    String naturalLanguageQuery,
    UUID preselectedEntityTypeId,
    QuerySuggestionMetadataContext metadataContext
  ) throws Exception {
    return """
      Natural-language request:
      %s

      Preselected entity type id:
      %s

      Metadata context:
      %s
      """.formatted(
      naturalLanguageQuery,
      preselectedEntityTypeId == null ? "null" : preselectedEntityTypeId,
      objectMapper.writeValueAsString(metadataContext)
    );
  }
}
