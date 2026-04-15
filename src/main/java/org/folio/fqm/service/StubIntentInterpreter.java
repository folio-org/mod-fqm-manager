package org.folio.fqm.service;

import org.folio.fqm.model.QuerySuggestionEntityTypeContext;
import org.folio.fqm.model.QuerySuggestionField;
import org.folio.fqm.model.QuerySuggestionIntent;
import org.folio.fqm.model.QuerySuggestionIntentFilter;
import org.folio.fqm.model.QuerySuggestionMetadataContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StubIntentInterpreter implements IntentInterpreter {

  private static final Pattern OLDER_THAN_DAYS_PATTERN = Pattern.compile("older than\\s+(\\d+)\\s+days?");

  @Override
  public QuerySuggestionIntent interpret(String naturalLanguageQuery, UUID preselectedEntityTypeId, QuerySuggestionMetadataContext metadataContext) {
    String normalizedQuery = naturalLanguageQuery.toLowerCase(Locale.ROOT);
    QuerySuggestionEntityTypeContext entityType = resolveEntityType(normalizedQuery, preselectedEntityTypeId, metadataContext);

    List<QuerySuggestionIntentFilter> filters = new ArrayList<>();
    List<String> assumptions = new ArrayList<>();
    List<String> clarificationQuestions = new ArrayList<>();

    if (entityType != null && preselectedEntityTypeId == null) {
      assumptions.add("Entity type was inferred as %s.".formatted(entityType.label()));
    }

    resolveStatusFilter(normalizedQuery, entityType).ifPresent(filters::add);

    Matcher olderThanDaysMatcher = OLDER_THAN_DAYS_PATTERN.matcher(normalizedQuery);
    if (olderThanDaysMatcher.find()) {
      String resolvedDateField = resolveField(entityType, "date")
        .map(QuerySuggestionField::name)
        .orElse("<date-field>");
      filters.add(new QuerySuggestionIntentFilter(
        resolvedDateField,
        "Date",
        "$olderThanDays",
        olderThanDaysMatcher.group(1)
      ));
      if ("<date-field>".equals(resolvedDateField)) {
        assumptions.add("A date field was not resolved from metadata for the age filter.");
      }
    }

    if (normalizedQuery.contains("without invoice") || normalizedQuery.contains("without invoices")) {
      QuerySuggestionField invoiceField = resolveField(entityType, "invoice").orElse(null);
      String fieldName = invoiceField == null ? "<invoice-field>" : invoiceField.name();
      String fieldLabel = invoiceField == null ? "Invoice" : defaultString(invoiceField.label(), invoiceField.name());
      filters.add(new QuerySuggestionIntentFilter(fieldName, fieldLabel, "$isNull", "true"));
      if (invoiceField == null) {
        assumptions.add("An invoice-related field was not resolved from metadata.");
      }
    }

    if (entityType == null) {
      clarificationQuestions.add("Which entity type should this report start from?");
    }

    if (filters.isEmpty()) {
      clarificationQuestions.add("Which parts of the request should become filters?");
    }

    return new QuerySuggestionIntent(
      entityType == null ? null : entityType.id(),
      entityType == null ? null : entityType.label(),
      filters,
      assumptions,
      clarificationQuestions
    );
  }

  private Optional<QuerySuggestionIntentFilter> resolveStatusFilter(String normalizedQuery, QuerySuggestionEntityTypeContext entityType) {
    if (!normalizedQuery.contains("open")) {
      return Optional.empty();
    }

    QuerySuggestionField statusField = resolveField(entityType, "status").orElse(null);
    String fieldName = statusField == null ? "<status-field>" : statusField.name();
    String fieldLabel = statusField == null ? "Status" : defaultString(statusField.label(), statusField.name());
    return Optional.of(new QuerySuggestionIntentFilter(fieldName, fieldLabel, "$eq", "Open"));
  }

  private QuerySuggestionEntityTypeContext resolveEntityType(
    String normalizedQuery,
    UUID preselectedEntityTypeId,
    QuerySuggestionMetadataContext metadataContext
  ) {
    if (metadataContext.entityTypes().isEmpty()) {
      return null;
    }

    if (preselectedEntityTypeId != null) {
      return metadataContext.entityTypes().stream()
        .filter(entityType -> entityType.id().equals(preselectedEntityTypeId))
        .findFirst()
        .orElse(null);
    }

    if (normalizedQuery.contains("order")) {
      QuerySuggestionEntityTypeContext match = findEntityTypeByKeyword(metadataContext, "order");
      if (match != null) {
        return match;
      }
    }

    if (normalizedQuery.contains("invoice")) {
      QuerySuggestionEntityTypeContext match = findEntityTypeByKeyword(metadataContext, "invoice");
      if (match != null) {
        return match;
      }
    }

    return metadataContext.entityTypes().get(0);
  }

  private QuerySuggestionEntityTypeContext findEntityTypeByKeyword(QuerySuggestionMetadataContext metadataContext, String keyword) {
    return metadataContext.entityTypes().stream()
      .filter(entityType -> containsIgnoreCase(entityType.name(), keyword) || containsIgnoreCase(entityType.label(), keyword))
      .findFirst()
      .orElse(null);
  }

  private Optional<QuerySuggestionField> resolveField(QuerySuggestionEntityTypeContext entityType, String keyword) {
    if (entityType == null) {
      return Optional.empty();
    }

    return entityType.fields().stream()
      .filter(field -> containsIgnoreCase(field.name(), keyword) || containsIgnoreCase(field.label(), keyword))
      .findFirst();
  }

  private boolean containsIgnoreCase(String value, String keyword) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
  }

  private String defaultString(String preferred, String fallback) {
    return preferred == null || preferred.isBlank() ? fallback : preferred;
  }
}
