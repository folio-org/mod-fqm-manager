package org.folio.fqm.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.folio.fql.model.AndCondition;
import org.folio.fql.model.FieldCondition;
import org.folio.fql.model.FqlCondition;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.Field;
import org.folio.querytool.domain.dto.MarcDataType;

@UtilityClass
public class MarcFieldFactory {

  public static final String GENERIC_MARC_COLUMN_NAME = "marc";
  public static final String BLANK_INDICATOR_TOKEN = "blank";

  private static final String MARC_INDEXERS_TABLE = "${tenant_id}_mod_source_record_storage.marc_indexers";
  private static final String MARC_VALUE_FUNCTION = "lower(:value)";
  private static final String BLANK_INDICATOR_STORAGE_VALUE = "#";
  private static final int CASE_INSENSITIVE = Pattern.CASE_INSENSITIVE;
  private static final Pattern MARC_TABLE_PATTERN = Pattern.compile("FROM\\s+(?<table>\\S+)\\s+marc", Pattern.CASE_INSENSITIVE);
  private static final Pattern TAG_ONLY_PATTERN = Pattern.compile("^marc_(?<tag>\\d{3})$", CASE_INSENSITIVE);
  private static final Pattern INDICATOR_PATTERN = Pattern.compile("^marc_(?<tag>\\d{3})_(?<indicator>ind[12])$", CASE_INSENSITIVE);
  private static final Pattern CONSTRAINED_SUBFIELD_PATTERN = Pattern.compile(
    "^marc_(?<tag>\\d{3})_(?<indicator>ind[12])_(?<indicatorValue>blank|[a-z0-9])_(?<subfield>[a-z0-9])$",
    CASE_INSENSITIVE
  );
  private static final Pattern SUBFIELD_PATTERN = Pattern.compile("^marc_(?<tag>\\d{3})_(?<subfield>[a-z0-9])$", CASE_INSENSITIVE);

  public static boolean isMarcFieldName(String fieldName) {
    return parse(fieldName).isPresent();
  }

  public static EntityType addSyntheticColumns(EntityType entityType, Collection<String> fieldNames, String tenantId) {
    if (fieldNames == null || fieldNames.isEmpty() || entityType.getColumns() == null) {
      return entityType;
    }

    List<EntityTypeColumn> updatedColumns = new ArrayList<>(entityType.getColumns());
    Set<String> existingFieldNames = updatedColumns.stream()
      .map(Field::getName)
      .collect(LinkedHashSet::new, Set::add, Set::addAll);

    for (String fieldName : fieldNames) {
      if (fieldName == null || existingFieldNames.contains(fieldName)) {
        continue;
      }

      createSyntheticColumn(entityType, fieldName, tenantId).ifPresent(column -> {
        updatedColumns.add(column);
        existingFieldNames.add(fieldName);
      });
    }

    return entityType.toBuilder().columns(updatedColumns).build();
  }

  public static EntityType addSyntheticColumns(EntityType entityType, Collection<String> fieldNames) {
    return addSyntheticColumns(entityType, fieldNames, null);
  }

  public static EntityType addSyntheticColumns(EntityType entityType, FqlCondition<?> condition, String tenantId) {
    return addSyntheticColumns(entityType, getReferencedFieldNames(condition), tenantId);
  }

  public static EntityType addSyntheticColumns(EntityType entityType, FqlCondition<?> condition) {
    return addSyntheticColumns(entityType, condition, null);
  }

  public static Set<String> getReferencedFieldNames(FqlCondition<?> condition) {
    if (condition instanceof FieldCondition<?> fieldCondition) {
      return Set.of(fieldCondition.field().getColumnName());
    }
    if (condition instanceof AndCondition andCondition) {
      return andCondition.value().stream()
        .map(MarcFieldFactory::getReferencedFieldNames)
        .collect(LinkedHashSet::new, Set::addAll, Set::addAll);
    }
    return Set.of();
  }

  public static Optional<EntityTypeColumn> createSyntheticColumn(EntityType entityType, String fieldName) {
    return createSyntheticColumn(entityType, fieldName, null);
  }

  public static Optional<EntityTypeColumn> createSyntheticColumn(EntityType entityType, String fieldName, String tenantId) {
    Optional<MarcFieldName> parsedField = parse(fieldName);
    Optional<EntityTypeColumn> placeholder = findMarcPlaceholder(entityType);

    if (parsedField.isEmpty() || placeholder.isEmpty()) {
      return Optional.empty();
    }

    EntityTypeColumn marcPlaceholder = placeholder.get();
    if (marcPlaceholder.getValueGetter() == null || marcPlaceholder.getValueGetter().isBlank()) {
      throw new InvalidEntityTypeDefinitionException(
        "Generic MARC column must define valueGetter so MARC indexers can be correlated",
        entityType
      );
    }

    MarcFieldName marcField = parsedField.get();
    return Optional.of(new EntityTypeColumn()
      .name(marcField.fieldName())
      .labelAlias(marcField.labelAlias())
      .dataType(new MarcDataType().dataType("marcDataType"))
      .queryable(true)
      .visibleByDefault(false)
      .essential(false)
      .valueGetter(buildValueGetter(marcField, marcPlaceholder.getValueGetter(), tenantId))
      .filterValueGetter(buildFilterValueGetter(marcField, marcPlaceholder.getValueGetter(), tenantId))
      .valueFunction(MARC_VALUE_FUNCTION));
  }

  public static Optional<MarcQueryContext> createQueryContext(EntityType entityType, String fieldName) {
    Optional<MarcFieldName> parsedField = parse(fieldName);
    Optional<EntityTypeColumn> placeholder = findMarcPlaceholder(entityType);
    Optional<EntityTypeColumn> syntheticField = findField(entityType, fieldName);

    if (parsedField.isEmpty() || placeholder.isEmpty() || syntheticField.isEmpty()) {
      return Optional.empty();
    }

    String marcIdGetter = placeholder.get().getValueGetter();
    String valueGetter = syntheticField.get().getValueGetter();
    String filterValueGetter = syntheticField.get().getFilterValueGetter();
    if (marcIdGetter == null || marcIdGetter.isBlank() || valueGetter == null || valueGetter.isBlank()
      || filterValueGetter == null || filterValueGetter.isBlank()) {
      return Optional.empty();
    }

    return extractMarcTableName(valueGetter)
      .map(tableName -> new MarcQueryContext(parsedField.get(), tableName, marcIdGetter, filterValueGetter));
  }

  public static Optional<MarcFieldName> parse(String fieldName) {
    Matcher tagOnlyMatcher = TAG_ONLY_PATTERN.matcher(fieldName);
    if (tagOnlyMatcher.matches()) {
      return Optional.of(new MarcFieldName(fieldName, tagOnlyMatcher.group("tag"), null, null, null));
    }

    Matcher indicatorMatcher = INDICATOR_PATTERN.matcher(fieldName);
    if (indicatorMatcher.matches()) {
      if (isControlFieldTag(indicatorMatcher.group("tag"))) {
        return Optional.empty();
      }
      return Optional.of(new MarcFieldName(
        fieldName,
        indicatorMatcher.group("tag"),
        normalizeLower(indicatorMatcher.group("indicator")),
        null,
        null
      ));
    }

    Matcher constrainedSubfieldMatcher = CONSTRAINED_SUBFIELD_PATTERN.matcher(fieldName);
    if (constrainedSubfieldMatcher.matches()) {
      if (isControlFieldTag(constrainedSubfieldMatcher.group("tag"))) {
        return Optional.empty();
      }
      return Optional.of(new MarcFieldName(
        fieldName,
        constrainedSubfieldMatcher.group("tag"),
        normalizeLower(constrainedSubfieldMatcher.group("indicator")),
        normalizeIndicatorValue(constrainedSubfieldMatcher.group("indicatorValue")),
        normalizeLower(constrainedSubfieldMatcher.group("subfield"))
      ));
    }

    Matcher subfieldMatcher = SUBFIELD_PATTERN.matcher(fieldName);
    if (subfieldMatcher.matches()) {
      if (isControlFieldTag(subfieldMatcher.group("tag"))) {
        return Optional.empty();
      }
      return Optional.of(new MarcFieldName(
        fieldName,
        subfieldMatcher.group("tag"),
        null,
        null,
        normalizeLower(subfieldMatcher.group("subfield"))
      ));
    }

    return Optional.empty();
  }

  public static Optional<EntityTypeColumn> findMarcPlaceholder(EntityType entityType) {
    List<EntityTypeColumn> columns = entityType.getColumns();
    if (columns == null) {
      return Optional.empty();
    }

    return columns.stream()
      .filter(column -> GENERIC_MARC_COLUMN_NAME.equals(column.getName()))
      .filter(column -> column.getDataType() instanceof MarcDataType)
      .findFirst();
  }

  public static Optional<EntityTypeColumn> findField(EntityType entityType, String fieldName) {
    List<EntityTypeColumn> columns = entityType.getColumns();
    if (columns == null) {
      return Optional.empty();
    }

    return columns.stream()
      .filter(column -> fieldName.equals(column.getName()))
      .findFirst();
  }

  private static String buildValueGetter(MarcFieldName marcField, String marcIdGetter, String tenantId) {
    String selectedValue = marcField.isIndicatorQuery() ? "DISTINCT marc.%s".formatted(marcField.indicator()) : "marc.value";
    String notNullCheck = marcField.isIndicatorQuery() ? "marc.%s IS NOT NULL".formatted(marcField.indicator()) : "marc.value IS NOT NULL";

    return """
      (
        SELECT jsonb_agg(%s) FILTER (WHERE %s)
        FROM %s marc
        WHERE marc.marc_id = %s
          AND marc.field_no = '%s'%s%s
      )
      """.formatted(
      selectedValue,
      notNullCheck,
      interpolateTenant(MARC_INDEXERS_TABLE, tenantId),
      marcIdGetter,
      marcField.tag(),
      buildIndicatorConstraintCondition(marcField),
      buildSubfieldCondition(marcField)
    ).trim();
  }

  private static String buildFilterValueGetter(MarcFieldName marcField, String marcIdGetter, String tenantId) {
    return marcField.isIndicatorQuery()
      ? "lower(marc.%s)".formatted(marcField.indicator())
      : "lower(marc.value)";
  }

  private static String interpolateTenant(String input, String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      return input;
    }
    return input.replace("${tenant_id}", tenantId);
  }

  private static String normalizeLower(String value) {
    return value == null ? null : value.toLowerCase(Locale.ROOT);
  }

  public static String normalizeIndicatorQueryValue(String value) {
    if (value == null) {
      return null;
    }

    String normalizedValue = normalizeLower(value);
    return BLANK_INDICATOR_TOKEN.equals(normalizedValue)
      ? BLANK_INDICATOR_STORAGE_VALUE
      : normalizedValue;
  }

  private static boolean isControlFieldTag(String tag) {
    return tag != null && tag.startsWith("00");
  }

  private static Optional<String> extractMarcTableName(String valueGetter) {
    Matcher matcher = MARC_TABLE_PATTERN.matcher(valueGetter);
    if (!matcher.find()) {
      return Optional.empty();
    }
    return Optional.of(matcher.group("table"));
  }

  private static String buildSubfieldCondition(MarcFieldName marcField) {
    if (!marcField.isSubfield()) {
      return "";
    }

    return "\n          AND marc.subfield_no = '%s'".formatted(marcField.subfield());
  }

  private static String buildIndicatorConstraintCondition(MarcFieldName marcField) {
    if (!marcField.hasIndicatorConstraint()) {
      return "";
    }

    return "\n          AND lower(marc.%s) = '%s'".formatted(
      marcField.indicator(),
      marcField.indicatorValue()
    );
  }

  private static String normalizeIndicatorValue(String value) {
    return normalizeIndicatorQueryValue(value);
  }

  private static String displayIndicatorValue(String indicatorValue) {
    return BLANK_INDICATOR_STORAGE_VALUE.equals(indicatorValue)
      ? BLANK_INDICATOR_TOKEN
      : indicatorValue;
  }

  public record MarcFieldName(String fieldName, String tag, String indicator, String indicatorValue, String subfield) {

    public boolean isIndicatorQuery() {
      return indicator != null && indicatorValue == null && subfield == null;
    }

    public boolean hasIndicatorConstraint() {
      return indicator != null && indicatorValue != null && subfield != null;
    }

    public boolean isSubfield() {
      return subfield != null;
    }

    public String labelAlias() {
      if (isIndicatorQuery()) {
        return "%s %s".formatted(tag, indicator);
      }
      if (isSubfield()) {
        if (hasIndicatorConstraint()) {
          return "%s %s=%s $%s".formatted(tag, indicator, displayIndicatorValue(indicatorValue), subfield);
        }
        return "%s$%s".formatted(tag, subfield);
      }
      return tag;
    }
  }

  public record MarcQueryContext(MarcFieldName marcField, String tableName, String marcIdGetter, String filterValueGetter) {

    public String whereClause() {
      return "marc.marc_id = %s and marc.field_no = '%s'%s%s".formatted(
        marcIdGetter,
        marcField.tag(),
        marcField.hasIndicatorConstraint()
          ? " and lower(marc.%s) = '%s'".formatted(marcField.indicator(), marcField.indicatorValue())
          : "",
        marcField.isSubfield() ? " and marc.subfield_no = '%s'".formatted(marcField.subfield()) : ""
      );
    }
  }
}
