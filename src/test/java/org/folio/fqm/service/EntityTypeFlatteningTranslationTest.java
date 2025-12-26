package org.folio.fqm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.folio.fqm.repository.EntityTypeCacheRepository;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.ArrayType;
import org.folio.querytool.domain.dto.NestedObjectProperty;
import org.folio.querytool.domain.dto.ObjectType;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.i18n.model.TranslationMap;
import org.folio.spring.i18n.service.TranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityTypeFlatteningTranslationTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String TENANT_ID = "tenant_01";

  private static final EntityType SIMPLE_1;
  private static final EntityType SIMPLE_2;
  private static final EntityType COMPOSITE_1;
  private static final EntityType DEEPLY_COMPOSITE;

  static {
    try {
      SIMPLE_1 =
        OBJECT_MAPPER.readValue(
          """
            {
              "id": "00000000-0000-0000-0000-000000000001",
              "name": "simple_1",
              "sources": [
                { "type": "db", "alias": "simple_1Source", "target": "simple_1_view", "order": 100 }
              ],
              "columns": [
                { "name": "field1", "dataType": { "dataType": "stringType" }, "valueGetter": "field1ValueGetter" },
                { "name": "field2", "dataType": { "dataType": "stringType" }, "valueGetter": "field2ValueGetter" },
                {
                  "name": "nestedTableField",
                  "sourceAlias": "simple_1Source",
                  "dataType": {
                    "dataType": "arrayType",
                    "itemDataType": {
                      "dataType": "objectType",
                      "properties": [
                        { "name": "prop1", "dataType": { "dataType": "stringType" }, "property": "prop1prop", "valueGetter": ":sourceAlias.nestedTableField ->> 'prop1'" },
                        { "name": "prop2", "dataType": { "dataType": "stringType" }, "property": "prop2prop", "valueGetter": ":sourceAlias.nestedTableField ->> 'prop2'" }
                      ]
                    }
                  },
                  "valueGetter": "nestedTableFieldValueGetter"
                }
              ]
            }
            """,
          EntityType.class
        );
      SIMPLE_2 =
        OBJECT_MAPPER.readValue(
          """
            {
              "id": "00000000-0000-0000-0000-000000000002",
              "name": "simple_2",
              "columns": [
                { "name": "fieldA", "dataType": { "dataType": "stringType" }, "valueGetter": "fieldAValueGetter" },
                { "name": "fieldB", "dataType": { "dataType": "stringType" }, "valueGetter": "fieldBValueGetter" }
              ]
            }
            """,
          EntityType.class
        );
      COMPOSITE_1 =
        OBJECT_MAPPER.readValue(
          """
            {
              "id": "cccccccc-cccc-cccc-cccc-cccccccccccc",
              "name": "composite_1",
              "sources": [
                { "type": "entity-type", "alias": "s1", "targetId": "00000000-0000-0000-0000-000000000001" }
              ]
            }
            """,
          EntityType.class
        );
      DEEPLY_COMPOSITE =
        OBJECT_MAPPER.readValue(
          """
            {
              "id": "dddddddd-dddd-dddd-dddd-dddddddddddd",
              "name": "deeply_composite",
              "sources": [
                { "type": "entity-type", "alias": "c1", "targetId": "cccccccc-cccc-cccc-cccc-cccccccccccc" },
                { "type": "entity-type", "alias": "s2", "targetId": "00000000-0000-0000-0000-000000000002" }
              ]
            }
            """,
          EntityType.class
        );
    } catch (JsonProcessingException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Mock
  private EntityTypeRepository entityTypeRepository;

  @Mock
  private TranslationService translationService;

  @Mock
  private FolioExecutionContext executionContext;

  @Mock
  private UserTenantService userTenantService;

  private EntityTypeFlatteningService entityTypeFlatteningService;

  @BeforeEach
  void setup() {
    LocalizationService localizationService = new LocalizationService(translationService);
    EntityTypeCacheRepository entityTypeCacheRepository = new EntityTypeCacheRepository(0);
    entityTypeFlatteningService =
      new EntityTypeFlatteningService(
        entityTypeRepository,
        entityTypeCacheRepository,
        localizationService,
        executionContext,
        userTenantService
      );
    lenient()
      .when(entityTypeRepository.getEntityTypeDefinition(eq(UUID.fromString(SIMPLE_1.getId())), any()))
      .thenReturn(Optional.of(SIMPLE_1));
    lenient()
      .when(entityTypeRepository.getEntityTypeDefinition(eq(UUID.fromString(SIMPLE_2.getId())), any()))
      .thenReturn(Optional.of(SIMPLE_2));
    lenient()
      .when(entityTypeRepository.getEntityTypeDefinition(eq(UUID.fromString(COMPOSITE_1.getId())), any()))
      .thenReturn(Optional.of(COMPOSITE_1));
    lenient()
      .when(entityTypeRepository.getEntityTypeDefinition(eq(UUID.fromString(DEEPLY_COMPOSITE.getId())), any()))
      .thenReturn(Optional.of(DEEPLY_COMPOSITE));

    when(translationService.format(anyString(), any(Object[].class)))
      .thenAnswer(invocation -> {
        Object[] args = invocation.getRawArguments();
        String key = (String) args[0];
        String translation =
          switch (key) {
            case "mod-fqm-manager.entityType._sourceLabelJoiner" -> "{sourceLabel} -> {fieldLabel}";
            case "mod-fqm-manager.entityType.simple_1" -> "Simple 1";
            case "mod-fqm-manager.entityType.simple_1.field1" -> "Field 1";
            case "mod-fqm-manager.entityType.simple_1.field2" -> "Field 2";
            case "mod-fqm-manager.entityType.simple_1.nestedTableField" -> "Nested";
            case "mod-fqm-manager.entityType.simple_1.nestedTableField.prop1" -> "Prop 1";
            case "mod-fqm-manager.entityType.simple_1.nestedTableField.prop1._qualified" -> "Nested Prop 1";
            case "mod-fqm-manager.entityType.simple_1.nestedTableField.prop2" -> "Prop 2";
            case "mod-fqm-manager.entityType.simple_1.nestedTableField.prop2._qualified" -> "Nested Prop 2";
            case "mod-fqm-manager.entityType.simple_2" -> "Simple 2";
            case "mod-fqm-manager.entityType.simple_2.fieldA" -> "Field A";
            case "mod-fqm-manager.entityType.simple_2.fieldB" -> "Field B";
            case "mod-fqm-manager.entityType.composite_1" -> "Composite 1";
            case "mod-fqm-manager.entityType.composite_1.s1" -> "Source 1";
            case "mod-fqm-manager.entityType.deeply_composite" -> "Deep composite";
            case "mod-fqm-manager.entityType.deeply_composite.s2" -> "Source 2";
            case "mod-fqm-manager.entityType.deeply_composite.c1" -> "Composite Source 1";
            default -> {
              if (!key.endsWith("_description")) { // Don't care about these
                System.out.println("Missing translation: " + key); // Tell me if we missed anything
              }
              yield key;
            }
          };
        return TranslationMap.formatString(Locale.US, ZoneId.of("UTC"), translation, (Object[]) args[1]);
      });
    when(userTenantService.getUserTenantsResponse(TENANT_ID)).thenReturn("{'totalRecords': 0}");
    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
  }

  @Test
  void shouldBuildLabelAliasHierarchy() {
    EntityType flattenedEntityType = entityTypeFlatteningService.getFlattenedEntityType(
      UUID.fromString(DEEPLY_COMPOSITE.getId()),
      TENANT_ID,
      false
    );

    assertEquals(5, flattenedEntityType.getColumns().size());

    var c1s1field1 = EntityTypeUtils.findColumnByName(flattenedEntityType, "c1.s1.field1");
    assertEquals("Composite Source 1 -> Source 1 -> Field 1", c1s1field1.getLabelAlias());

    var c1s1field2 = EntityTypeUtils.findColumnByName(flattenedEntityType, "c1.s1.field2");
    assertEquals("Composite Source 1 -> Source 1 -> Field 2", c1s1field2.getLabelAlias());

    var s2fieldA = EntityTypeUtils.findColumnByName(flattenedEntityType, "s2.fieldA");
    assertEquals("Source 2 -> Field A", s2fieldA.getLabelAlias());

    var s2fieldB = EntityTypeUtils.findColumnByName(flattenedEntityType, "s2.fieldB");
    assertEquals("Source 2 -> Field B", s2fieldB.getLabelAlias());

    var c1s1nestedTableField = EntityTypeUtils.findColumnByName(flattenedEntityType, "c1.s1.nestedTableField");
    assertEquals("Composite Source 1 -> Source 1 -> Nested", c1s1nestedTableField.getLabelAlias());
    var nestedType = (ArrayType) c1s1nestedTableField.getDataType();
    var objectType = (ObjectType) nestedType.getItemDataType();
    assertEquals(2, objectType.getProperties().size());
    for (NestedObjectProperty prop : objectType.getProperties()) {
      if (prop.getName().equals("prop1")) {
        assertEquals("Composite Source 1 -> Source 1 -> Nested Prop 1", prop.getLabelAliasFullyQualified());
      } else if (prop.getName().equals("prop2")) {
        assertEquals("Composite Source 1 -> Source 1 -> Nested Prop 2", prop.getLabelAliasFullyQualified());
      }
    }
  }
}
