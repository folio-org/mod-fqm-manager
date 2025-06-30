package org.folio.fqm.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.service.FqlService;
import org.folio.fqm.config.MigrationConfiguration;
import org.folio.fqm.migration.warnings.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;

@Log4j2
public abstract class AbstractSimpleMigrationStrategy implements MigrationStrategy {

  protected final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * The entity types that got a new UUID
   */
  public Map<UUID, UUID> getEntityTypeChanges() {
    return Map.of();
  }

  /**
   * The fields that were renamed. Keys should use their OLD entity type ID, if applicable.
   * <p>
   * The special key "*" on the field map can be used to apply a template to all fields. This value
   * should be a string with a %s placeholder, which will be filled in with the original field name.
   */
  public Map<UUID, Map<String, String>> getFieldChanges() {
    return Map.of();
  }

  /**
   * Entity types that were removed or deprecated. Removed ones will automatically be mapped to the `removed` entity type.
   */
  public Map<UUID, Function<String, EntityTypeWarning>> getEntityTypeWarnings() {
    return Map.of();
  }

  /**
   * The fields that were deprecated, removed, etc.
   * <p>
   * ET keys should use their OLD entity type ID, if applicable.
   * Fields should use their OLD names, if applicable.
   * <p>
   * The function will be given the field's name and FQL (if applicable), as a string
   */
  public Map<UUID, Map<String, BiFunction<String, String, FieldWarning>>> getFieldWarnings() {
    return Map.of();
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation src) {
    MigratableQueryInformation result = src;
    Set<Warning> warnings = new HashSet<>(src.warnings());

    Optional<EntityTypeWarning> entityTypeWarning = Optional
      .ofNullable(getEntityTypeWarnings().get(src.entityTypeId()))
      .map(f -> f.apply(src.fqlQuery()));
    if (entityTypeWarning.isPresent() && entityTypeWarning.get() instanceof RemovedEntityWarning) {
      return MigratableQueryInformation
        .builder()
        .entityTypeId(MigrationConfiguration.REMOVED_ENTITY_TYPE_ID)
        .fqlQuery("{}")
        .fields(List.of())
        .warning(getEntityTypeWarnings().get(src.entityTypeId()).apply(src.fqlQuery()))
        .hadBreakingChanges(true)
        .build();
    } else if (entityTypeWarning.isPresent()) {
      warnings.add(entityTypeWarning.get());
    }
    if (this.getEntityTypeChanges().containsKey(src.entityTypeId())) {
      result = result
        .withEntityTypeId(this.getEntityTypeChanges().getOrDefault(src.entityTypeId(), src.entityTypeId()))
        .withHadBreakingChanges(true);
    }

    Map<String, String> fieldChanges = this.getFieldChanges().getOrDefault(src.entityTypeId(), Map.of());
    Map<String, BiFunction<String, String, FieldWarning>> fieldWarnings =
      this.getFieldWarnings().getOrDefault(src.entityTypeId(), Map.of());

    AtomicBoolean hadBreakingChanges = new AtomicBoolean(false);
    String newFql = MigrationUtils.migrateFql(
      result.fqlQuery(),
      (fql, key, value) -> {
        if (fieldWarnings.containsKey(key)) {
          FieldWarning warning = fieldWarnings.get(key).apply(key, value.toPrettyString());

          warnings.add(warning);
          if (warning instanceof RemovedFieldWarning || warning instanceof QueryBreakingWarning) {
            hadBreakingChanges.set(true);
            return;
          }
        }
        fql.set(getNewFieldName(fieldChanges, key), value);
      }
    );

    if (!fieldChanges.isEmpty() || !fieldWarnings.isEmpty()) {
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
                  hadBreakingChanges.set(true);
                }
                if (warning instanceof RemovedFieldWarning) {
                  return null;
                }
              }
              String newFieldName = getNewFieldName(fieldChanges, f);
              if (!f.equals(newFieldName)) {
                hadBreakingChanges.set(true);
              }
              return newFieldName;
            })
            .filter(Objects::nonNull)
            .distinct()
            .toList()
        );
    }

    if (hadBreakingChanges.get()) {
      result = result.withHadBreakingChanges(true);
    }

    result = result.withFqlQuery(newFql);

    return result.withWarnings(new ArrayList<>(warnings));
  }

  protected static String getNewFieldName(Map<String, String> fieldChanges, String oldFieldName) {
    if (MigrationConfiguration.VERSION_KEY.equals(oldFieldName)) {
      return oldFieldName;
    } else if (fieldChanges.containsKey("*")) {
      return fieldChanges.get("*").formatted(oldFieldName);
    } else {
      return fieldChanges.getOrDefault(oldFieldName, oldFieldName);
    }
  }
}
