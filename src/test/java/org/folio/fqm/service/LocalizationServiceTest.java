package org.folio.fqm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.folio.querytool.domain.dto.ArrayType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.NestedObjectProperty;
import org.folio.querytool.domain.dto.ObjectType;
import org.folio.querytool.domain.dto.StringType;
import org.folio.spring.i18n.service.TranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalizationServiceTest {

  @Mock
  private TranslationService translationService;

  @InjectMocks
  private LocalizationService localizationService;

  @Test
  void testBasicEntityTypeFormatting() {
    String expectedTableTranslationKey = "mod-fqm-manager.entityType.table_name";
    String expectedColumnTranslationKey = "mod-fqm-manager.entityType.table_name.column_name";

    String expectedTableTranslation = "Table Name";
    String expectedColumnTranslation = "Column Name";

    EntityType entityType = new EntityType()
      .name("table_name")
      .addColumnsItem(new EntityTypeColumn().name("column_name"));

    when(translationService.format(expectedTableTranslationKey)).thenReturn(expectedTableTranslation);
    when(translationService.format(expectedColumnTranslationKey)).thenReturn(expectedColumnTranslation);

    EntityType actual = localizationService.localizeEntityType(entityType);

    assertEquals(expectedTableTranslation, actual.getLabelAlias());
    assertEquals(expectedColumnTranslation, actual.getColumns().get(0).getLabelAlias());

    verify(translationService, times(1)).format(expectedTableTranslationKey);
    verify(translationService, times(1)).format(expectedColumnTranslationKey);
    verifyNoMoreInteractions(translationService);
  }

  @Test
  void testCustomFieldFormatting() {
    String expectedTranslationKey = "mod-fqm-manager.entityType.table_name._custom_field_possessive";
    String expectedTranslation = "Test's Custom Field";

    EntityType entityType = new EntityType()
      .name("table_name")
      .addColumnsItem(new EntityTypeColumn().name("Custom Field").isCustomField(true));

    when(translationService.format(expectedTranslationKey, "customField", "Custom Field"))
      .thenReturn("Test's Custom Field");

    localizationService.localizeEntityTypeColumn(entityType, entityType.getColumns().get(0));

    assertEquals(expectedTranslation, entityType.getColumns().get(0).getLabelAlias());

    verify(translationService, times(1)).format(expectedTranslationKey, "customField", "Custom Field");
    verifyNoMoreInteractions(translationService);
  }

  @Test
  void testObjectTypeColumn() {
    String expectedOuterTranslationKey = "mod-fqm-manager.entityType.table_name.column_name";
    String expectedOuterTranslation = "Outer Column";
    String expectedInnerTranslationKey = "mod-fqm-manager.entityType.table_name.column_name.nested_property";
    String expectedInnerTranslation = "Nested Property";

    EntityType entityType = new EntityType()
      .name("table_name")
      .addColumnsItem(
        new EntityTypeColumn()
          .name("column_name")
          .dataType(
            new ObjectType()
              .addPropertiesItem(new NestedObjectProperty().dataType(new StringType()).name("nested_property"))
          )
      );

    when(translationService.format(expectedOuterTranslationKey)).thenReturn(expectedOuterTranslation);
    when(translationService.format(expectedInnerTranslationKey)).thenReturn(expectedInnerTranslation);

    localizationService.localizeEntityTypeColumn(entityType, entityType.getColumns().get(0));

    assertEquals(expectedOuterTranslation, entityType.getColumns().get(0).getLabelAlias());
    assertEquals(
      expectedInnerTranslation,
      ((ObjectType) entityType.getColumns().get(0).getDataType()).getProperties().get(0).getLabelAlias()
    );

    verify(translationService, times(1)).format(expectedOuterTranslationKey);
    verify(translationService, times(1)).format(expectedInnerTranslationKey);
    verifyNoMoreInteractions(translationService);
  }

  @Test
  void testNestedObjectArrayTypeColumn() {
    String expectedOuterTranslationKey = "mod-fqm-manager.entityType.table_name.column_name";
    String expectedOuterTranslation = "Outer Column";
    String expectedInnerTranslationKey = "mod-fqm-manager.entityType.table_name.column_name.nested_property";
    String expectedInnerTranslation = "Nested Property";
    String expectedInnermostTranslationKey = "mod-fqm-manager.entityType.table_name.column_name.nested_property_inner";
    String expectedInnermostTranslation = "Nested * 2 Property";

    // array -> array -> object -> object -> array
    NestedObjectProperty innermost = new NestedObjectProperty()
      .dataType(new ArrayType().itemDataType(new StringType()))
      .name("nested_property_inner");
    NestedObjectProperty inner = new NestedObjectProperty()
      .name("nested_property")
      .dataType(new ObjectType().addPropertiesItem(innermost));

    EntityType entityType = new EntityType()
      .name("table_name")
      .addColumnsItem(
        new EntityTypeColumn()
          .name("column_name")
          .dataType(
            new ArrayType().itemDataType(new ArrayType().itemDataType(new ObjectType().addPropertiesItem(inner)))
          )
      );

    when(translationService.format(expectedOuterTranslationKey)).thenReturn(expectedOuterTranslation);
    when(translationService.format(expectedInnerTranslationKey)).thenReturn(expectedInnerTranslation);
    when(translationService.format(expectedInnermostTranslationKey)).thenReturn(expectedInnermostTranslation);

    localizationService.localizeEntityTypeColumn(entityType, entityType.getColumns().get(0));

    assertEquals(expectedOuterTranslation, entityType.getColumns().get(0).getLabelAlias());
    assertEquals(expectedInnerTranslation, inner.getLabelAlias());
    assertEquals(expectedInnermostTranslation, innermost.getLabelAlias());

    verify(translationService, times(1)).format(expectedOuterTranslationKey);
    verify(translationService, times(1)).format(expectedInnerTranslationKey);
    verify(translationService, times(1)).format(expectedInnermostTranslationKey);
    verifyNoMoreInteractions(translationService);
  }
}
