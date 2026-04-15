package org.folio.fqm.model;

import java.util.List;
import java.util.UUID;

public record QuerySuggestionEntityTypeContext(
  UUID id,
  String name,
  String label,
  List<QuerySuggestionField> fields
) {}
