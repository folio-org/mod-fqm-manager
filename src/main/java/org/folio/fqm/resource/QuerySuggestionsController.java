package org.folio.fqm.resource;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.domain.dto.QuerySuggestionRequest;
import org.folio.fqm.domain.dto.QuerySuggestionResponse;
import org.folio.fqm.service.QuerySuggestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class QuerySuggestionsController implements QuerySuggestionsApi {

  private final QuerySuggestionService querySuggestionService;

  @Override
  public ResponseEntity<QuerySuggestionResponse> suggestFqmQueries(QuerySuggestionRequest querySuggestionRequest) {
    return ResponseEntity.ok(querySuggestionService.suggestQueries(querySuggestionRequest));
  }

  @Override
  public Optional<NativeWebRequest> getRequest() {
    return QuerySuggestionsApi.super.getRequest();
  }
}
