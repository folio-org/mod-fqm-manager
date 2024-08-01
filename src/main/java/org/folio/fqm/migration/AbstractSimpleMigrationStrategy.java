package org.folio.fqm.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.service.FqlService;
import org.folio.fqm.migration.warnings.EntityTypeWarning;
import org.folio.fqm.migration.warnings.FieldWarning;
import org.folio.fqm.migration.warnings.QueryBreakingWarning;
import org.folio.fqm.migration.warnings.RemovedEntityWarning;
import org.folio.fqm.migration.warnings.RemovedFieldWarning;
import org.folio.fqm.migration.warnings.Warning;
import org.folio.fqm.service.MigrationService;

@Log4j2
public abstract class AbstractSimpleMigrationStrategy implements MigrationStrategy {

  protected final ObjectMapper objectMapper = new ObjectMapper();

  /** The version migrating FROM */
  public abstract String getSourceVersion();

  /** The version migrating TO */
  public abstract String getTargetVersion();

  /** The entity types that got a new UUID */
  public abstract Map<UUID, UUID> getEntityTypeChanges();

  /**
   * The fields that were renamed. Keys should use their OLD entity type ID, if applicable.
   *
   * The special key "*" on the field map can be used to apply a template to all fields. This value
   * should be a string with a %s placeholder, which will be filled in with the original field name.
   */
  public abstract Map<UUID, Map<String, String>> getFieldChanges();

  /**
   * Entity types that were removed or deprecated. Removed ones will automatically be mapped to the `removed` entity type.
   */
  public abstract Map<UUID, Function<String, EntityTypeWarning>> getEntityTypeWarnings();

  /**
   * The fields that were deprecated, removed, etc.
   *
   * ET keys should use their OLD entity type ID, if applicable.
   * Fields should use their OLD names, if applicable.
   *
   * The function will be given the field's name and FQL (if applicable), as a string
   */
  public abstract Map<UUID, Map<String, BiFunction<String, String, FieldWarning>>> getFieldWarnings();

  @Override
  public boolean applies(String version) {
    return this.getSourceVersion().equals(version);
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation src) {
    try {
      MigratableQueryInformation result = src;
      Set<Warning> warnings = new HashSet<>(src.warnings());

      Optional<EntityTypeWarning> entityTypeWarning = Optional
        .ofNullable(getEntityTypeWarnings().get(src.entityTypeId()))
        .map(f -> f.apply(src.fqlQuery()));
      if (entityTypeWarning.isPresent() && entityTypeWarning.get() instanceof RemovedEntityWarning) {
        return MigratableQueryInformation
          .builder()
          .entityTypeId(MigrationService.REMOVED_ENTITY_TYPE_ID)
          .fqlQuery(objectMapper.writeValueAsString(Map.of(MigrationService.VERSION_KEY, this.getTargetVersion())))
          .fields(List.of())
          .warning(getEntityTypeWarnings().get(src.entityTypeId()).apply(src.fqlQuery()))
          .build();
      } else if (entityTypeWarning.isPresent()) {
        warnings.add(entityTypeWarning.get());
      }

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

      Map<String, String> fieldChanges = this.getFieldChanges().getOrDefault(src.entityTypeId(), Map.of());
      Map<String, BiFunction<String, String, FieldWarning>> fieldWarnings =
        this.getFieldWarnings().getOrDefault(src.entityTypeId(), Map.of());
      if (!fieldChanges.isEmpty() || !fieldWarnings.isEmpty()) {
        // map query fields
        fql = migrateFqlTree(fieldChanges, fieldWarnings, fql, warnings);

        // map fields list
        result =
          result.withFields(
            src
              .fields()
              .stream()
              .map(f -> {
                if (fieldWarnings.containsKey(f)) {
                  Warning warning = fieldWarnings.get(f).apply(f, null);
                  if (!(warning instanceof QueryBreakingWarning)) {
                    warnings.add(warning);
                  }
                  if (warning instanceof RemovedFieldWarning) {
                    return null;
                  }
                }
                return getNewFieldName(fieldChanges, f);
              })
              .filter(f -> f != null)
              .distinct()
              .toList()
          );
      }

      result = result.withFqlQuery(objectMapper.writeValueAsString(fql));

      return result.withWarnings(new ArrayList<>(warnings));
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

  protected static ObjectNode migrateFqlTree(
    Map<String, String> fieldChanges,
    Map<String, BiFunction<String, String, FieldWarning>> fieldWarnings,
    ObjectNode fql,
    Set<Warning> warnings
  ) {
    ObjectNode result = new ObjectMapper().createObjectNode();
    // iterate through fields in source
    fql
      .fields()
      .forEachRemaining(entry -> {
        if ("$and".equals(entry.getKey())) {
          ArrayNode resultContents = new ObjectMapper().createArrayNode();
          ((ArrayNode) entry.getValue()).elements()
            .forEachRemaining(node -> {
              ObjectNode innerResult = migrateFqlTree(fieldChanges, fieldWarnings, (ObjectNode) node, warnings);
              // handle removed fields
              if (!innerResult.isEmpty()) {
                resultContents.add(innerResult);
              }
            });
          result.set("$and", resultContents);
        } else {
          if (fieldWarnings.containsKey(entry.getKey())) {
            FieldWarning warning = fieldWarnings
              .get(entry.getKey())
              .apply(entry.getKey(), entry.getValue().toPrettyString());
            warnings.add(warning);
            if (warning instanceof RemovedFieldWarning || warning instanceof QueryBreakingWarning) {
              return;
            }
          }
          result.set(getNewFieldName(fieldChanges, entry.getKey()), entry.getValue());
        }
      });

    return result;
  }
}
