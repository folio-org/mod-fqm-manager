package org.folio.fqm.service;

import org.folio.fqm.domain.dto.QuerySuggestion;
import org.folio.fqm.domain.dto.QuerySuggestionRequest;
import org.folio.fqm.domain.dto.QuerySuggestionResponse;
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

  public QuerySuggestionService(QuerySuggestionMetadataContextBuilder metadataContextBuilder) {
    this.metadataContextBuilder = metadataContextBuilder;
  }

  public QuerySuggestionResponse suggestQueries(QuerySuggestionRequest request) {
    String trimmedQuery = request.getNaturalLanguageQuery().trim();
    int maxSuggestions = request.getMaxSuggestions() == null ? 3 : request.getMaxSuggestions();
    QuerySuggestionMetadataContext metadataContext = metadataContextBuilder.buildContext(MVP_ENTITY_TYPE_IDS);

    List<String> sharedAssumptions = List.of(
      "This is an MVP stub response and does not yet use metadata to generate real FQL filters.",
      "Metadata context loaded for %s entity types.".formatted(metadataContext.entityTypes().size()),
      "Relative date phrases should be reviewed before running the query."
    );

    QuerySuggestion suggestion = new QuerySuggestion()
      .entityTypeId(request.getEntityTypeId())
      .summary("Draft suggestion for: " + trimmedQuery)
      .fqlQuery(buildPlaceholderFql(trimmedQuery))
      .validationStatus(QuerySuggestion.ValidationStatusEnum.NEEDS_REVIEW)
      .assumptions(sharedAssumptions);

    QuerySuggestionResponse response = new QuerySuggestionResponse()
      .suggestions(List.of(suggestion).subList(0, Math.min(1, maxSuggestions)))
      .assumptions(sharedAssumptions)
      .clarificationQuestions(List.of(
        "Which entity type should this report start from?",
        "Should relative dates like \"older than 30 days\" be evaluated against today or another reference date?"
      ));

    return response;
  }

  private String buildPlaceholderFql(String naturalLanguageQuery) {
    String sanitizedQuery = naturalLanguageQuery.replace("\"", "\\\"");
    return """
      {
        "_note": {
          "$eq": "Placeholder generated from natural-language request: %s"
        }
      }
      """.formatted(sanitizedQuery);
  }
}
