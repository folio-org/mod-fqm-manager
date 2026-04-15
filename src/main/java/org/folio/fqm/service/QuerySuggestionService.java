package org.folio.fqm.service;

import org.folio.fqm.domain.dto.CandidateEntityType;
import org.folio.fqm.domain.dto.QuerySuggestion;
import org.folio.fqm.domain.dto.QuerySuggestionRequest;
import org.folio.fqm.domain.dto.QuerySuggestionResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuerySuggestionService {

  public QuerySuggestionResponse suggestQueries(QuerySuggestionRequest request) {
    String trimmedQuery = request.getNaturalLanguageQuery().trim();
    int maxSuggestions = request.getMaxSuggestions() == null ? 3 : request.getMaxSuggestions();

    List<String> sharedAssumptions = List.of(
      "This is an MVP stub response and does not yet use live entity metadata.",
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
      .candidateEntityTypes(List.of(new CandidateEntityType()
        .entityTypeId(request.getEntityTypeId())
        .label(request.getEntityTypeId() == null ? "Needs entity type selection" : "Preselected entity type")
        .confidence(request.getEntityTypeId() == null ? 0.25 : 0.9)))
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
