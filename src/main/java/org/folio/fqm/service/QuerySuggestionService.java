package org.folio.fqm.service;

import org.folio.fqm.domain.dto.QuerySuggestion;
import org.folio.fqm.domain.dto.QuerySuggestionRequest;
import org.folio.fqm.domain.dto.QuerySuggestionResponse;
import org.folio.fqm.model.QuerySuggestionIntent;
import org.folio.fqm.model.QuerySuggestionIntentFilter;
import org.folio.fqm.model.QuerySuggestionMetadataContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class QuerySuggestionService {

  private static final List<UUID> MVP_ENTITY_TYPE_IDS = List.of(
    UUID.fromString("bb058933-cd06-4539-bd3a-6f248ff98ee2"),
    UUID.fromString("8fc4a9d2-7ccf-4233-afb8-796911839862")
  );

  private final QuerySuggestionMetadataContextBuilder metadataContextBuilder;
  private final IntentInterpreter intentInterpreter;

  public QuerySuggestionService(QuerySuggestionMetadataContextBuilder metadataContextBuilder, IntentInterpreter intentInterpreter) {
    this.metadataContextBuilder = metadataContextBuilder;
    this.intentInterpreter = intentInterpreter;
  }

  public QuerySuggestionResponse suggestQueries(QuerySuggestionRequest request) {
    String trimmedQuery = request.getNaturalLanguageQuery().trim();
    int maxSuggestions = request.getMaxSuggestions() == null ? 3 : request.getMaxSuggestions();
    QuerySuggestionMetadataContext metadataContext = metadataContextBuilder.buildContext(MVP_ENTITY_TYPE_IDS);
    QuerySuggestionIntent intent = intentInterpreter.interpret(trimmedQuery, request.getEntityTypeId(), metadataContext);

    List<String> sharedAssumptions = new java.util.ArrayList<>(List.of(
      "This is an MVP stub response and does not yet use metadata to generate real FQL filters.",
      "Metadata context loaded for %s entity types.".formatted(metadataContext.entityTypes().size()),
      "Relative date phrases should be reviewed before running the query."
    ));
    sharedAssumptions.addAll(intent.assumptions());

    QuerySuggestion suggestion = new QuerySuggestion()
      .entityTypeId(intent.entityTypeId() != null ? intent.entityTypeId() : request.getEntityTypeId())
      .summary(buildSummary(trimmedQuery, intent))
      .fqlQuery(buildPlaceholderFql(intent, trimmedQuery))
      .validationStatus(QuerySuggestion.ValidationStatusEnum.NEEDS_REVIEW)
      .assumptions(sharedAssumptions);

    QuerySuggestionResponse response = new QuerySuggestionResponse()
      .suggestions(List.of(suggestion).subList(0, Math.min(1, maxSuggestions)))
      .assumptions(sharedAssumptions)
      .clarificationQuestions(mergeClarificationQuestions(intent));

    return response;
  }

  private String buildSummary(String naturalLanguageQuery, QuerySuggestionIntent intent) {
    if (intent.entityTypeLabel() != null && !intent.filters().isEmpty()) {
      return "Draft %s query with %s interpreted filters".formatted(
        intent.entityTypeLabel(),
        intent.filters().size()
      );
    }
    if (intent.entityTypeLabel() != null) {
      return "Draft %s query for: %s".formatted(intent.entityTypeLabel(), naturalLanguageQuery);
    }
    return "Draft suggestion for: " + naturalLanguageQuery;
  }

  private List<String> mergeClarificationQuestions(QuerySuggestionIntent intent) {
    java.util.LinkedHashSet<String> questions = new java.util.LinkedHashSet<>(intent.clarificationQuestions());
    questions.add("Should relative dates like \"older than 30 days\" be evaluated against today or another reference date?");
    return List.copyOf(questions);
  }

  private String buildPlaceholderFql(QuerySuggestionIntent intent, String naturalLanguageQuery) {
    if (intent.filters().isEmpty()) {
      String sanitizedQuery = naturalLanguageQuery.replace("\"", "\\\"");
      return """
        {
          "_note": {
            "$eq": "Placeholder generated from natural-language request: %s"
          }
        }
        """.formatted(sanitizedQuery);
    }

    String filters = intent.filters().stream()
      .map(this::formatFilter)
      .collect(java.util.stream.Collectors.joining(",\n"));
    return """
      {
      %s
      }
      """.formatted(filters);
  }

  private String formatFilter(QuerySuggestionIntentFilter filter) {
    return """
        "%s": {
          "%s": %s
        }""".formatted(
      filter.fieldName(),
      filter.operator(),
      formatValue(filter.value())
    );
  }

  private String formatValue(String value) {
    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
      return value.toLowerCase(java.util.Locale.ROOT);
    }
    if (value.chars().allMatch(Character::isDigit)) {
      return value;
    }
    return "\"%s\"".formatted(value.replace("\"", "\\\""));
  }
}
