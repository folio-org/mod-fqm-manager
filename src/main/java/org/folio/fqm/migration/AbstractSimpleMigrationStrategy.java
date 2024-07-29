package org.folio.fqm.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.service.FqlService;
import org.folio.fqm.service.MigrationService;

@Log4j2
public abstract class AbstractSimpleMigrationStrategy implements MigrationStrategy {

  protected final ObjectMapper objectMapper = new ObjectMapper();

  /** The version migrating FROM */
  public abstract String getSourceVersion();

  /** The version migrating TO */
  public abstract String getTargetVersion();

  /** The entity types that got a new UUID */
  protected abstract Map<UUID, UUID> getEntityTypeChanges();

  /**
   * The fields that were renamed. Keys should use their OLD entity type ID, if applicable.
   *
   * The special key "*" on the field map can be used to apply a template to all fields. This value
   * should be a string with a %s placeholder, which will be filled in with the original field name.
   */
  protected abstract Map<UUID, Map<String, String>> getFieldChanges();

  @Override
  public boolean applies(String version) {
    return this.getSourceVersion().equals(version);
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation src) {
    try {
      MigratableQueryInformation result = src;

      if (src.fqlQuery() == null) {
        result =
          result.withFqlQuery(
            objectMapper.writeValueAsString(Map.of(MigrationService.VERSION_KEY, this.getTargetVersion()))
          );
      }

      result =
        result.withEntityTypeId(this.getEntityTypeChanges().getOrDefault(src.entityTypeId(), src.entityTypeId()));

      ObjectNode fql = (ObjectNode) objectMapper.readTree(result.fqlQuery());
      fql.set(MigrationService.VERSION_KEY, objectMapper.valueToTree(this.getTargetVersion()));

      Map<String, String> fieldChanges = this.getFieldChanges().get(src.entityTypeId());
      if (fieldChanges != null) {
        // map query fields
        fql = migrateFqlTree(fieldChanges, fql);
        // map fields list
        result = result.withFields(src.fields().stream().map(f -> getNewFieldName(fieldChanges, f)).toList());
      }

      result = result.withFqlQuery(objectMapper.writeValueAsString(fql));

      return result;
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize FQL query", e);
      throw new UncheckedIOException(e);
    }
  }

  protected static String getNewFieldName(Map<String, String> fieldChanges, String oldFieldName) {
    if (MigrationService.VERSION_KEY.equals(oldFieldName)) {
      return oldFieldName;
    } else if (fieldChanges.containsKey("*")) {
      return fieldChanges.get("*").formatted(oldFieldName);
    } else {
      return fieldChanges.getOrDefault(oldFieldName, oldFieldName);
    }
  }

  protected static ObjectNode migrateFqlTree(Map<String, String> fieldChanges, ObjectNode fql) {
    ObjectNode result = new ObjectMapper().createObjectNode();
    // iterate through fields in source
    fql
      .fields()
      .forEachRemaining(entry -> {
        if ("$and".equals(entry.getKey())) {
          ArrayNode resultContents = new ObjectMapper().createArrayNode();
          ((ArrayNode) entry.getValue()).elements()
            .forEachRemaining(node -> resultContents.add(migrateFqlTree(fieldChanges, (ObjectNode) node)));
          result.set("$and", resultContents);
        } else {
          result.set(getNewFieldName(fieldChanges, entry.getKey()), entry.getValue());
        }
      });

    return result;
  }
}
