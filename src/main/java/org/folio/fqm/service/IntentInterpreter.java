package org.folio.fqm.service;

import org.folio.fqm.model.QuerySuggestionIntent;
import org.folio.fqm.model.QuerySuggestionMetadataContext;

import java.util.UUID;

public interface IntentInterpreter {

  QuerySuggestionIntent interpret(String naturalLanguageQuery, UUID preselectedEntityTypeId, QuerySuggestionMetadataContext metadataContext);
}
