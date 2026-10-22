package org.folio.fqm.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.folio.fql.model.FqlCondition;
import org.folio.fql.model.field.MarcFieldName;
import org.folio.fql.service.MarcFieldFactory;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.fqm.exception.InvalidFqlException;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.Field;

/**
 * Builds the SQL-bearing synthetic columns and query contexts for dynamic MARC fields.
 *
 * <p>MARC field-name parsing, recognition, placeholder detection, and metadata-only column generation are owned
 * by the shared lib ({@link MarcFieldFactory}), so the grammar lives in exactly one place. This class layers the
 * mod-fqm-manager-specific SQL onto the lib's parsed results: the {@code valueGetter} that correlates against the
 * MARC indexer views, the filter/value functions, and the row-level predicates used for querying.
 *
 * <p>Leader byte positions ({@code marc_ldr_p06}, {@code marc_ldr_p00_04}) take a separate path: the leader is not
 * in marc_indexers, but in marc_indexers_leader, which holds one row per record with the leader pre-split into
 * position columns. They correlate against the same {@code marc} placeholder. Control-field positions
 * ({@code marc_008_p06}) are parsed by the lib but rejected here until they have SQL support.
 */
@UtilityClass
public class MarcSqlFactory {

  private static final String MARC_INDEXERS_VIEW = "${tenant_id}_mod_fqm_manager.src_srs_marc_indexers";
  private static final String MARC_INDEXERS_LEADER_VIEW = "${tenant_id}_mod_fqm_manager.src_srs_marc_indexers_leader";
  // The leader columns stored by marc_indexers_leader, in order, with the inclusive position range each holds.
  // Position 23 is not stored.
  private static final List<LeaderColumn> LEADER_COLUMNS = List.of(
    new LeaderColumn("p_00_04", 0, 4),
    new LeaderColumn("p_05", 5, 5),
    new LeaderColumn("p_06", 6, 6),
    new LeaderColumn("p_07", 7, 7),
    new LeaderColumn("p_08", 8, 8),
    new LeaderColumn("p_09", 9, 9),
    new LeaderColumn("p_10", 10, 10),
    new LeaderColumn("p_11", 11, 11),
    new LeaderColumn("p_12_16", 12, 16),
    new LeaderColumn("p_17", 17, 17),
    new LeaderColumn("p_18", 18, 18),
    new LeaderColumn("p_19", 19, 19),
    new LeaderColumn("p_20", 20, 20),
    new LeaderColumn("p_21", 21, 21),
    new LeaderColumn("p_22", 22, 22)
  );
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

    MarcFieldName marcField = parsedField.get();
    // The lib parses control-field positions (marc_008_p06), but only leader positions have SQL support so far.
    if (marcField.isPositionTarget() && !marcField.isLeader()) {
      throw new InvalidFqlException(fieldName, Map.of(fieldName,
        "MARC byte positions are currently supported only for the leader (e.g. marc_ldr_p06)"));
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
    if (marcField.isLeader()) {
      return buildLeaderValueGetter(marcField, marcIdGetter, tenantId);
    }
    String targetColumn = targetColumn(marcField);
    // Indicators are denormalized onto every subfield row, so aggregating them as-is repeats the same value
    // once per subfield (e.g. a 245 with $a$b yields ["1","1"]). DISTINCT collapses that artifactual
    // duplication to the distinct indicator value(s). Subfield/tag values are aggregated as-is, since their
    // repetition is meaningful.
    String distinct = marcField.isIndicatorTarget() ? "DISTINCT " : "";
    return """
      (
        SELECT COALESCE(jsonb_agg(%smarc.%s) FILTER (WHERE marc.%s IS NOT NULL), '[]'::jsonb)
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

  private static String buildLeaderValueGetter(MarcFieldName marcField, String marcIdGetter, String tenantId) {
    String position = leaderPositionExpression(marcField);
    return """
      (
        SELECT COALESCE(jsonb_agg(%s) FILTER (WHERE %s <> ''), '[]'::jsonb)
        FROM %s marc
        WHERE marc.marc_id = %s
      )
    """.formatted(
      position,
      position,
      interpolateTenant(MARC_INDEXERS_LEADER_VIEW, tenantId),
      marcIdGetter
    ).trim();
  }

  /**
   * SQL expression for a leader position or range. Resolves to the stored column when the range aligns with one
   * exactly (e.g. {@code marc.p_06}, {@code marc.p_00_04}); otherwise substrings the concatenation of all stored
   * columns, so ranges crossing column boundaries (or within a grouped column) work too. Positions past 22 fall
   * outside the concatenation and yield {@code ''}.
   *
   */
  private static String leaderPositionExpression(MarcFieldName marcField) {
    int start = marcField.position();
    int end = marcField.endPosition();
    return LEADER_COLUMNS.stream()
      .filter(column -> column.start() == start && column.end() == end)
      .findFirst()
      .map(column -> "marc." + column.name())
      .orElseGet(() -> "substring(%s from %d for %d)".formatted(
        LEADER_COLUMNS.stream().map(column -> "marc." + column.name()).collect(Collectors.joining(" || ")),
        start + 1,
        end - start + 1
      ));
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

  // SQL expression the search value is compared against (the value column, an indicator column, or a leader
  // position).
  private static String filterValueGetter(MarcFieldName marcField) {
    if (marcField.isLeader()) {
      return leaderPositionExpression(marcField);
    }
    return "lower(marc.%s)".formatted(targetColumn(marcField));
  }

  private record LeaderColumn(String name, int start, int end) {}

  public record MarcQueryContext(MarcFieldName marcField, String tableName, String marcIdGetter) {

    /** SQL expression the search value is compared against (value column, indicator column, or leader position). */
    public String filterValueGetter() {
      return MarcSqlFactory.filterValueGetter(marcField);
    }

    public String whereClause() {
      // The leader view has one row per record and no field_no/indicator/subfield columns.
      if (marcField.isLeader()) {
        return "marc.marc_id = %s".formatted(marcIdGetter);
      }
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
