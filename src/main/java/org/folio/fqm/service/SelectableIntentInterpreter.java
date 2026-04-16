package org.folio.fqm.service;

import org.folio.fqm.model.QuerySuggestionIntent;
import org.folio.fqm.model.QuerySuggestionMetadataContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
@Primary
public class SelectableIntentInterpreter implements IntentInterpreter {

  private final StubIntentInterpreter stubIntentInterpreter;
  private final LlmIntentInterpreter llmIntentInterpreter;
  private final String interpreterMode;

  public SelectableIntentInterpreter(
    StubIntentInterpreter stubIntentInterpreter,
    LlmIntentInterpreter llmIntentInterpreter,
    @Value("${mod-fqm-manager.query-suggestions.interpreter:stub}") String interpreterMode
  ) {
    this.stubIntentInterpreter = stubIntentInterpreter;
    this.llmIntentInterpreter = llmIntentInterpreter;
    this.interpreterMode = interpreterMode == null ? "stub" : interpreterMode;
  }

  @Override
  public QuerySuggestionIntent interpret(
    String naturalLanguageQuery,
    UUID preselectedEntityTypeId,
    QuerySuggestionMetadataContext metadataContext
  ) {
    return switch (interpreterMode.toLowerCase(Locale.ROOT)) {
      case "llm" -> llmIntentInterpreter.interpret(naturalLanguageQuery, preselectedEntityTypeId, metadataContext);
      case "stub" -> stubIntentInterpreter.interpret(naturalLanguageQuery, preselectedEntityTypeId, metadataContext);
      default -> throw new IllegalStateException("Unsupported query suggestion interpreter mode: " + interpreterMode);
    };
  }
}
