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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.function.TriConsumer;
import org.folio.fqm.config.MigrationConfiguration;
import org.folio.fqm.exception.InvalidFqlException;

@Log4j2
@UtilityClass
public class MigrationUtils {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Helper function to transform an FQL query where each field gets turned into a new quantity of fields.
   * This runs a given function on each field's condition in the query, potentially adding or removing $and as needed.
   *
   * @param fqlQuery The root query to migrate
   * @param handler  something that takes an {@link FqlFieldAndCondition} and returns a list of
   *                 {@link FqlFieldAndCondition FqlFieldAndCondition(s)} to replace it with
   */
  public static String migrateAndReshapeFql(
    String fqlQuery,
    Function<FqlFieldAndCondition, Collection<FqlFieldAndCondition>> handler
  ) {
    try {
      ObjectNode fql = (ObjectNode) objectMapper.readTree(fqlQuery);

      ObjectNode result = objectMapper.createObjectNode();

      // iterate through fields in source
      List<FqlFieldAndCondition> startingFields = new ArrayList<>();
      extractFieldsAndConditions(fql, startingFields, v -> result.set(MigrationConfiguration.VERSION_KEY, v));

      List<FqlFieldAndCondition> resultingFields = startingFields
        .stream()
        .map(handler)
        .flatMap(Collection::stream)
        .toList();

      if (resultingFields.isEmpty()) {
        log.warn("Migrating {} yielded zero fields", fqlQuery);
      } else if (resultingFields.size() == 1) {
        result.set(resultingFields.get(0).field(), resultingFields.get(0).getConditionObject());
      } else {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        resultingFields.forEach(field -> arrayNode.add(field.getFieldAndConditionObject()));
        result.set("$and", arrayNode);
      }

      return objectMapper.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      log.error("Unable to process JSON", e);
      throw new UncheckedIOException(e);
    }
  }

  private static void extractFieldsAndConditions(
    ObjectNode fql,
    List<FqlFieldAndCondition> result,
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
              result.add(new FqlFieldAndCondition(entry.getKey(), condition.getKey(), condition.getValue()))
            );
        } else {
          handleVersion.accept(entry.getValue());
        }
      });
  }

  public record FqlFieldAndCondition(String field, String operator, JsonNode value) {
    public JsonNode getConditionObject() {
      return objectMapper.createObjectNode().set(operator, value);
    }
    public JsonNode getFieldAndConditionObject() {
      return objectMapper.createObjectNode().set(field, objectMapper.createObjectNode().set(operator, value));
    }
  }

  /**
   * New code should probably use {@link #migrateAndReshapeFql(String, Function)} instead.
   *
   * Helper function to transform an FQL query where each field gets turned into one or zero fields.
   * This changes a version to a new one, and runs a given function on each field in the query. See
   * {@link #migrateFqlTree(ObjectNode, TriConsumer)} for more details on the field transformation function.
   *
   * @param fqlQuery The root query to migrate
   * @param handler  something that takes the result node, the field name, and the field's query object,
   *                 applies some transformation, and stores the results back in result
   */
  public static String migrateFql(String fqlQuery, TriConsumer<ObjectNode, String, JsonNode> handler) {
    try {
      ObjectNode fql = (ObjectNode) objectMapper.readTree(fqlQuery);
      fql = migrateFqlTree(fql, handler);

      return objectMapper.writeValueAsString(fql);
    } catch (JsonProcessingException e) {
      log.error("Unable to process JSON", e);
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Call `handler` for each field in the FQL query tree, returning a new tree.
   * Note that `handler` is responsible for inserting what should be left in the tree, if anything;
   * if the function is a no-op, an empty FQL tree will be returned.
   * <p>
   * A true "no-op" here would look like (result, key, value) -> result.set(key, value).
   * <p>
   * This conveniently handles `$and`s, allowing logic to be handled on fields only.
   *
   * @param fql     the fql node
   * @param handler something that takes the result node, the field name, and the field's query object,
   *                applies some transformation, and stores the results back in result
   * @return
   */
  private static ObjectNode migrateFqlTree(ObjectNode fql, TriConsumer<ObjectNode, String, JsonNode> handler) {
    ObjectNode result = objectMapper.createObjectNode();
    // iterate through fields in source
    fql
      .properties()
      .forEach(entry -> {
        if ("$and".equals(entry.getKey())) {
          ArrayNode resultContents = objectMapper.createArrayNode();
          ((ArrayNode) entry.getValue()).elements()
            .forEachRemaining(node -> {
              ObjectNode innerResult = migrateFqlTree((ObjectNode) node, handler);
              // handle removed fields
              if (!innerResult.isEmpty()) {
                resultContents.add(innerResult);
              }
            });
          result.set("$and", resultContents);
          // ensure we don't run this on the _version
        } else if (!MigrationConfiguration.VERSION_KEY.equals(entry.getKey())) {
          handler.accept(result, entry.getKey(), entry.getValue());
        } else {
          // keep _version as-is
          result.set(entry.getKey(), entry.getValue());
        }
      });

    return result;
  }

  /**
   * Helper function to transform values in an FQL query. This changes a version to a new one, and
   * runs a given function on each value in the query. Returning `null` from the transformation
   * function will result in the value being removed from the query.
   * <p>
   * This is similar to {@link #migrateFql(String, TriConsumer)}, but operates on
   * each value, and provides additional convenience to handle cases of array values, etc.
   *
   * @param fqlQuery         The root query to migrate
   * @param applies          If the valueTransformer should be applied to the field (for optimization)
   * @param valueTransformer Something that takes an incoming field name, value, and a supplier
   *                         (to get the original fql for warnings), returning either the
   *                         transformed value, or `null` if it should be removed (the transformer
   *                         is responsible for handling warnings)
   */
  public static String migrateFqlValues(String fqlQuery, Predicate<String> applies, ValueTransformer valueTransformer) {
    return migrateFql(
      fqlQuery,
      (result, key, value) -> {
        if (!applies.test(key)) {
          result.set(key, value); // no-op
          return;
        }

        ObjectNode conditions = (ObjectNode) value;
        ObjectNode newValues = objectMapper.createObjectNode();

        conditions
          .properties()
          .forEach(entry -> {
            if (entry.getValue().isArray()) {
              ArrayNode node = (ArrayNode) entry.getValue();

              List<JsonNode> transformedValues = new ArrayList<>();
              node.forEach(element -> {
                JsonNode transformedValue = transformIfStringOnly(
                  element,
                  textValue ->
                    valueTransformer.apply(
                      key,
                      textValue,
                      () -> objectMapper.createObjectNode().set(entry.getKey(), entry.getValue()).toPrettyString()
                    )
                );

                if (transformedValue != null) {
                  transformedValues.add(transformedValue);
                }
              });

              if (!transformedValues.isEmpty()) {
                newValues.set(entry.getKey(), objectMapper.createArrayNode().addAll(transformedValues));
              }
            } else {
              JsonNode transformedValue = transformIfStringOnly(
                entry.getValue(),
                textValue ->
                  valueTransformer.apply(
                    key,
                    textValue,
                    () -> objectMapper.createObjectNode().set(entry.getKey(), entry.getValue()).toPrettyString()
                  )
              );

              if (transformedValue != null) {
                newValues.set(entry.getKey(), transformedValue);
              }
            }
          });

        if (newValues.size() != 0) {
          result.set(key, newValues);
        }
      }
    );
  }

  /**
   * Calls provided function with the String contents of the node, if it is safe to do so. Returns
   * node version of the supplier's response, if applicable; if the original node is non-string,
   * simply returns that.
   */
  private static JsonNode transformIfStringOnly(JsonNode value, UnaryOperator<String> transformer) {
    if (!value.isTextual()) {
      return value;
    }

    String newValue = transformer.apply(value.textValue());

    if (newValue == null) {
      return null;
    }

    return new TextNode(newValue);
  }

  @FunctionalInterface
  public interface ValueTransformer {
    String apply(String key, String value, Supplier<String> fql);
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
