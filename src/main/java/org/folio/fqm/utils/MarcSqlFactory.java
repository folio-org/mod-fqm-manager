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
import org.apache.commons.lang3.StringUtils;
import org.folio.fql.model.FqlCondition;
import org.folio.fql.model.field.MarcFieldName;
import org.folio.fql.service.MarcFieldFactory;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.Field;

/**
 * Builds the SQL-bearing synthetic columns and query contexts for dynamic MARC fields.
 *
 * <p>MARC field-name parsing, recognition, placeholder detection, and metadata-only column generation are owned
 * by the shared lib ({@link MarcFieldFactory}), so the grammar lives in exactly one place. This class layers the
 * mod-fqm-manager-specific SQL onto the lib's parsed results: the {@code valueGetter} that correlates against the
 * marc_indexers view, the filter/value functions, and the row-level predicates used for querying.
 */
@UtilityClass
public class MarcSqlFactory {

  private static final String MARC_INDEXERS_VIEW = "${tenant_id}_mod_fqm_manager.src_srs_marc_indexers";
  private static final String MARC_VALUE_FUNCTION = "lower(:value)";
  private static final Pattern MARC_TABLE_PATTERN =
    Pattern.compile("FROM\\s+(?<table>\\S+)\\s+marc", Pattern.CASE_INSENSITIVE);

  public static EntityType addSyntheticColumns(EntityType entityType, String rawQuery, String tenantId) {
    return addSyntheticColumns(entityType, MarcFieldFactory.getReferencedMarcFieldNames(rawQuery), tenantId);
  }

  public static EntityType addSyntheticColumns(EntityType entityType, FqlCondition<?> condition, String tenantId) {
    return addSyntheticColumns(entityType, MarcFieldFactory.getReferencedMarcFieldNames(condition), tenantId);
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

  public static Optional<EntityTypeColumn> createSyntheticColumn(EntityType entityType, String fieldName, String tenantId) {
    Optional<MarcFieldName> parsedField = MarcFieldFactory.parse(fieldName);
    Optional<EntityTypeColumn> placeholder =
      parsedField.flatMap(field -> MarcFieldFactory.findMarcPlaceholder(entityType, field));

    if (parsedField.isEmpty() || placeholder.isEmpty()) {
      return Optional.empty();
    }

    EntityTypeColumn marcPlaceholder = placeholder.get();
    if (StringUtils.isBlank(marcPlaceholder.getValueGetter())) {
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
    // The lib supplies the metadata-only column (name, label, marcType); mod-fqm-manager layers on the SQL.
    return Optional.of(MarcFieldFactory.toColumn(marcField)
      .valueGetter(buildValueGetter(marcField, marcPlaceholder.getValueGetter(), tenantId))
      .filterValueGetter(filterValueGetter(marcField))
      .valueFunction(MARC_VALUE_FUNCTION));
  }

  public static Optional<MarcQueryContext> createQueryContext(EntityType entityType, String fieldName) {
    Optional<MarcFieldName> parsedField = MarcFieldFactory.parse(fieldName);
    Optional<EntityTypeColumn> placeholder =
      parsedField.flatMap(field -> MarcFieldFactory.findMarcPlaceholder(entityType, field));
    Optional<EntityTypeColumn> syntheticField = EntityTypeUtils.findColumn(entityType, fieldName);

    if (parsedField.isEmpty() || placeholder.isEmpty() || syntheticField.isEmpty()) {
      return Optional.empty();
    }

    String marcIdGetter = placeholder.get().getValueGetter();
    String valueGetter = syntheticField.get().getValueGetter();
    if (StringUtils.isBlank(marcIdGetter) || StringUtils.isBlank(valueGetter)) {
      return Optional.empty();
    }

    return extractMarcTableName(valueGetter)
      .map(tableName -> new MarcQueryContext(parsedField.get(), tableName, marcIdGetter));
  }

  // ---- SQL generation -------------------------------------------------------------------------------------

  private static String buildValueGetter(MarcFieldName marcField, String marcIdGetter, String tenantId) {
    String targetColumn = targetColumn(marcField);
    // Indicators are denormalized onto every subfield row, so aggregating them as-is repeats the same value
    // once per subfield (e.g. a 245 with $a$b yields ["1","1"]). DISTINCT collapses that artifactual
    // duplication to the distinct indicator value(s). Subfield/tag values are aggregated as-is, since their
    // repetition is meaningful.
    String distinct = marcField.isIndicatorTarget() ? "DISTINCT " : "";
    return """
      (
        SELECT jsonb_agg(%smarc.%s) FILTER (WHERE marc.%s IS NOT NULL)
        FROM %s marc
        WHERE marc.marc_id = %s
          AND marc.field_no = '%s'%s%s
      )
    """.formatted(
      distinct,
      targetColumn,
      targetColumn,
      interpolateTenant(MARC_INDEXERS_VIEW, tenantId),
      marcIdGetter,
      marcField.tag(),
      indicatorConstraintClause(marcField),
      subfieldClause(marcField)
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

  // The marc_indexers column this field targets: ind1/ind2 when an indicator is the target, otherwise the value.
  private static String targetColumn(MarcFieldName marcField) {
    return marcField.isIndicatorTarget() ? "ind" + marcField.targetIndicator() : "value";
  }

  // WHERE fragment narrowing to a specific subfield; empty for tag-only and indicator-only fields.
  private static String subfieldClause(MarcFieldName marcField) {
    return marcField.subfield() == null ? "" : " AND marc.subfield_no = '%s'".formatted(marcField.subfield());
  }

  // WHERE fragment(s) pinning ind1 and/or ind2 to fixed values (constrained forms); empty when neither indicator
  // is a constraint. Matched case-insensitively, consistent with indicator matching.
  private static String indicatorConstraintClause(MarcFieldName marcField) {
    StringBuilder clause = new StringBuilder();
    if (marcField.ind1Value() != null) {
      clause.append(" AND lower(marc.ind1) = '%s'".formatted(marcField.ind1Value()));
    }
    if (marcField.ind2Value() != null) {
      clause.append(" AND lower(marc.ind2) = '%s'".formatted(marcField.ind2Value()));
    }
    return clause.toString();
  }

  // SQL expression the search value is compared against (the value column, or an indicator column).
  private static String filterValueGetter(MarcFieldName marcField) {
    return "lower(marc.%s)".formatted(targetColumn(marcField));
  }

  public record MarcQueryContext(MarcFieldName marcField, String tableName, String marcIdGetter) {

    /** SQL expression the search value is compared against (the value column, or an indicator column). */
    public String filterValueGetter() {
      return MarcSqlFactory.filterValueGetter(marcField);
    }

    public String whereClause() {
      String formatted = "marc.marc_id = %s and marc.field_no = '%s'".formatted(marcIdGetter, marcField.tag());
      StringBuilder clause = new StringBuilder(formatted);
      if (marcField.ind1Value() != null) {
        clause.append(" and lower(marc.ind1) = '%s'".formatted(marcField.ind1Value()));
      }
      if (marcField.ind2Value() != null) {
        clause.append(" and lower(marc.ind2) = '%s'".formatted(marcField.ind2Value()));
      }
      if (marcField.subfield() != null) {
        clause.append(" and marc.subfield_no = '%s'".formatted(marcField.subfield()));
      }
      return clause.toString();
    }

    /**
     * Row-level existence predicate comparing the targeted MARC column against a single bound parameter
     * ({@code {0}}). Used for eq/ne/in/nin and (with a LIKE operator) for starts_with/contains.
     *
     * @param operator    the SQL comparison or pattern operator (e.g. {@code =}, {@code like})
     * @param existsMatch {@code true} for {@code EXISTS}, {@code false} for {@code NOT EXISTS}
     */
    public String existsClause(String operator, boolean existsMatch) {
      return "%s (select 1 from %s marc where %s and %s %s {0})".formatted(
        existsMatch ? "exists" : "not exists",
        tableName,
        whereClause(),
        filterValueGetter(),
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
        filterValueGetter(),
        filterValueGetter()
      );
    }
  }
}
