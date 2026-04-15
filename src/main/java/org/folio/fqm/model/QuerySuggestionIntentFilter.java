package org.folio.fqm.model;

public record QuerySuggestionIntentFilter(
  String fieldName,
  String fieldLabel,
  String operator,
  String value
) {}
