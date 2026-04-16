package org.folio.fqm.service;

import org.folio.fqm.model.QuerySuggestionEntityTypeContext;
import org.folio.fqm.model.QuerySuggestionField;
import org.folio.fqm.model.QuerySuggestionFieldValue;
import org.folio.fqm.model.QuerySuggestionIntent;
import org.folio.fqm.model.QuerySuggestionIntentFilter;
import org.folio.fqm.model.QuerySuggestionMetadataContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StubIntentInterpreter implements IntentInterpreter {

  private static final Pattern OLDER_THAN_DAYS_PATTERN = Pattern.compile("older than\\s+(\\d+)\\s+days?");
  private static final Pattern WITHOUT_PATTERN = Pattern.compile("(without|no|missing)\\s+([a-zA-Z][a-zA-Z\\s_-]+)");
  private static final Set<String> STOP_WORDS = Set.of(
    "show", "me", "all", "the", "a", "an", "with", "without", "no", "missing", "older", "than", "days", "day"
  );

  @Override
  public QuerySuggestionIntent interpret(String naturalLanguageQuery, UUID preselectedEntityTypeId, QuerySuggestionMetadataContext metadataContext) {
    String normalizedQuery = naturalLanguageQuery.toLowerCase(Locale.ROOT);
    Set<String> queryTokens = tokenize(normalizedQuery);
    QuerySuggestionEntityTypeContext entityType = resolveEntityType(normalizedQuery, preselectedEntityTypeId, metadataContext);

    List<QuerySuggestionIntentFilter> filters = new ArrayList<>();
    List<String> assumptions = new ArrayList<>();
    List<String> clarificationQuestions = new ArrayList<>();

    if (entityType != null && preselectedEntityTypeId == null) {
      assumptions.add("Entity type was inferred as %s.".formatted(entityType.label()));
    }

    resolveValueFilter(normalizedQuery, queryTokens, entityType, "status").ifPresent(filters::add);

    Matcher olderThanDaysMatcher = OLDER_THAN_DAYS_PATTERN.matcher(normalizedQuery);
    if (olderThanDaysMatcher.find()) {
      String resolvedDateField = resolveDateField(entityType)
        .map(QuerySuggestionField::name)
        .orElse("<date-field>");
      filters.add(new QuerySuggestionIntentFilter(
        resolvedDateField,
        resolveDateField(entityType).map(this::displayLabel).orElse("Date"),
        "$olderThanDays",
        olderThanDaysMatcher.group(1)
      ));
      if ("<date-field>".equals(resolvedDateField)) {
        assumptions.add("A date field was not resolved from metadata for the age filter.");
      }
    }

    Matcher withoutMatcher = WITHOUT_PATTERN.matcher(normalizedQuery);
    while (withoutMatcher.find()) {
      String concept = withoutMatcher.group(2).trim();
      Optional<QuerySuggestionField> matchedField = resolveField(entityType, concept);
      if (matchedField.isPresent()) {
        QuerySuggestionField field = matchedField.get();
        filters.add(new QuerySuggestionIntentFilter(field.name(), displayLabel(field), "$isNull", "true"));
      } else if (concept.contains("invoice")) {
        filters.add(new QuerySuggestionIntentFilter("<invoice-field>", "Invoice", "$isNull", "true"));
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

  private Optional<QuerySuggestionIntentFilter> resolveValueFilter(
    String normalizedQuery,
    Set<String> queryTokens,
    QuerySuggestionEntityTypeContext entityType,
    String concept
  ) {
    Optional<QuerySuggestionField> fieldCandidate = resolveField(entityType, concept);
    if (fieldCandidate.isEmpty()) {
      return Optional.empty();
    }

    QuerySuggestionField field = fieldCandidate.get();
    Optional<QuerySuggestionFieldValue> matchedValue = matchEnumeratedValue(field, normalizedQuery, queryTokens);
    if (matchedValue.isPresent()) {
      QuerySuggestionFieldValue value = matchedValue.get();
      return Optional.of(new QuerySuggestionIntentFilter(
        field.name(),
        displayLabel(field),
        "$eq",
        defaultString(value.value(), value.label())
      ));
    }

    if ("status".equals(concept) && normalizedQuery.contains("open")) {
      return Optional.of(new QuerySuggestionIntentFilter(field.name(), displayLabel(field), "$eq", "Open"));
    }

    return Optional.empty();
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

    return metadataContext.entityTypes().stream()
      .max(Comparator.comparingInt(entityType -> scoreEntityType(entityType, normalizedQuery)))
      .orElse(metadataContext.entityTypes().get(0));
  }

  private int scoreEntityType(QuerySuggestionEntityTypeContext entityType, String normalizedQuery) {
    int score = 0;
    score += scoreText(entityType.name(), normalizedQuery, 6);
    score += scoreText(entityType.label(), normalizedQuery, 6);
    for (QuerySuggestionField field : entityType.fields()) {
      score += scoreField(field, normalizedQuery);
    }
    return score;
  }

  private int scoreField(QuerySuggestionField field, String normalizedQuery) {
    int score = 0;
    score += scoreText(field.name(), normalizedQuery, 3);
    score += scoreText(field.label(), normalizedQuery, 4);
    score += scoreText(field.fullyQualifiedLabel(), normalizedQuery, 2);
    score += scoreText(field.sourceAlias(), normalizedQuery, 1);
    if (field.values() != null) {
      for (QuerySuggestionFieldValue value : field.values()) {
        score += scoreText(value.label(), normalizedQuery, 2);
        score += scoreText(value.value(), normalizedQuery, 2);
      }
    }
    return score;
  }

  private int scoreText(String candidate, String normalizedQuery, int weight) {
    if (candidate == null || candidate.isBlank()) {
      return 0;
    }
    int score = 0;
    for (String token : tokenize(candidate)) {
      if (!STOP_WORDS.contains(token) && normalizedQuery.contains(token)) {
        score += weight;
      }
    }
    return score;
  }

  private Optional<QuerySuggestionField> resolveField(QuerySuggestionEntityTypeContext entityType, String concept) {
    if (entityType == null) {
      return Optional.empty();
    }

    return entityType.fields().stream()
      .filter(field -> field.queryable() || !field.queryOnly())
      .max(Comparator.comparingInt(field -> scoreFieldForConcept(field, concept)));
  }

  private Optional<QuerySuggestionField> resolveDateField(QuerySuggestionEntityTypeContext entityType) {
    if (entityType == null) {
      return Optional.empty();
    }

    return entityType.fields().stream()
      .filter(field -> isDateLike(field) || scoreFieldForConcept(field, "date") > 0)
      .max(Comparator.comparingInt(field -> (isDateLike(field) ? 10 : 0) + scoreFieldForConcept(field, "date")));
  }

  private int scoreFieldForConcept(QuerySuggestionField field, String concept) {
    int score = 0;
    score += scoreConceptMatch(field.name(), concept, 4);
    score += scoreConceptMatch(field.label(), concept, 5);
    score += scoreConceptMatch(field.fullyQualifiedLabel(), concept, 3);
    score += scoreConceptMatch(field.sourceAlias(), concept, 2);
    return score;
  }

  private int scoreConceptMatch(String text, String concept, int weight) {
    if (text == null || text.isBlank()) {
      return 0;
    }
    String normalizedText = text.toLowerCase(Locale.ROOT);
    int score = 0;
    for (String token : tokenize(concept)) {
      if (normalizedText.contains(token)) {
        score += weight;
      }
    }
    return score;
  }

  private Optional<QuerySuggestionFieldValue> matchEnumeratedValue(
    QuerySuggestionField field,
    String normalizedQuery,
    Set<String> queryTokens
  ) {
    if (field.values() == null || field.values().isEmpty()) {
      return Optional.empty();
    }

    return field.values().stream()
      .max(Comparator.comparingInt(value -> scoreValue(value, normalizedQuery, queryTokens)))
      .filter(value -> scoreValue(value, normalizedQuery, queryTokens) > 0);
  }

  private int scoreValue(QuerySuggestionFieldValue value, String normalizedQuery, Set<String> queryTokens) {
    int score = 0;
    score += scoreConceptMatch(value.label(), normalizedQuery, 2);
    score += scoreConceptMatch(value.value(), normalizedQuery, 2);
    for (String token : tokenize(defaultString(value.label(), value.value()))) {
      if (queryTokens.contains(token)) {
        score += 3;
      }
    }
    return score;
  }

  private boolean isDateLike(QuerySuggestionField field) {
    if (field.dataType() == null || field.dataType().type() == null) {
      return false;
    }
    String type = field.dataType().type().toLowerCase(Locale.ROOT);
    return type.contains("date");
  }

  private Set<String> tokenize(String value) {
    Set<String> tokens = new HashSet<>();
    if (value == null || value.isBlank()) {
      return tokens;
    }

    String normalized = value
      .replaceAll("([a-z])([A-Z])", "$1 $2")
      .toLowerCase(Locale.ROOT)
      .replaceAll("[^a-z0-9]+", " ");

    for (String token : normalized.trim().split("\\s+")) {
      if (!token.isBlank() && !STOP_WORDS.contains(token)) {
        tokens.add(singularize(token));
      }
    }
    return tokens;
  }

  private String singularize(String token) {
    if (token.endsWith("ies") && token.length() > 3) {
      return token.substring(0, token.length() - 3) + "y";
    }
    if (token.endsWith("s") && token.length() > 3) {
      return token.substring(0, token.length() - 1);
    }
    return token;
  }

  private boolean containsIgnoreCase(String value, String keyword) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
  }

  private String displayLabel(QuerySuggestionField field) {
    return defaultString(field.label(), field.name());
  }

  private String defaultString(String preferred, String fallback) {
    return preferred == null || preferred.isBlank() ? fallback : preferred;
  }
}
