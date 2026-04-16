package org.folio.fqm.service;

import org.folio.fqm.model.QuerySuggestionIntent;
import org.folio.fqm.model.QuerySuggestionMetadataContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectableIntentInterpreterTest {

  @Test
  void shouldDelegateToStubInterpreterWhenConfigured() {
    SelectableIntentInterpreter interpreter = new SelectableIntentInterpreter(
      new FixedStubIntentInterpreter("stub"),
      new FixedLlmIntentInterpreter("llm"),
      "stub"
    );

    QuerySuggestionIntent result = interpreter.interpret("query", null, new QuerySuggestionMetadataContext(List.of()));

    assertEquals("stub", result.entityTypeLabel());
  }

  @Test
  void shouldDelegateToLlmInterpreterWhenConfigured() {
    SelectableIntentInterpreter interpreter = new SelectableIntentInterpreter(
      new FixedStubIntentInterpreter("stub"),
      new FixedLlmIntentInterpreter("llm"),
      "llm"
    );

    QuerySuggestionIntent result = interpreter.interpret("query", null, new QuerySuggestionMetadataContext(List.of()));

    assertEquals("llm", result.entityTypeLabel());
  }

  private static final class FixedStubIntentInterpreter extends StubIntentInterpreter {
    private final String label;

    private FixedStubIntentInterpreter(String label) {
      this.label = label;
    }

    @Override
    public QuerySuggestionIntent interpret(String naturalLanguageQuery, UUID preselectedEntityTypeId, QuerySuggestionMetadataContext metadataContext) {
      return new QuerySuggestionIntent(null, label, List.of(), List.of(), List.of());
    }
  }

  private static final class FixedLlmIntentInterpreter extends LlmIntentInterpreter {
    private final String label;

    private FixedLlmIntentInterpreter(String label) {
      super((systemPrompt, userPrompt) -> "{}", new ObjectMapper());
      this.label = label;
    }

    @Override
    public QuerySuggestionIntent interpret(String naturalLanguageQuery, UUID preselectedEntityTypeId, QuerySuggestionMetadataContext metadataContext) {
      return new QuerySuggestionIntent(null, label, List.of(), List.of(), List.of());
    }
  }
}
