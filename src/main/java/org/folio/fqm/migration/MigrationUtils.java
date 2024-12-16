package org.folio.fqm.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.function.TriConsumer;
import org.folio.fqm.config.MigrationConfiguration;

@Log4j2
@UtilityClass
public class MigrationUtils {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Helper function to transform an FQL query. This changes a version to a new one, and runs a given
   * function on each field in the query. See {@link #migrateFqlTree(ObjectNode, TriConsumer)} for more
   * details on the field transformation function.
   *
   * @param fqlQuery The root query to migrate
   * @param versionTransformer A function that takes the current (potentially null) version and
   *                           returns the new one to be persisted in the query
   * @param handler something that takes the result node, the field name, and the field's query object,
   *                applies some transformation, and stores the results back in result
   */
  public static String migrateFql(
    String fqlQuery,
    UnaryOperator<String> versionTransformer,
    TriConsumer<ObjectNode, String, JsonNode> handler
  ) {
    try {
      ObjectNode fql = (ObjectNode) objectMapper.readTree(fqlQuery);

      fql.set(
        MigrationConfiguration.VERSION_KEY,
        objectMapper.valueToTree(
          versionTransformer.apply(
            Optional.ofNullable(fql.get(MigrationConfiguration.VERSION_KEY)).map(JsonNode::asText).orElse(null)
          )
        )
      );

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
   *
   * A true "no-op" here would look like (result, key, value) -> result.set(key, value).
   *
   * This conveniently handles `$and`s, allowing logic to be handled on fields only.
   *
   * @param fql the fql node
   * @param handler something that takes the result node, the field name, and the field's query object,
   *                applies some transformation, and stores the results back in result
   * @return
   */
  private static ObjectNode migrateFqlTree(ObjectNode fql, TriConsumer<ObjectNode, String, JsonNode> handler) {
    ObjectNode result = new ObjectMapper().createObjectNode();
    // iterate through fields in source
    fql
      .fields()
      .forEachRemaining(entry -> {
        if ("$and".equals(entry.getKey())) {
          ArrayNode resultContents = new ObjectMapper().createArrayNode();
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
   *
   * This is similar to {@link #migrateFql(String, UnaryOperator, TriConsumer)}, but operates on
   * each value, and provides additional convenience to handle cases of array values, etc.
   *
   * @param fqlQuery The root query to migrate
   * @param versionTransformer A function that takes the current (potentially null) version and
   *                           returns the new one to be persisted in the query
   * @param applies If the valueTransformer should be applied to the field (for optimization)
   * @param valueTransformer Something that takes an incoming field name, value, and a supplier
   *                         (to get the original fql for warnings), returning either the
   *                         transformed value, or `null` if it should be removed (the transformer
   *                         is responsible for handling warnings)
   */
  public static String migrateFqlValues(
    String fqlQuery,
    UnaryOperator<String> versionTransformer,
    Predicate<String> applies,
    ValueTransformer valueTransformer
  ) {
    return migrateFql(
      fqlQuery,
      versionTransformer,
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
}
