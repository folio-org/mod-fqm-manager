package org.folio.fqm.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.UncheckedIOException;
import java.util.Optional;
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
   * @throws JsonMappingException
   * @throws JsonProcessingException
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
}
