package org.folio.fqm.service;

import org.folio.fqm.model.QuerySuggestionIntent;
import org.folio.fqm.model.QuerySuggestionEntityTypeContext;
import org.folio.fqm.model.QuerySuggestionField;
import org.folio.fqm.model.QuerySuggestionMetadataContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class LlmIntentInterpreter implements IntentInterpreter {
  private static final Pattern JSON_CODE_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*(\\{.*})\\s*```", Pattern.DOTALL);

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
      log.info("Raw LLM query suggestion response: {}", abbreviate(response, 1200));
      QuerySuggestionIntent intent = normalize(
        objectMapper.readValue(extractJsonObject(response), QuerySuggestionIntent.class),
        preselectedEntityTypeId,
        metadataContext
      );
      log.info(
        "Normalized LLM intent: entityTypeId={}, entityTypeLabel={}, filterCount={}, assumptionsCount={}, clarificationCount={}",
        intent.entityTypeId(),
        intent.entityTypeLabel(),
        intent.filters().size(),
        intent.assumptions().size(),
        intent.clarificationQuestions().size()
      );
      return intent;
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
            "operator": "$eq | $contains | $isNull | $olderThanDays",
            "value": "string"
          }
        ],
        "assumptions": ["string"],
        "clarificationQuestions": ["string"]
      }
      Rules:
      - Only use field names and entityTypeIds that are present in the provided metadata context.
      - Always return all list fields as arrays. Use [] when there are no filters, assumptions, or clarification questions.
      - Use $eq for exact or enumerated matches such as boolean values, statuses, and exact identifiers.
      - Use $contains for partial text matches or topical searches, such as requests like "about Mexico", "title contains history", "instances with publisher Oxford", or "users with email gmail.com".
      - When the user describes a topic or subject rather than an exact full field value, prefer $contains over $eq for string fields.
      - Use $isNull with value "true" for requests like "without invoices", "missing vendor", or "no status".
      - Use $olderThanDays for relative age filters like "older than 30 days".
      - If entity type is ambiguous, set entityTypeId to null and add a clarification question.
      - If a field is ambiguous, omit that filter and add a clarification question.
      - Keep assumptions and clarificationQuestions concise.

      Example 1:
      Request: list of all active users without a first name
      Response:
      {
        "entityTypeId": "bb058933-cd06-4539-bd3a-6f248ff98ee2",
        "entityTypeLabel": "Simple Users",
        "filters": [
          {
            "fieldName": "active",
            "fieldLabel": "Active",
            "operator": "$eq",
            "value": "true"
          },
          {
            "fieldName": "first_name",
            "fieldLabel": "First name",
            "operator": "$isNull",
            "value": "true"
          }
        ],
        "assumptions": [],
        "clarificationQuestions": []
      }

      Example 2:
      Request: instances cataloged older than 30 days
      Response:
      {
        "entityTypeId": "8fc4a9d2-7ccf-4233-afb8-796911839862",
        "entityTypeLabel": "Instances",
        "filters": [
          {
            "fieldName": "cataloged_date",
            "fieldLabel": "Cataloged date",
            "operator": "$olderThanDays",
            "value": "30"
          }
        ],
        "assumptions": [],
        "clarificationQuestions": []
      }

      Example 3:
      Request: all instances about Mexico
      Response:
      {
        "entityTypeId": "8fc4a9d2-7ccf-4233-afb8-796911839862",
        "entityTypeLabel": "Instances",
        "filters": [
          {
            "fieldName": "title",
            "fieldLabel": "Resource title",
            "operator": "$contains",
            "value": "Mexico"
          }
        ],
        "assumptions": [],
        "clarificationQuestions": []
      }

      Example 4:
      Request: active users without a first name
      Response:
      {
        "entityTypeId": "bb058933-cd06-4539-bd3a-6f248ff98ee2",
        "entityTypeLabel": "Simple Users",
        "filters": [
          {
            "fieldName": "active",
            "fieldLabel": "Active",
            "operator": "$eq",
            "value": "true"
          },
          {
            "fieldName": "first_name",
            "fieldLabel": "First name",
            "operator": "$isNull",
            "value": "true"
          }
        ],
        "assumptions": [],
        "clarificationQuestions": []
      }

      Example 5:
      Request: instances with title Mexico
      Response:
      {
        "entityTypeId": "8fc4a9d2-7ccf-4233-afb8-796911839862",
        "entityTypeLabel": "Instances",
        "filters": [
          {
            "fieldName": "title",
            "fieldLabel": "Resource title",
            "operator": "$eq",
            "value": "Mexico"
          }
        ],
        "assumptions": [],
        "clarificationQuestions": []
      }
      """;
  }

  private QuerySuggestionIntent normalize(
    QuerySuggestionIntent intent,
    UUID preselectedEntityTypeId,
    QuerySuggestionMetadataContext metadataContext
  ) {
    UUID resolvedEntityTypeId = resolveEntityTypeId(intent, preselectedEntityTypeId, metadataContext);
    String resolvedEntityTypeLabel = resolveEntityTypeLabel(intent, resolvedEntityTypeId, metadataContext);

    return new QuerySuggestionIntent(
      resolvedEntityTypeId,
      resolvedEntityTypeLabel,
      intent.filters() == null ? List.of() : List.copyOf(intent.filters()),
      intent.assumptions() == null ? List.of() : List.copyOf(intent.assumptions()),
      intent.clarificationQuestions() == null ? List.of() : List.copyOf(intent.clarificationQuestions())
    );
  }

  private UUID resolveEntityTypeId(
    QuerySuggestionIntent intent,
    UUID preselectedEntityTypeId,
    QuerySuggestionMetadataContext metadataContext
  ) {
    if (preselectedEntityTypeId != null) {
      return preselectedEntityTypeId;
    }
    if (intent.entityTypeId() != null) {
      return intent.entityTypeId();
    }
    if (intent.entityTypeLabel() == null || metadataContext == null || metadataContext.entityTypes() == null) {
      return null;
    }

    String requested = normalizeEntityText(intent.entityTypeLabel());
    List<QuerySuggestionEntityTypeContext> matches = metadataContext.entityTypes().stream()
      .filter(entityType -> matchesEntityType(entityType, requested))
      .toList();

    if (matches.size() == 1) {
      return matches.get(0).id();
    }

    return null;
  }

  private String resolveEntityTypeLabel(
    QuerySuggestionIntent intent,
    UUID resolvedEntityTypeId,
    QuerySuggestionMetadataContext metadataContext
  ) {
    if (resolvedEntityTypeId == null || metadataContext == null || metadataContext.entityTypes() == null) {
      return intent.entityTypeLabel();
    }

    return metadataContext.entityTypes().stream()
      .filter(entityType -> resolvedEntityTypeId.equals(entityType.id()))
      .map(QuerySuggestionEntityTypeContext::label)
      .findFirst()
      .orElse(intent.entityTypeLabel());
  }

  private boolean matchesEntityType(QuerySuggestionEntityTypeContext entityType, String requested) {
    List<String> candidates = List.of(
      normalizeEntityText(entityType.label()),
      normalizeEntityText(entityType.name())
    );

    return candidates.stream()
      .filter(candidate -> !candidate.isBlank())
      .anyMatch(candidate -> candidate.equals(requested)
        || singularize(candidate).equals(singularize(requested))
        || candidate.contains(requested)
        || requested.contains(candidate));
  }

  private String normalizeEntityText(String value) {
    if (value == null) {
      return "";
    }

    return value.toLowerCase(Locale.ROOT)
      .replace('_', ' ')
      .replaceAll("[^a-z0-9 ]", " ")
      .replaceAll("\\s+", " ")
      .trim();
  }

  private String singularize(String value) {
    if (value.endsWith("ies") && value.length() > 3) {
      return value.substring(0, value.length() - 3) + "y";
    }
    if (value.endsWith("s") && value.length() > 1) {
      return value.substring(0, value.length() - 1);
    }
    return value;
  }

  private String abbreviate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength) + "...";
  }

  static String extractJsonObject(String response) {
    if (response == null || response.isBlank()) {
      throw new IllegalArgumentException("LLM returned blank content.");
    }

    String trimmed = response.trim();
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
      return trimmed;
    }

    Matcher matcher = JSON_CODE_BLOCK_PATTERN.matcher(trimmed);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }

    int firstBrace = trimmed.indexOf('{');
    if (firstBrace < 0) {
      throw new IllegalArgumentException("LLM response did not contain a JSON object. Response starts with: " + trimmed.substring(0, Math.min(trimmed.length(), 120)));
    }

    int depth = 0;
    boolean inString = false;
    boolean escaping = false;
    for (int i = firstBrace; i < trimmed.length(); i++) {
      char current = trimmed.charAt(i);

      if (escaping) {
        escaping = false;
        continue;
      }

      if (current == '\\' && inString) {
        escaping = true;
        continue;
      }

      if (current == '"') {
        inString = !inString;
        continue;
      }

      if (inString) {
        continue;
      }

      if (current == '{') {
        depth++;
      } else if (current == '}') {
        depth--;
        if (depth == 0) {
          return trimmed.substring(firstBrace, i + 1);
        }
      }
    }

    throw new IllegalArgumentException("LLM response contained '{' but no complete JSON object. Response starts with: "
      + trimmed.substring(0, Math.min(trimmed.length(), 120)));
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

      Metadata context summary:
      %s
      """.formatted(
      naturalLanguageQuery,
      preselectedEntityTypeId == null ? "null" : preselectedEntityTypeId,
      summarizeMetadataContext(metadataContext)
    );
  }

  private String summarizeMetadataContext(QuerySuggestionMetadataContext metadataContext) {
    return metadataContext.entityTypes().stream()
      .map(entityType -> {
        String fieldSummary = entityType.fields().stream()
          .filter(field -> field.queryable() && !field.hidden())
          .limit(30)
          .map(this::summarizeField)
          .reduce((left, right) -> left + "\n" + right)
          .orElse("  - no queryable fields");

        return """
          Entity Type:
          - id: %s
          - name: %s
          - label: %s
          - queryable fields:
          %s
          """.formatted(entityType.id(), entityType.name(), entityType.label(), fieldSummary);
      })
      .reduce((left, right) -> left + "\n\n" + right)
      .orElse("No entity types available.");
  }

  private String summarizeField(QuerySuggestionField field) {
    String type = field.dataType() == null ? "unknown" : field.dataType().type();
    String values = field.values() == null || field.values().isEmpty()
      ? ""
      : " values=" + field.values().stream()
      .limit(8)
      .map(value -> value.value() + (value.label() == null || value.label().equals(value.value()) ? "" : " (" + value.label() + ")"))
      .reduce((left, right) -> left + ", " + right)
      .orElse("");
    String fullyQualified = field.fullyQualifiedLabel() == null ? "" : " fqLabel=" + field.fullyQualifiedLabel();
    String sourceAlias = field.sourceAlias() == null ? "" : " sourceAlias=" + field.sourceAlias();

    return "  - %s | label=%s%s%s | type=%s%s".formatted(
      field.name(),
      field.label(),
      fullyQualified,
      sourceAlias,
      type,
      values
    );
  }
}
