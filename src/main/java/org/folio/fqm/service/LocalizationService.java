package org.folio.fqm.service;

import lombok.AllArgsConstructor;
import org.folio.querytool.domain.dto.ArrayType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.ObjectType;
import org.folio.spring.i18n.service.TranslationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Small wrapper class for {@link TranslationService TranslationService} to provide reusable templates for translations,
 * particularly for entity type definitions.
 */
@Service
@AllArgsConstructor(onConstructor_ = @Autowired)
public class LocalizationService {

  // refers to the entity type as a whole in plural, e.g. "Users", "Purchase order lines"
  private static final String ENTITY_TYPE_LABEL_TRANSLATION_TEMPLATE = "mod-fqm-manager.entityType.%s";
  // refers to a single column inside the entity type, e.g. "Name", "Barcode"
  private static final String ENTITY_TYPE_COLUMN_AND_SOURCE_LABEL_TRANSLATION_TEMPLATE = "mod-fqm-manager.entityType.%s.%s";
  // refers to a property inside an objectType column inside the entity type, e.g. "City" inside "Address" column inside "Users"
  private static final String ENTITY_TYPE_COLUMN_NESTED_LABEL_TRANSLATION_TEMPLATE =
    "mod-fqm-manager.entityType.%s.%s.%s";
  private static final String ENTITY_TYPE_COLUMN_NESTED_LABEL_QUALIFIED_TRANSLATION_TEMPLATE =
    "mod-fqm-manager.entityType.%s.%s.%s._qualified";
  // refers to a possessive version of the entity type, for custom fields, e.g. "User's {customField}"
  private static final String ENTITY_TYPE_CUSTOM_FIELD_POSSESSIVE_TRANSLATION_TEMPLATE =
    "mod-fqm-manager.entityType.%s._custom_field_possessive";
  private static final String ENTITY_TYPE_DESCRIPTION_TRANSLATION_TEMPLATE = "mod-fqm-manager.entityType.%s._description";

  // provides locale-specific way of joining `Source name — Field name`, to account for different separators or RTL
  // see MODFQMMGR-409 for more details
  private static final String ENTITY_TYPE_SOURCE_PREFIX_JOINER = "mod-fqm-manager.entityType._sourceLabelJoiner";

  // the translation parameter for custom fields
  private static final String CUSTOM_FIELD_PARAMETER = "customField";

  // translation logic happens in Warning classes
  public static final String MIGRATION_WARNING_TRANSLATION_TEMPLATE = "mod-fqm-manager.migration.warning.%s";

  private TranslationService translationService;

  public List<Locale> getCurrentLocales() {
    return translationService.getCurrentLocales();
  }

  public EntityType localizeEntityType(EntityType entityType, List<EntityTypeSource> sources) {
    entityType.setLabelAlias(getEntityTypeLabel(entityType));
    entityType.setDescription(getEntityTypeDescription(entityType));

    var localizedColumns = entityType.getColumns().stream()
      .map(column -> localizeEntityTypeColumn(entityType, sources, column))
      .toList();

    return entityType.columns(localizedColumns);
  }

  public EntityTypeColumn localizeEntityTypeColumn(EntityType entityType, List<EntityTypeSource> sources, EntityTypeColumn column) {
    var newColumn = column.toBuilder().build();
    if (newColumn.getLabelAlias() == null) {
      // Custom field names are already localized as they are user-defined, so they require special handling
      if (Boolean.TRUE.equals(newColumn.getIsCustomField())) {
        newColumn.setLabelAlias(getEntityTypeCustomFieldLabel(entityType.getName(), newColumn.getName()));
        return newColumn;
      }

      newColumn.setLabelAlias(getEntityTypeColumnLabel(entityType.getName(), newColumn.getName()));
      if (newColumn.getDataType() instanceof ObjectType objectColumn) {
        localizeObjectColumn(entityType, newColumn, objectColumn);
      } else if (newColumn.getDataType() instanceof ArrayType arrayColumn) {
        localizeArrayColumn(entityType, newColumn, arrayColumn);
      }
    } else {
      // column has been previously translated, so just append source translations to it
      String sourceTranslation = getTranslationWithSourcePrefix(entityType, newColumn.getName(), newColumn.getLabelAlias(), sources);
      newColumn.setLabelAlias(sourceTranslation);
    }
    return newColumn;
  }

  String localizeSourceLabel(EntityType entityType, String sourceAlias, EntityTypeSource source) {
    // If the source has a "name" property, then use it. Otherwise, translate the sourceAlias
    if (source.getName() != null) {
      return source.getName();
    }
    return translationService.format(
      ENTITY_TYPE_COLUMN_AND_SOURCE_LABEL_TRANSLATION_TEMPLATE.formatted(entityType.getName(), sourceAlias)
    );
  }

  private String getTranslationWithSourcePrefix(EntityType entityType, String columnName, String fieldLabel, List<EntityTypeSource> sources) {
    int currentSourceIndex = columnName.indexOf(".");
    if (currentSourceIndex > 0) {
      String currentSource = columnName.substring(0, currentSourceIndex);
      EntityTypeSource source = sources.stream()
        .filter(s -> Objects.equals(s.getAlias(), currentSource))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Unable to find source"));
      String sourceLabel = localizeSourceLabel(entityType, currentSource, source);

      return translationService.format(
        ENTITY_TYPE_SOURCE_PREFIX_JOINER,
        "sourceLabel",
        sourceLabel,
        "fieldLabel",
        fieldLabel
      );
    } else {
      return fieldLabel;
    }
  }

  private void localizeObjectColumn(EntityType entityType, EntityTypeColumn column, ObjectType objectColumn) {
    objectColumn
      .getProperties()
      .forEach(property -> {
        property.setLabelAlias(
          getEntityTypeColumnLabelNested(entityType.getName(), column.getName(), property.getName())
        );
        property.setLabelAliasFullyQualified(
          getQualifiedEntityTypeColumnLabelNested(entityType.getName(), column.getName(), property.getName())
        );
        if (property.getDataType() instanceof ObjectType nestedObject) {
          localizeObjectColumn(entityType, column, nestedObject);
        } else if (property.getDataType() instanceof ArrayType nestedArray) {
          localizeArrayColumn(entityType, column, nestedArray);
        }
      });
  }

  private void localizeArrayColumn(EntityType entityType, EntityTypeColumn column, ArrayType arrayColumn) {
    if (arrayColumn.getItemDataType() instanceof ObjectType nestedObject) {
      localizeObjectColumn(entityType, column, nestedObject);
    } else if (arrayColumn.getItemDataType() instanceof ArrayType nestedArray) {
      localizeArrayColumn(entityType, column, nestedArray);
    }
  }

  String getEntityTypeLabel(EntityType entityType) {
    if (!Boolean.TRUE.equals(entityType.getAdditionalProperty("isCustom"))) {
      return translationService.format(ENTITY_TYPE_LABEL_TRANSLATION_TEMPLATE.formatted(entityType.getName()));
    } else {
      return entityType.getName();
    }
  }

  private String getEntityTypeDescription(EntityType entityType) {
    if (!Boolean.TRUE.equals(entityType.getAdditionalProperty("isCustom"))) {
      return translationService.format(ENTITY_TYPE_DESCRIPTION_TRANSLATION_TEMPLATE.formatted(entityType.getName()));
    }
    return entityType.getDescription() != null ? entityType.getDescription() : "";
  }

  private String getEntityTypeColumnLabel(String tableName, String columnName) {
    return translationService.format(ENTITY_TYPE_COLUMN_AND_SOURCE_LABEL_TRANSLATION_TEMPLATE.formatted(tableName, columnName));
  }

  public String getEntityTypeColumnLabelNested(String tableName, String columnName, String nestedPropertyName) {
    return translationService.format(
      ENTITY_TYPE_COLUMN_NESTED_LABEL_TRANSLATION_TEMPLATE.formatted(tableName, columnName, nestedPropertyName)
    );
  }

  private String getQualifiedEntityTypeColumnLabelNested(
    String tableName,
    String columnName,
    String nestedPropertyName
  ) {
    return translationService.format(
      ENTITY_TYPE_COLUMN_NESTED_LABEL_QUALIFIED_TRANSLATION_TEMPLATE.formatted(
        tableName,
        columnName,
        nestedPropertyName
      )
    );
  }

  // Custom field names are already localized as they are user-defined, so we prepend a possessive (e.g. User's)
  // but leave the {@code customField} untouched
  private String getEntityTypeCustomFieldLabel(String tableName, String customField) {
    return translationService.format(
      ENTITY_TYPE_CUSTOM_FIELD_POSSESSIVE_TRANSLATION_TEMPLATE.formatted(tableName),
      CUSTOM_FIELD_PARAMETER,
      customField
    );
  }
}
