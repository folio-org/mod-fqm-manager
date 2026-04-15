package org.folio.fqm.model;

import java.util.List;
import java.util.UUID;

public record QuerySuggestionIntent(
  UUID entityTypeId,
  String entityTypeLabel,
  List<QuerySuggestionIntentFilter> filters,
  List<String> assumptions,
  List<String> clarificationQuestions
) {}
