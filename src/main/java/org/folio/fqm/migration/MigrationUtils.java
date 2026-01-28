package org.folio.fqm.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.config.MigrationConfiguration;
import org.folio.fqm.exception.InvalidFqlException;
import org.folio.fqm.migration.types.MigratableFqlField;
import org.folio.fqm.migration.types.MigratableFqlFieldAndCondition;
import org.folio.fqm.migration.types.MigratableFqlFieldOnly;
import org.folio.fqm.migration.types.MigrationResult;
import org.folio.fqm.migration.types.SingleFieldMigrationResult;
import org.folio.fqm.migration.types.ValueTransformer;
import org.folio.fqm.migration.warnings.Warning;

@Log4j2
@UtilityClass
public class MigrationUtils {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Helper function to transform an FQL query where each field gets turned into a new quantity of fields.
   * This runs a given function on each field's condition in the query, potentially adding or removing $and as needed.
   *
   * @param entityTypeId The entity type ID of the query being migrated
   * @param fqlQuery The root query to migrate
   * @param handler  something that takes an {@link MigratableFqlFieldAndCondition} and returns a list of
   *                 {@link SingleFieldMigrationResult} indicating the new field(s), warnings, and whether
   *                 a breaking change occurred
   */
  public static MigrationResult<String> migrateFql(
    UUID entityTypeId,
    String fqlQuery,
    Function<MigratableFqlFieldAndCondition, SingleFieldMigrationResult<MigratableFqlFieldAndCondition>> handler,
    Map<UUID, Map<String, UUID>> sourceMappings
  ) {
    try {
      ObjectNode fql = (ObjectNode) objectMapper.readTree(fqlQuery);

      ObjectNode result = objectMapper.createObjectNode();

      // iterate through fields in source
      List<MigratableFqlFieldAndCondition> startingFields = new ArrayList<>();
      extractFieldsAndConditions(
        entityTypeId,
        fql,
        startingFields,
        v -> result.set(MigrationConfiguration.VERSION_KEY, v)
      );

      List<SingleFieldMigrationResult<MigratableFqlFieldAndCondition>> transformed = startingFields
        .stream()
        .map(f ->
          handleSingleFieldWithNesting(f, handler, MigrationUtils::didMigrationModifyFieldAndCondition, sourceMappings)
        )
        .toList();
      List<MigratableFqlFieldAndCondition> resultingFields = transformed
        .stream()
        .map(SingleFieldMigrationResult::result)
        .flatMap(Collection::stream)
        .toList();

      if (resultingFields.isEmpty()) {
        log.warn("Migrating {} yielded zero fields", fqlQuery);
      } else if (resultingFields.size() == 1) {
        result.set(resultingFields.get(0).getFullField(), resultingFields.get(0).getConditionObject());
      } else {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        resultingFields.forEach(field -> arrayNode.add(field.getFieldAndConditionObject()));
        result.set("$and", arrayNode);
      }

      return new MigrationResult<>(
        objectMapper.writeValueAsString(result),
        transformed.stream().map(SingleFieldMigrationResult::warnings).flatMap(Collection::stream).toList(),
        transformed.stream().anyMatch(SingleFieldMigrationResult::hadBreakingChange)
      );
    } catch (JsonProcessingException e) {
      log.error("Unable to process JSON", e);
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Similar to {@link #migrateFql(UUID, String, Function)} but for field name lists.
   *
   * @param entityTypeId the entity type ID of the query being migrated
   * @param fields the list of fields to migrate
   * @param handler something that takes an {@link MigratableFqlFieldOnly} and returns a list of
   *                {@link SingleFieldMigrationResult} indicating the new field(s), warnings, and
   *                whether a breaking change occurred
   */
  public static MigrationResult<List<String>> migrateFieldNames(
    UUID entityTypeId,
    List<String> fields,
    Function<MigratableFqlFieldOnly, SingleFieldMigrationResult<MigratableFqlFieldOnly>> handler,
    Map<UUID, Map<String, UUID>> sourceMappings
  ) {
    List<SingleFieldMigrationResult<MigratableFqlFieldOnly>> transformed = fields
      .stream()
      .map(f ->
        handleSingleFieldWithNesting(
          new MigratableFqlFieldOnly(entityTypeId, "", f),
          handler,
          MigrationUtils::didMigrationModifyFieldOnly,
          sourceMappings
        )
      )
      .toList();

    return new MigrationResult<>(
      transformed
        .stream()
        .map(SingleFieldMigrationResult::result)
        .flatMap(Collection::stream)
        .map(MigratableFqlFieldOnly::getFullField)
        .distinct()
        .toList(),
      transformed.stream().map(SingleFieldMigrationResult::warnings).flatMap(Collection::stream).toList(),
      transformed.stream().anyMatch(SingleFieldMigrationResult::hadBreakingChange)
    );
  }

  private static <F extends MigratableFqlField<F>> SingleFieldMigrationResult<F> handleSingleFieldWithNesting(
    F original,
    Function<F, SingleFieldMigrationResult<F>> handler,
    BiPredicate<F, SingleFieldMigrationResult<F>> didModify,
    Map<UUID, Map<String, UUID>> sourceMappings
  ) {
    SingleFieldMigrationResult<F> transformed = handler.apply(original);
    int fieldDelimiterIndex = original.field().indexOf('.');

    if (didModify.test(original, transformed) || fieldDelimiterIndex == -1) {
      return transformed;
    }

    Map<String, UUID> sourceMap = sourceMappings.get(original.entityTypeId());
    String source = original.field().substring(0, fieldDelimiterIndex);
    String remainder = original.field().substring(fieldDelimiterIndex + 1);
    if (sourceMap == null || sourceMap.get(source) == null) {
      return transformed;
    }

    return handleSingleFieldWithNesting(
      original.dereferenced(sourceMap.get(source), source, remainder),
      handler,
      didModify,
      sourceMappings
    );
  }

  public static boolean didMigrationModifyFieldAndCondition(
    MigratableFqlFieldAndCondition original,
    SingleFieldMigrationResult<MigratableFqlFieldAndCondition> result
  ) {
    return (
      result.hadBreakingChange() ||
      !result.warnings().isEmpty() ||
      result.result().size() != 1 ||
      !result.result().iterator().next().equals(original)
    );
  }

  public static boolean didMigrationModifyFieldOnly(
    MigratableFqlFieldOnly original,
    SingleFieldMigrationResult<MigratableFqlFieldOnly> result
  ) {
    return (
      result.hadBreakingChange() ||
      !result.warnings().isEmpty() ||
      result.result().size() != 1 ||
      !result.result().iterator().next().getFullField().equals(original.getFullField())
    );
  }

  /**
   * Extracts every field and condition from the given FQL node, adding them to the provided result list.
   * This will fully unpack $ands and any other nesting as necessary.
   */
  private static void extractFieldsAndConditions(
    UUID entityTypeId,
    ObjectNode fql,
    List<MigratableFqlFieldAndCondition> result,
    Consumer<JsonNode> handleVersion
  ) {
    fql
      .properties()
      .forEach(entry -> {
        if ("$and".equals(entry.getKey())) {
          // recurse onto nested conditions
          ((ArrayNode) entry.getValue()).elements()
            .forEachRemaining(node -> {
              extractFieldsAndConditions(
                entityTypeId,
                (ObjectNode) node,
                result,
                // _version only applies to outer
                v -> {
                  throw log.throwing(new InvalidFqlException("_version found nested inside query", Map.of()));
                }
              );
            });
        } else if (!MigrationConfiguration.VERSION_KEY.equals(entry.getKey())) {
          // add each condition from this field
          entry
            .getValue()
            .properties()
            .forEach(condition ->
              result.add(
                new MigratableFqlFieldAndCondition(
                  entityTypeId,
                  "",
                  entry.getKey(),
                  condition.getKey(),
                  condition.getValue()
                )
              )
            );
        } else {
          handleVersion.accept(entry.getValue());
        }
      });
  }

  /**
   * Helper function to transform values in an FQL query. The returned function is intended to be
   * called inside {@link #migrateFql(UUID, String, Function, Map)}'s {@code handler} parameter
   * (or wrapped by `migrateFieldAndCondition` in {@link AbstractRegularMigrationStrategy#migrateFieldAndCondition}).
   *
   * @param input            The field/condition being evaluated
   * @param applies          If the valueTransformer should be applied to the field (for optimization)
   * @param valueTransformer Something that takes an incoming field, value, and a supplier (to get
   *                         the original fql for warnings), returning either the transformed value,
   *                         or `null` if it should be removed (the transformer is responsible for
   *                         directly adding warnings in the caller's context, if applicable)
   */
  public static SingleFieldMigrationResult<MigratableFqlFieldAndCondition> migrateFqlValues(
    MigratableFqlFieldAndCondition input,
    Predicate<MigratableFqlFieldAndCondition> applies,
    ValueTransformer valueTransformer
  ) {
    if (!applies.test(input)) {
      return SingleFieldMigrationResult.noop(input);
    }

    JsonNode result = null;
    List<Warning> warnings = new ArrayList<>();
    AtomicBoolean hadBreakingChange = new AtomicBoolean(false);

    if (input.value().isArray()) {
      ArrayNode node = (ArrayNode) input.value();

      List<JsonNode> transformedValues = new ArrayList<>();
      node.forEach(element -> {
        MigrationResult<JsonNode> transformedValue = transformIfStringOnly(
          element,
          textValue -> valueTransformer.apply(input, textValue, () -> input.getConditionObject().toPrettyString())
        );

        if (transformedValue.result() != null) {
          transformedValues.add(transformedValue.result());
        }
        warnings.addAll(transformedValue.warnings());
        hadBreakingChange.set(hadBreakingChange.get() || transformedValue.hadBreakingChange());
      });

      if (!transformedValues.isEmpty()) {
        result = objectMapper.createArrayNode().addAll(transformedValues);
      }
    } else {
      MigrationResult<JsonNode> transformedValue = transformIfStringOnly(
        input.value(),
        textValue -> valueTransformer.apply(input, textValue, () -> input.getConditionObject().toPrettyString())
      );
      warnings.addAll(transformedValue.warnings());
      hadBreakingChange.set(hadBreakingChange.get() || transformedValue.hadBreakingChange());

      if (transformedValue.result() != null) {
        result = transformedValue.result();
      }
    }

    if (result == null) {
      return SingleFieldMigrationResult
        .<MigratableFqlFieldAndCondition>removed()
        .withWarnings(warnings)
        .withHadBreakingChange(hadBreakingChange.get());
    } else {
      return SingleFieldMigrationResult
        .withField(input.withValue(result))
        .withWarnings(warnings)
        .withHadBreakingChange(hadBreakingChange.get());
    }
  }

  /**
   * Calls provided function with the String contents of the node, if it is safe to do so. Returns
   * node version of the supplier's response, if applicable; if the original node is non-string,
   * simply returns that.
   */
  private static MigrationResult<JsonNode> transformIfStringOnly(
    JsonNode value,
    Function<String, MigrationResult<String>> transformer
  ) {
    if (!value.isTextual()) {
      return MigrationResult.noop(value);
    }

    MigrationResult<String> newValue = transformer.apply(value.textValue());

    if (newValue.result() == null) {
      return newValue.withoutResult();
    }

    return newValue.withNewResult(new TextNode(newValue.result()));
  }

  // Formatting note: This object is down here instead of at the top of the class because it is only used in
  // compareVersions() and is a static field only because Patterns are expensive to construct.
  private static final Pattern NUMBER_PATTERN = Pattern.compile("^\\d+$");

  /**
   * Compare two version strings, following the standard compareTo() semantics.
   *
   * <p/>A version can be separated into pieces by dots ("."), which are handled independently.
   * If the same part in both versions is an integer, they are compared numerically.
   * If at least one of them is not an integer, they are compared lexicographically as strings.
   *
   * <p/>Null versions or version pieces are treated as "0"
   * This means "1" and "1.0" are considered equal versions.
   *
   * <p/>Examples:
   * <ul>
   *   <li>1 < 2</li>
   *   <li>1.1 > 1</li>
   *   <li>1.0.0 == 1</li>
   *   <li>1.000 = 1</li>
   *   <li>2.0 > 1.9.9.9</li>
   *   <li>alpha < beta</li>
   *   <li>1.something > 1.0</li>
   * </ul>
   */
  public static int compareVersions(String version1, String version2) {
    // Basic algorithm:
    // 1. Base case: Versions are equal, so return immediately
    // 2. Otherwise, look at the first version piece and compare them
    // 3. If they are different, return immediately
    // 4. Otherwise, recurse with the remaining portion of the versions
    version1 = Objects.requireNonNullElse(version1, "0");
    version2 = Objects.requireNonNullElse(version2, "0");
    if (version1.equals(version2)) {
      return 0;
    }
    String[] version1Parts = version1.split("\\.", 2);
    String[] version2Parts = version2.split("\\.", 2);
    int result = NUMBER_PATTERN.matcher(version1Parts[0]).matches() &&
      NUMBER_PATTERN.matcher(version2Parts[0]).matches() // Are both of these ints?
      ? Integer.compare(Integer.parseInt(version1Parts[0]), Integer.parseInt(version2Parts[0])) // Yep - compare as ints
      : version1Parts[0].compareTo(version2Parts[0]); // Nope - compare as strings
    if (result != 0) {
      return result;
    }
    // If we got to here, then the current version parts are equal, so move on to the next set of parts
    return compareVersions(
      version1Parts.length == 1 ? null : version1Parts[1],
      version2Parts.length == 1 ? null : version2Parts[1]
    );
  }
}
