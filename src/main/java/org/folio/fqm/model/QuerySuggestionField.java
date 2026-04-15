package org.folio.fqm.model;

import java.util.List;

public record QuerySuggestionField(
  String name,
  String label,
  String fullyQualifiedLabel,
  String sourceAlias,
  boolean queryable,
  boolean queryOnly,
  boolean hidden,
  boolean idField,
  QuerySuggestionDataType dataType,
  List<QuerySuggestionFieldValue> values
) {}
