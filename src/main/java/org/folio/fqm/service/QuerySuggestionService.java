package org.folio.fqm.service;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.fqm.domain.dto.QuerySuggestionRequest;
import org.folio.fqm.domain.dto.QuerySuggestionResponse;
import org.springframework.stereotype.Service;

/**
 * Placeholder service for the proof-of-concept query suggestion API.
 *
 * <p>The intended end-state is that this API will call an LLM, and the LLM will use the embedded
 * MCP service to fetch runtime FQM metadata before creating suggestions. Until that integration is
 * added, this service returns an explicit stub response instead of pretending MCP itself generated
 * the suggestions.
 */
@Service
@RequiredArgsConstructor
public class QuerySuggestionService {

  private final FqmMcpService fqmMcpService;

  /**
   * Returns a placeholder response until the LLM-backed suggestion flow is implemented.
   *
   * @param request natural-language query suggestion request
   * @return structured query suggestion response
   */
  public QuerySuggestionResponse suggestQueries(QuerySuggestionRequest request) {
    List<String> toolNames = fqmMcpService.listTools().tools().stream()
      .map(McpSchema.Tool::name)
      .toList();

    return new QuerySuggestionResponse()
      .suggestions(List.of())
      .assumptions(List.of(
        "LLM integration is not yet implemented in this proof of concept.",
        "A future LLM should use the embedded MCP tools for runtime FQM context: " + String.join(", ", toolNames) + '.',
        "Relative date phrases should be reviewed before running any generated query."
      ))
      .clarificationQuestions(List.of(
        "Which entity type should this report start from?",
        "Should relative dates like \"older than 30 days\" be evaluated against April 15, 2026 or another reference date?"
      ));
  }
}
