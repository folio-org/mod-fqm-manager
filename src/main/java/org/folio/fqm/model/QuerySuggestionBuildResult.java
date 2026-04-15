package org.folio.fqm.model;

import java.util.List;
import java.util.UUID;

public record QuerySuggestionBuildResult(
  UUID entityTypeId,
  String fqlQuery,
  List<String> assumptions,
  boolean validated
) {}
