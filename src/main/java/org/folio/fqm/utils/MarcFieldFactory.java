package org.folio.fqm.utils;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.MarcDataType;

@UtilityClass
public class MarcFieldFactory {

  public static final String GENERIC_MARC_COLUMN_NAME = "marc";

  private static final String MARC_INDEXERS_TABLE = "${tenant_id}_mod_source_record_storage.marc_indexers";
  private static final String MARC_VALUE_FUNCTION = "lower(:value)";
  private static final Pattern TAG_ONLY_PATTERN = Pattern.compile("^marc_(?<tag>\\d{3})$");
  private static final Pattern INDICATOR_PATTERN = Pattern.compile("^marc_(?<tag>\\d{3})_(?<indicator>ind[12])$");
  private static final Pattern SUBFIELD_PATTERN = Pattern.compile("^marc_(?<tag>\\d{3})_(?<subfield>[a-z0-9])$");

  public static boolean isMarcFieldName(String fieldName) {
    return parse(fieldName).isPresent();
  }

  public static Optional<EntityTypeColumn> createSyntheticColumn(EntityType entityType, String fieldName) {
    Optional<MarcFieldName> parsedField = parse(fieldName);
    Optional<EntityTypeColumn> placeholder = findMarcPlaceholder(entityType);

    if (parsedField.isEmpty() || placeholder.isEmpty()) {
      return Optional.empty();
    }

    EntityTypeColumn marcPlaceholder = placeholder.get();
    if (marcPlaceholder.getIdColumnName() == null || marcPlaceholder.getIdColumnName().isBlank()) {
      throw new InvalidEntityTypeDefinitionException(
        "Generic MARC column must define idColumnName so MARC indexers can be correlated",
        entityType
      );
    }

    EntityTypeColumn marcIdColumn = EntityTypeUtils.findColumnByName(entityType, marcPlaceholder.getIdColumnName());
    if (marcIdColumn.getValueGetter() == null || marcIdColumn.getValueGetter().isBlank()) {
      throw new InvalidEntityTypeDefinitionException(
        "MARC id column %s must define a valueGetter".formatted(marcPlaceholder.getIdColumnName()),
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
      .idColumnName(marcPlaceholder.getIdColumnName())
      .valueGetter(buildValueGetter(marcField, marcIdColumn.getValueGetter()))
      .filterValueGetter(buildFilterValueGetter(marcField, marcIdColumn.getValueGetter()))
      .valueFunction(MARC_VALUE_FUNCTION));
  }

  public static Optional<MarcFieldName> parse(String fieldName) {
    Matcher tagOnlyMatcher = TAG_ONLY_PATTERN.matcher(fieldName);
    if (tagOnlyMatcher.matches()) {
      return Optional.of(new MarcFieldName(fieldName, tagOnlyMatcher.group("tag"), null, null));
    }

    Matcher indicatorMatcher = INDICATOR_PATTERN.matcher(fieldName);
    if (indicatorMatcher.matches()) {
      return Optional.of(new MarcFieldName(
        fieldName,
        indicatorMatcher.group("tag"),
        indicatorMatcher.group("indicator"),
        null
      ));
    }

    Matcher subfieldMatcher = SUBFIELD_PATTERN.matcher(fieldName);
    if (subfieldMatcher.matches()) {
      return Optional.of(new MarcFieldName(
        fieldName,
        subfieldMatcher.group("tag"),
        null,
        subfieldMatcher.group("subfield")
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

  private static String buildValueGetter(MarcFieldName marcField, String marcIdGetter) {
    String selectedValue = marcField.isIndicator() ? "DISTINCT marc.%s".formatted(marcField.indicator()) : "marc.value";
    String notNullCheck = marcField.isIndicator() ? "marc.%s IS NOT NULL".formatted(marcField.indicator()) : "marc.value IS NOT NULL";

    return """
      (
        SELECT jsonb_agg(%s) FILTER (WHERE %s)
        FROM %s marc
        WHERE marc.marc_id = %s
          AND marc.field_no = '%s'%s
      )
      """.formatted(
      selectedValue,
      notNullCheck,
      MARC_INDEXERS_TABLE,
      marcIdGetter,
      marcField.tag(),
      buildSubfieldCondition(marcField)
    ).trim();
  }

  private static String buildFilterValueGetter(MarcFieldName marcField, String marcIdGetter) {
    String selectedValue = marcField.isIndicator()
      ? "DISTINCT marc.%s".formatted(marcField.indicator())
      : "marc.value";
    String notNullCheck = marcField.isIndicator()
      ? "marc.%s IS NOT NULL".formatted(marcField.indicator())
      : "marc.value IS NOT NULL";

    return """
      (
        SELECT lower(string_agg(%s, ' ') FILTER (WHERE %s))
        FROM %s marc
        WHERE marc.marc_id = %s
          AND marc.field_no = '%s'%s
      )
      """.formatted(
      selectedValue,
      notNullCheck,
      MARC_INDEXERS_TABLE,
      marcIdGetter,
      marcField.tag(),
      buildSubfieldCondition(marcField)
    ).trim();
  }

  private static String buildSubfieldCondition(MarcFieldName marcField) {
    if (!marcField.isSubfield()) {
      return "";
    }

    return "\n          AND marc.subfield_no = '%s'".formatted(marcField.subfield());
  }

  public record MarcFieldName(String fieldName, String tag, String indicator, String subfield) {

    public boolean isIndicator() {
      return indicator != null;
    }

    public boolean isSubfield() {
      return subfield != null;
    }

    public String labelAlias() {
      if (isIndicator()) {
        return "%s %s".formatted(tag, indicator);
      }
      if (isSubfield()) {
        return "%s$%s".formatted(tag, subfield);
      }
      return tag;
    }
  }
}
