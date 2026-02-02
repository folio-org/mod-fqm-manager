package org.folio.fqm.migration.strategies;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.config.MigrationConfiguration;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.types.MigratableFqlFieldAndCondition;
import org.folio.fqm.migration.types.MigratableFqlFieldOnly;
import org.folio.fqm.migration.types.SingleFieldMigrationResult;
import org.folio.fqm.migration.warnings.EntityTypeWarning;
import org.folio.fqm.migration.warnings.EntityTypeWarningFactory;
import org.folio.fqm.migration.warnings.FieldWarning;
import org.folio.fqm.migration.warnings.FieldWarningFactory;
import org.folio.fqm.migration.warnings.QueryBreakingWarning;
import org.folio.fqm.migration.warnings.RemovedEntityWarning;
import org.folio.fqm.migration.warnings.RemovedFieldWarning;
import org.folio.fqm.migration.warnings.Warning;

@Log4j2
public abstract class AbstractSimpleMigrationStrategy extends AbstractRegularMigrationStrategy<Void> {

  protected final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * The entity types that got moved to a new UUID. These are direct mappings from old to new and
   * will not be considered a breaking change.
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
  public Map<UUID, EntityTypeWarningFactory> getEntityTypeWarnings() {
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
  public Map<UUID, Map<String, FieldWarningFactory>> getFieldWarnings() {
    return Map.of();
  }

  @Override
  public SingleFieldMigrationResult<MigratableFqlFieldAndCondition> migrateFql(
    Void v,
    MigratableFqlFieldAndCondition condition
  ) {
    Map<String, String> fieldChanges = this.getFieldChanges().getOrDefault(condition.entityTypeId(), Map.of());
    Map<String, FieldWarningFactory> fieldWarnings =
      this.getFieldWarnings().getOrDefault(condition.entityTypeId(), Map.of());

    SingleFieldMigrationResult<MigratableFqlFieldAndCondition> result = SingleFieldMigrationResult.noop(condition);

    FieldWarningFactory warningFactory = fieldWarnings.get(condition.field());
    if (warningFactory != null) {
      FieldWarning warning = warningFactory.apply(
        condition.fieldPrefix(),
        condition.field(),
        condition.getConditionObject().toPrettyString()
      );
      if (warning instanceof RemovedFieldWarning || warning instanceof QueryBreakingWarning) {
        return SingleFieldMigrationResult
          .<MigratableFqlFieldAndCondition>removed()
          .withWarnings(List.of(warning))
          .withHadBreakingChange(true);
      } else {
        result = result.withWarnings(List.of(warning));
      }
    }

    return result.withResult(List.of(condition.withField(getNewFieldName(fieldChanges, condition.field()))));
  }

  @Override
  public SingleFieldMigrationResult<MigratableFqlFieldOnly> migrateFieldName(Void v, MigratableFqlFieldOnly field) {
    Map<String, String> fieldChanges = this.getFieldChanges().getOrDefault(field.entityTypeId(), Map.of());
    Map<String, FieldWarningFactory> fieldWarnings =
      this.getFieldWarnings().getOrDefault(field.entityTypeId(), Map.of());

    SingleFieldMigrationResult<MigratableFqlFieldOnly> result = SingleFieldMigrationResult.noop(field);

    FieldWarningFactory warningFactory = fieldWarnings.get(field.field());
    if (warningFactory != null) {
      FieldWarning warning = warningFactory.apply(field.fieldPrefix(), field.field(), null);
      switch (warning) {
        case RemovedFieldWarning w -> {
          return SingleFieldMigrationResult
            .<MigratableFqlFieldOnly>removed()
            .withWarnings(List.of(w))
            .withHadBreakingChange(true);
        }
        case QueryBreakingWarning w -> {
          break;
        }
        default -> result = result.withWarnings(List.of(warning));
      }
    }

    return result.withResult(List.of(field.withField(getNewFieldName(fieldChanges, field.field()))));
  }

  @Override
  public MigratableQueryInformation additionalChanges(Void v, MigratableQueryInformation query) {
    Optional<EntityTypeWarning> entityTypeWarning = Optional
      .ofNullable(getEntityTypeWarnings().get(query.entityTypeId()))
      .map(f -> f.apply(query.fqlQuery()));

    MigratableQueryInformation result = query;

    if (entityTypeWarning.isPresent()) {
      switch (entityTypeWarning.get()) {
        case RemovedEntityWarning w -> {
          return MigratableQueryInformation
            .builder()
            .entityTypeId(MigrationConfiguration.REMOVED_ENTITY_TYPE_ID)
            .fqlQuery("{}")
            .fields(List.of())
            .warning(w)
            .hadBreakingChanges(true)
            .build();
        }
        default -> {
          List<Warning> warnings = new ArrayList<>(result.warnings());
          warnings.add(entityTypeWarning.get());
          return result.withWarnings(warnings);
        }
      }
    }

    if (this.getEntityTypeChanges().containsKey(query.entityTypeId())) {
      result =
        result.withEntityTypeId(this.getEntityTypeChanges().getOrDefault(query.entityTypeId(), query.entityTypeId()));
    }

    return result;
  }

  protected static String getNewFieldName(Map<String, String> fieldChanges, String oldFieldName) {
    if (MigrationConfiguration.VERSION_KEY.equals(oldFieldName)) {
      return oldFieldName;
    } else if (fieldChanges.containsKey(oldFieldName)) { // specific field changes take priority over wildcards
      return fieldChanges.get(oldFieldName);
    } else if (fieldChanges.containsKey("*")) {
      return fieldChanges.get("*").formatted(oldFieldName);
    } else {
      return oldFieldName;
    }
  }
}
