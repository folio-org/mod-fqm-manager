package org.folio.fqm.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
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
import org.folio.querytool.domain.dto.MarcType;

@UtilityClass
public class MarcFieldFactory {

  public static final String GENERIC_MARC_COLUMN_NAME = "marc";

  private static final String MARC_INDEXERS_VIEW = "${tenant_id}_mod_fqm_manager.src_srs_marc_indexers";
  private static final String MARC_VALUE_FUNCTION = "lower(:value)";
  private static final String MARC_FILTER_VALUE_GETTER = "lower(marc.value)";
  private static final Pattern MARC_TABLE_PATTERN = Pattern.compile("FROM\\s+(?<table>\\S+)\\s+marc", Pattern.CASE_INSENSITIVE);
  // Field names are accepted case-insensitively (e.g. MARC_245_A behaves the same as marc_245_a). The tag/
  // subfield are normalized to their canonical storage form when the MarcFieldName is built.
  private static final Pattern SUBFIELD_PATTERN = Pattern.compile("^marc_(?<tag>\\d{3})_(?<subfield>[a-z0-9])$", Pattern.CASE_INSENSITIVE);
  // Tag-only form (e.g. marc_245). Matches any subfield of the tag: the generated predicate filters on
  // field_no without a subfield_no constraint, so it is satisfied when ANY subfield of the tag matches.
  private static final Pattern TAG_PATTERN = Pattern.compile("^marc_(?<tag>\\d{3})$", Pattern.CASE_INSENSITIVE);
  // Generic scanner for "fieldName": keys in a raw FQL query. It intentionally does NOT encode the MARC
  // grammar; every candidate key is validated through parse()/isMarcFieldName so the grammar lives in
  // exactly one place and the two cannot drift.
  private static final Pattern QUERY_FIELD_KEY_PATTERN = Pattern.compile("\"(?<field>\\w+)\"\\s*:");

  public static boolean isMarcFieldName(String fieldName) {
    return parse(fieldName).isPresent();
  }

  public static Set<String> getReferencedMarcFieldNames(String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) {
      return Set.of();
    }

    Set<String> fieldNames = new LinkedHashSet<>();
    Matcher matcher = QUERY_FIELD_KEY_PATTERN.matcher(rawQuery);
    while (matcher.find()) {
      String candidate = matcher.group("field");
      if (isMarcFieldName(candidate)) {
        fieldNames.add(candidate);
      }
    }
    return fieldNames;
  }

  public static EntityType addSyntheticColumns(EntityType entityType, String rawQuery, String tenantId) {
    return addSyntheticColumns(entityType, getReferencedMarcFieldNames(rawQuery), tenantId);
  }

  public static EntityType addSyntheticColumns(EntityType entityType, FqlCondition<?> condition, String tenantId) {
    return addSyntheticColumns(entityType, getReferencedFieldNames(condition), tenantId);
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

    // A concrete tenant is required: the synthesized SQL references a tenant-qualified view, and leaving
    // ${tenant_id} un-interpolated would emit broken SQL. Fail fast rather than defer to query execution.
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException(
        "A tenant id is required to synthesize MARC column '" + fieldName + "'"
      );
    }

    MarcFieldName marcField = parsedField.get();
    return Optional.of(new EntityTypeColumn()
      .name(marcField.fieldName())
      .labelAlias(marcField.labelAlias())
      .dataType(new MarcType().dataType("marcType"))
      .queryable(true)
      .visibleByDefault(false)
      .essential(false)
      .valueGetter(buildValueGetter(marcField, marcPlaceholder.getValueGetter(), tenantId))
      .filterValueGetter(MARC_FILTER_VALUE_GETTER)
      .valueFunction(MARC_VALUE_FUNCTION));
  }

  public static Optional<MarcQueryContext> createQueryContext(EntityType entityType, String fieldName) {
    Optional<MarcFieldName> parsedField = parse(fieldName);
    Optional<EntityTypeColumn> placeholder = findMarcPlaceholder(entityType);
    Optional<EntityTypeColumn> syntheticField = EntityTypeUtils.findColumn(entityType, fieldName);

    if (parsedField.isEmpty() || placeholder.isEmpty() || syntheticField.isEmpty()) {
      return Optional.empty();
    }

    String marcIdGetter = placeholder.get().getValueGetter();
    String valueGetter = syntheticField.get().getValueGetter();
    if (marcIdGetter == null || marcIdGetter.isBlank() || valueGetter == null || valueGetter.isBlank()) {
      return Optional.empty();
    }

    return extractMarcTableName(valueGetter)
      .map(tableName -> new MarcQueryContext(parsedField.get(), tableName, marcIdGetter, MARC_FILTER_VALUE_GETTER));
  }

  public static Optional<MarcFieldName> parse(String fieldName) {
    Matcher subfieldMatcher = SUBFIELD_PATTERN.matcher(fieldName);
    if (subfieldMatcher.matches()) {
      // Preserve the original field name so the synthesized column matches the name referenced in the query,
      // but normalize the subfield code to lower case to match how it is stored in marc_indexers.
      return Optional.of(new MarcFieldName(
        fieldName,
        subfieldMatcher.group("tag"),
        subfieldMatcher.group("subfield").toLowerCase()
      ));
    }

    Matcher tagMatcher = TAG_PATTERN.matcher(fieldName);
    if (tagMatcher.matches()) {
      // Tag-only: no subfield target, so the predicate matches any subfield of the tag.
      return Optional.of(new MarcFieldName(fieldName, tagMatcher.group("tag"), null));
    }

    return Optional.empty();
  }

  public static Optional<EntityTypeColumn> findMarcPlaceholder(EntityType entityType) {
    return entityType.getColumns().stream()
      .filter(MarcFieldFactory::isGenericMarcPlaceholder)
      .findFirst();
  }

  /**
   * The generic, hidden {@code marc} capability column that declares an entity type supports dynamic MARC
   * field references. It is a correlation placeholder, not a user-facing field, and should be excluded from
   * field listings.
   */
  public static boolean isGenericMarcPlaceholder(EntityTypeColumn column) {
    return column != null
      && GENERIC_MARC_COLUMN_NAME.equals(column.getName())
      && column.getDataType() instanceof MarcType;
  }

  private static String buildValueGetter(MarcFieldName marcField, String marcIdGetter, String tenantId) {
    return """
      (
        SELECT jsonb_agg(marc.value) FILTER (WHERE marc.value IS NOT NULL)
        FROM %s marc
        WHERE marc.marc_id = %s
          AND marc.field_no = '%s'%s
      )
    """.formatted(
      interpolateTenant(MARC_INDEXERS_VIEW, tenantId),
      marcIdGetter,
      marcField.tag(),
      marcField.subfieldClause()
    ).trim();
  }

  private static String interpolateTenant(String input, String tenantId) {
    // tenantId is guaranteed non-blank by createSyntheticColumn's guard before this is reached.
    return input.replace("${tenant_id}", tenantId);
  }

  private static Optional<String> extractMarcTableName(String valueGetter) {
    Matcher matcher = MARC_TABLE_PATTERN.matcher(valueGetter);
    if (!matcher.find()) {
      return Optional.empty();
    }
    return Optional.of(matcher.group("table"));
  }

  public record MarcFieldName(String fieldName, String tag, String subfield) {

    public String labelAlias() {
      return subfield == null ? tag : "%s$%s".formatted(tag, subfield);
    }

    /** Display value-getter fragment (uppercase SQL); empty for tag-only fields, which match any subfield. */
    public String subfieldClause() {
      return subfield == null ? "" : "\n          AND marc.subfield_no = '%s'".formatted(subfield);
    }
  }

  public record MarcQueryContext(MarcFieldName marcField, String tableName, String marcIdGetter, String filterValueGetter) {

    public String whereClause() {
      String clause = "marc.marc_id = %s and marc.field_no = '%s'".formatted(marcIdGetter, marcField.tag());
      if (marcField.subfield() != null) {
        clause += " and marc.subfield_no = '%s'".formatted(marcField.subfield());
      }
      return clause;
    }

    /**
     * Row-level existence predicate comparing the MARC value against a single bound parameter ({@code {0}}).
     * Used for eq/ne/in/nin and (with a LIKE operator) for starts_with/contains.
     *
     * @param operator   the SQL comparison or pattern operator (e.g. {@code =}, {@code like})
     * @param existsMatch {@code true} for {@code EXISTS}, {@code false} for {@code NOT EXISTS}
     */
    public String existsClause(String operator, boolean existsMatch) {
      return "%s (select 1 from %s marc where %s and %s %s {0})".formatted(
        existsMatch ? "exists" : "not exists",
        tableName,
        whereClause(),
        filterValueGetter,
        operator
      );
    }

    /**
     * Presence predicate for {@code $empty}: a matching MARC row exists with a non-empty value.
     */
    public String presenceClause() {
      return "exists (select 1 from %s marc where %s and %s is not null and %s <> '')".formatted(
        tableName,
        whereClause(),
        filterValueGetter,
        filterValueGetter
      );
    }
  }
}
