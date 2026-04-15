package org.folio.fqm.service;

import org.folio.fql.service.FqlValidationService;
import org.folio.fqm.exception.InvalidFqlException;
import org.folio.fqm.model.QuerySuggestionBuildResult;
import org.folio.fqm.model.QuerySuggestionIntent;
import org.folio.fqm.model.QuerySuggestionIntentFilter;
import org.folio.querytool.domain.dto.DateTimeType;
import org.folio.querytool.domain.dto.DateType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.Field;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class QuerySuggestionFqlBuilder {

  private final EntityTypeService entityTypeService;
  private final FqlValidationService fqlValidationService;
  private final ClockService clockService;

  public QuerySuggestionFqlBuilder(
    EntityTypeService entityTypeService,
    FqlValidationService fqlValidationService,
    ClockService clockService
  ) {
    this.entityTypeService = entityTypeService;
    this.fqlValidationService = fqlValidationService;
    this.clockService = clockService;
  }

  public QuerySuggestionBuildResult build(QuerySuggestionIntent intent, String naturalLanguageQuery) {
    if (intent.entityTypeId() == null || intent.filters().isEmpty()) {
      return new QuerySuggestionBuildResult(
        intent.entityTypeId(),
        "{\"_note\":{\"$eq\":\"Placeholder generated from natural-language request: %s\"}}"
          .formatted(naturalLanguageQuery.replace("\"", "\\\"")),
        List.of(),
        false
      );
    }

    EntityType entityType = entityTypeService.getEntityTypeDefinition(intent.entityTypeId(), true);
    List<String> buildAssumptions = new ArrayList<>();
    String fqlQuery = "{%s}".formatted(intent.filters().stream()
      .map(filter -> normalizeFilter(filter, entityType, buildAssumptions))
      .collect(Collectors.joining(",")));

    Map<String, String> errorMap = fqlValidationService.validateFql(entityType, fqlQuery);
    if (!errorMap.isEmpty()) {
      throw new InvalidFqlException(fqlQuery, errorMap);
    }

    return new QuerySuggestionBuildResult(intent.entityTypeId(), fqlQuery, buildAssumptions, true);
  }

  private String normalizeFilter(QuerySuggestionIntentFilter filter, EntityType entityType, List<String> buildAssumptions) {
    return switch (filter.operator()) {
      case "$olderThanDays" -> buildOlderThanDaysFilter(filter, entityType, buildAssumptions);
      case "$isNull" -> "\"%s\":{\"$empty\":true}".formatted(filter.fieldName());
      default -> "\"%s\":{\"%s\":%s}".formatted(filter.fieldName(), filter.operator(), formatValue(filter.value()));
    };
  }

  private String buildOlderThanDaysFilter(QuerySuggestionIntentFilter filter, EntityType entityType, List<String> buildAssumptions) {
    Field resolvedField = entityType.getColumns().stream()
      .filter(field -> filter.fieldName().equals(field.getName()))
      .findFirst()
      .orElse(null);

    int days = Integer.parseInt(filter.value());
    var instant = clockService.now().toInstant().minusSeconds(days * 24L * 60L * 60L);

    String formattedValue;
    if (resolvedField != null && resolvedField.getDataType() instanceof DateType) {
      formattedValue = DateTimeFormatter.ISO_LOCAL_DATE.format(instant.atZone(ZoneOffset.UTC));
    } else {
      formattedValue = DateTimeFormatter.ISO_INSTANT.format(instant);
      buildAssumptions.add("Relative date filter was translated using UTC.");
    }

    return "\"%s\":{\"$lt\":\"%s\"}".formatted(filter.fieldName(), formattedValue);
  }

  private String formatValue(String value) {
    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
      return value.toLowerCase(java.util.Locale.ROOT);
    }
    if (value.chars().allMatch(Character::isDigit)) {
      return value;
    }
    return "\"%s\"".formatted(value.replace("\"", "\\\""));
  }
}
