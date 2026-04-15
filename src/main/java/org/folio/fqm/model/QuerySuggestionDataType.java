package org.folio.fqm.model;

import java.util.List;

public record QuerySuggestionDataType(
  String type,
  QuerySuggestionDataType itemType,
  List<QuerySuggestionField> properties
) {}
