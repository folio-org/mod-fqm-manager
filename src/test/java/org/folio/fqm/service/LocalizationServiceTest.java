package org.folio.fqm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class LocalizationServiceTest {

  private static final String ENTITY_TYPE_SOURCE_PREFIX_JOINER = "mod-fqm-manager.entityType._sourceLabelJoiner";

  @Mock
  private TranslationService translationService;

  @InjectMocks
  private LocalizationService localizationService;

  private void testBasicEntityTypeFormatting(Map<String, String> translations,
                                             String expectedTableTranslation,
                                             String expectedColumnTranslation,
                                             int numInvocations) {
    translations.forEach((translationKey, translationValue) -> when(translationService.format(translationKey)).thenReturn(translationValue));

    EntityType entityType = new EntityType()
      .name("table_name")
      .addColumnsItem(new EntityTypeColumn().name("column_name"));

    EntityType actual = localizationService.localizeEntityType(entityType);
    assertEquals(expectedTableTranslation, actual.getLabelAlias());
    assertEquals(expectedColumnTranslation, actual.getColumns().get(0).getLabelAlias());
    verify(translationService, times(numInvocations)).format(anyString());
    verifyNoMoreInteractions(translationService);
  }

  private void mockSourceLabelJoiner() {
    when(
      translationService.format(
        eq(ENTITY_TYPE_SOURCE_PREFIX_JOINER),
        eq("sourceLabel"),
        anyString(),
        eq("fieldLabel"),
        anyString()
      )
    )
      .thenAnswer(invocation -> {
        String sourceLabel = invocation.getArgument(2);
        String fieldLabel = invocation.getArgument(4);
        return sourceLabel + " | " + fieldLabel;
      });
  }

  @Test
  void testSimpleEntityTypeTranslations() {
    String expectedTableTranslation = "Table Name";
    String expectedColumnTranslation = "Column Name";
    testBasicEntityTypeFormatting(
      Map.of(
        "mod-fqm-manager.entityType.table_name",
        expectedTableTranslation,
        "mod-fqm-manager.entityType.table_name.column_name",
        expectedColumnTranslation
      ),
      expectedTableTranslation,
      expectedColumnTranslation,
      2
    );
  }

  @Test
  void testEntityTypeRootTranslations() {
    String expectedTableTranslationKey = "mod-fqm-manager.entityType.table_name";
    String expectedTableTranslation = "Table Name";

    String expectedInnerSourceTranslationKey = "mod-fqm-manager.entityType.table_name.nested_source";
    String expectedInnerSourceTranslation = "Inner Source Name";

    String expectedColumnTranslation = "Field from a simple entity!";

    EntityType entityType = new EntityType()
      .name("table_name")
      .addColumnsItem(new EntityTypeColumn().name("nested_source.foo").labelAlias(expectedColumnTranslation));

    when(translationService.format(expectedTableTranslationKey)).thenReturn(expectedTableTranslation);
    when(translationService.format(expectedInnerSourceTranslationKey)).thenReturn(expectedInnerSourceTranslation);
    mockSourceLabelJoiner();

    EntityType result = localizationService.localizeEntityType(entityType);
    assertEquals("Inner Source Name | Field from a simple entity!", result.getColumns().get(0).getLabelAlias());

    verify(translationService, times(2)).format(anyString());
    verify(translationService, times(1)).format(anyString(), any(), any(), any(), any());
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

    var localizedColumn = localizationService.localizeEntityTypeColumn(entityType, entityType.getColumns().get(0));

    assertEquals(expectedTranslation, localizedColumn.getLabelAlias());

    verify(translationService, times(1)).format(expectedTranslationKey, "customField", "Custom Field");
    verifyNoMoreInteractions(translationService);
  }

  @Test
  void testObjectTypeColumn() {
    String expectedOuterTranslationKey = "mod-fqm-manager.entityType.table_name.column_name";
    String expectedOuterTranslation = "Outer Column";
    String expectedInnerTranslationKey = "mod-fqm-manager.entityType.table_name.column_name.nested_property";
    String expectedInnerTranslation = "Nested Property";
    String expectedInnerQualifiedTranslationKey = "mod-fqm-manager.entityType.table_name.column_name.nested_property._qualified";
    String expectedInnerQualifiedTranslation = "Outer Column's Nested Property";

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
    when(translationService.format(expectedInnerQualifiedTranslationKey)).thenReturn(expectedInnerQualifiedTranslation);

    var localizedColumn = localizationService.localizeEntityTypeColumn(entityType, entityType.getColumns().get(0));

    assertEquals(expectedOuterTranslation, localizedColumn.getLabelAlias());
    assertEquals(
      expectedInnerTranslation,
      ((ObjectType) entityType.getColumns().get(0).getDataType()).getProperties().get(0).getLabelAlias()
    );
    assertEquals(
      expectedInnerQualifiedTranslation,
      ((ObjectType) entityType.getColumns().get(0).getDataType()).getProperties().get(0).getLabelAliasFullyQualified()
    );

    verify(translationService, times(1)).format(expectedOuterTranslationKey);
    verify(translationService, times(1)).format(expectedInnerTranslationKey);
    verify(translationService, times(1)).format(expectedInnerQualifiedTranslationKey);
    verifyNoMoreInteractions(translationService);
  }

  @Test
  void testNestedObjectArrayTypeColumn() {
    String expectedOuterTranslationKey = "mod-fqm-manager.entityType.table_name.column_name";
    String expectedOuterTranslation = "Outer Column";
    String expectedInnerTranslationKey = "mod-fqm-manager.entityType.table_name.column_name.nested_property";
    String expectedInnerTranslation = "Nested Property";
    String expectedInnerQualifiedTranslationKey = "mod-fqm-manager.entityType.table_name.column_name.nested_property._qualified";
    String expectedInnerQualifiedTranslation = "Outer Column's Nested Property";
    String expectedInnermostTranslationKey = "mod-fqm-manager.entityType.table_name.column_name.nested_property_inner";
    String expectedInnermostTranslation = "Nested * 2 Property";
    String expectedInnermostQualifiedTranslationKey = "mod-fqm-manager.entityType.table_name.column_name.nested_property_inner._qualified";
    String expectedInnermostQualifiedTranslation = "Outer Column's Nested Property's Nested * 2 Property";

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
    when(translationService.format(expectedInnerQualifiedTranslationKey)).thenReturn(expectedInnerQualifiedTranslation);
    when(translationService.format(expectedInnermostQualifiedTranslationKey)).thenReturn(expectedInnermostQualifiedTranslation);

    var localizedColumn = localizationService.localizeEntityTypeColumn(entityType, entityType.getColumns().get(0));

    assertEquals(expectedOuterTranslation, localizedColumn.getLabelAlias());
    assertEquals(expectedInnerTranslation, inner.getLabelAlias());
    assertEquals(expectedInnermostTranslation, innermost.getLabelAlias());
    assertEquals(expectedInnerQualifiedTranslation, inner.getLabelAliasFullyQualified());
    assertEquals(expectedInnermostQualifiedTranslation, innermost.getLabelAliasFullyQualified());

    verify(translationService, times(1)).format(expectedOuterTranslationKey);
    verify(translationService, times(1)).format(expectedInnerTranslationKey);
    verify(translationService, times(1)).format(expectedInnermostTranslationKey);
    verify(translationService, times(1)).format(expectedInnerQualifiedTranslationKey);
    verify(translationService, times(1)).format(expectedInnermostQualifiedTranslationKey);
    verifyNoMoreInteractions(translationService);
  }
}
