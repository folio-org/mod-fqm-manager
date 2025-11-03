package org.folio.fqm.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.SneakyThrows;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.ArrayType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.querytool.domain.dto.NestedObjectProperty;
import org.folio.querytool.domain.dto.ObjectType;
import org.folio.querytool.domain.dto.StringType;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityTypeFlatteningServiceTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String TENANT_ID = "tenant_01";

  private static final EntityType SIMPLE_1;
  private static final EntityType SIMPLE_2;
  private static final EntityType COMPOSITE_1_WRAPPER;
  private static final EntityType COMPOSITE_2_WRAPPER;
  private static final EntityType COMPOSITE_1_TO_2;
  private static final EntityType COMPOSITE_2_TO_1;
  private static final EntityType COMPOSITE_1W_TO_2W;
  private static final EntityType COMPOSITE_COMPOSITE;

  static {
    try {
      SIMPLE_1 =
        OBJECT_MAPPER.readValue(
          """
          {
            "id": "00000000-0000-0000-0000-000000000001",
            "name": "simple_1",
            "columns": [
              {
                "name": "field1",
                "dataType": { "dataType": "stringType" },
                "valueGetter": ":source1.field1",
                "isIdColumn": true
              },
              {
                "name": "field2",
                "dataType": { "dataType": "stringType" },
                "valueGetter": ":source1.field2",
                "filterValueGetter": "lower(:source1.field2)"
              },
              {
                "name": "object",
                "dataType": {
                  "dataType": "objectType",
                  "properties": [
                    {
                      "name": "object_field1",
                      "dataType": { "dataType": "stringType" },
                      "valueGetter": ":source1.object.object_field1",
                      "filterValueGetter": ":source1.object.object_field1"
                    },
                    {
                      "name": "object_field2",
                      "dataType": { "dataType": "stringType" },
                      "valueGetter": ":source1.object.object_field2",
                      "filterValueGetter": ":source1.object.object_field2"
                    }
                  ]
                },
                "valueGetter": ":source1.field2",
                "filterValueGetter": "lower(:source1.field2)"
              },
              {
                "name": "string_array_field",
                "dataType": { "dataType": "arrayType", "itemDataType": { "dataType": "stringType" } },
                "valueGetter": ":source1.string_array_field",
                "filterValueGetter": "lower(:source1.string_array_field)"
              },
              {
                "name": "nested_string_array_field",
                "dataType": {
                  "dataType": "arrayType",
                  "itemDataType": { "dataType": "arrayType", "itemDataType": { "dataType": "stringType" } }
                },
                "valueGetter": ":source1.nested_string_array_field",
                "filterValueGetter": "lower(:source1.nested_string_array_field)"
              },
              {
                "name": "object_array_field",
                "dataType": {
                  "dataType": "arrayType",
                  "itemDataType": {
                    "dataType": "objectType",
                    "properties": [
                      {
                        "name": "array_object_field1",
                        "dataType": { "dataType": "stringType" },
                        "valueGetter": ":source1.array_object.array_object_field1",
                        "filterValueGetter": ":source1.array_object.array_object_field1"
                      },
                      {
                        "name": "array_object_field2",
                        "dataType": { "dataType": "stringType" },
                        "valueGetter": ":source1.array_object.array_object_field2",
                        "filterValueGetter": ":source1.array_object.object_field2"
                      }
                    ]
                  }
                },
                "valueGetter": ":source1.object_array_field",
                "filterValueGetter": "lower(:source1.object_array_field)"
              },
              {
                "name": "ecs_field",
                "dataType": { "dataType": "stringType" },
                "valueGetter": ":source1.ecs_field",
                "values": [
                  { "value": "value1", "label": "label1" },
                  { "value": "value1", "label": "label1" }
                ],
                "ecsOnly": true,
                "isIdColumn": true
              }
            ],
            "crossTenantQueriesEnabled": false,
            "sources": [
              {
                "type": "db",
                "alias": "source1",
                "target": "source1_target"
              }
            ],
            "requiredPermissions": ["simple_permission1", "simple_permission2"],
            "sourceViewExtractor": ":source1.some_view_extractor",
            "filterConditions": [":source1.field1 != 'xyz'"]
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
              {
                "name": "fieldZ",
                "dataType": { "dataType": "stringType" },
                "valueGetter": ":sourceZ.field1",
                "isIdColumn": true,
                "joinsTo": [
                  {
                    "targetId": "00000000-0000-0000-0000-000000000001",
                    "targetField": "field1",
                    "type": "equality-simple"
                  }
                ]
              }
            ],
            "sources": [
              {
                "type": "db",
                "alias": "sourceZ",
                "target": "sourceZ_target"
              }
            ],
            "requiredPermissions": []
          }
          """,
          EntityType.class
        );

      COMPOSITE_1_WRAPPER =
        OBJECT_MAPPER.readValue(
          """
          {
            "id": "cccccccc-0000-0000-0000-000000000001",
            "name": "composite_1",
            "columns": [],
            "sources": [
              {
                "type": "entity-type",
                "alias": "simple_1",
                "targetId": "00000000-0000-0000-0000-000000000001"
              }
            ],
            "filterConditions": [":simple_1.source1.field2 != 'abc'"]
          }
          """,
          EntityType.class
        );
      COMPOSITE_2_WRAPPER =
        OBJECT_MAPPER.readValue(
          """
          {
            "id": "cccccccc-0000-0000-0000-000000000002",
            "name": "composite_2",
            "columns": [],
            "sources": [
              {
                "type": "entity-type",
                "alias": "simple_2",
                "targetId": "00000000-0000-0000-0000-000000000002"
              }
            ]
          }
          """,
          EntityType.class
        );
      COMPOSITE_1_TO_2 =
        OBJECT_MAPPER.readValue(
          """
          {
            "id": "dddddddd-0000-0000-0000-000000000012",
            "name": "composite_1_to_2",
            "columns": [],
            "sources": [
              {
                "type": "entity-type",
                "alias": "simple_1",
                "targetId": "00000000-0000-0000-0000-000000000001",
                "targetField": "field1",
                "sourceField": "simple_2.fieldZ"
              },
              {
                "type": "entity-type",
                "alias": "simple_2",
                "targetId": "00000000-0000-0000-0000-000000000002",
                "order": 1
              }
            ]
          }
          """,
          EntityType.class
        );
      COMPOSITE_2_TO_1 =
        OBJECT_MAPPER.readValue(
          """
          {
            "id": "dddddddd-0000-0000-0000-000000000021",
            "name": "composite_2_to_1",
            "columns": [],
            "sources": [
              {
                "type": "entity-type",
                "alias": "simple_1",
                "targetId": "00000000-0000-0000-0000-000000000001"
              },
              {
                "type": "entity-type",
                "alias": "simple_2",
                "targetId": "00000000-0000-0000-0000-000000000002",
                "targetField": "fieldZ",
                "sourceField": "simple_1.field1"
              }
            ]
          }
          """,
          EntityType.class
        );
      COMPOSITE_1W_TO_2W =
        OBJECT_MAPPER.readValue(
          """
          {
            "id": "eeeeeeee-0000-0000-0000-000000000102",
            "name": "composite_1_wrapper_to_2_wrapper",
            "columns": [],
            "sources": [
              {
                "type": "entity-type",
                "alias": "composite_1",
                "targetId": "cccccccc-0000-0000-0000-000000000001"
              },
              {
                "type": "entity-type",
                "alias": "composite_2",
                "targetId": "cccccccc-0000-0000-0000-000000000002",
                "targetField": "simple_2.fieldZ",
                "sourceField": "composite_1.simple_1.field1"
              }
            ]
          }
          """,
          EntityType.class
        );
      COMPOSITE_COMPOSITE =
        OBJECT_MAPPER.readValue(
          """
          {
            "id": "ffffffff-0000-0000-0000-000000000000",
            "name": "composite_composite",
            "columns": [],
            "sources": [
              {
                "type": "entity-type",
                "alias": "composite_wrapper_to_wrapper",
                "targetId": "eeeeeeee-0000-0000-0000-000000000102"
              },
              {
                "type": "entity-type",
                "alias": "composite_1_to_2",
                "targetId": "dddddddd-0000-0000-0000-000000000012",
                "targetField": "simple_2.fieldZ",
                "sourceField": "composite_wrapper_to_wrapper.composite_1.simple_1.field1"
              }
            ],
            "requiredPermissions": ["extra_perm"]
          }
          """,
          EntityType.class
        );
      // what is that composite_composite amalgamation?
      // composite_composite
      // ├--- composite_wrapper_to_wrapper
      // |    ├--- composite_1 <-----------┐ <------------------------┐ via composite_1 to simple_2 (D)
      // |    |    └--- simple_1 -> db (A) | via simple_2 to simple_1 | resolves. simple_1 (A) to simple_2 (D)
      // |    └--- composite_2 ------------┘                          |
      // |         └--- simple_2 -> db (B)                            |
      // └--- composite_1_to_2 ---------------------------------------┘
      //      ├--- simple_1 -> db (C) ---┐
      //      └--- simple_2 -> db (D) <--┘
    } catch (JsonProcessingException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Mock
  private EntityTypeRepository entityTypeRepository;

  @Mock
  private LocalizationService localizationService;

  @Mock
  private UserTenantService userTenantService;

  @Mock
  private FolioExecutionContext executionContext;

  private EntityTypeFlatteningService entityTypeFlatteningService;

  @BeforeEach
  void setup() {
    entityTypeFlatteningService =
      new EntityTypeFlatteningService(
        entityTypeRepository,
        localizationService,
        executionContext,
        userTenantService,
        0
      );

    for (EntityType et : List.of(
      SIMPLE_1,
      SIMPLE_2,
      COMPOSITE_1_WRAPPER,
      COMPOSITE_2_WRAPPER,
      COMPOSITE_1_TO_2,
      COMPOSITE_2_TO_1,
      COMPOSITE_1W_TO_2W,
      COMPOSITE_COMPOSITE
    )) {
      lenient()
        .when(entityTypeRepository.getEntityTypeDefinition(eq(UUID.fromString(et.getId())), any()))
        .thenReturn(Optional.of(copyEntityType(et)));
    }
    lenient()
      .when(localizationService.localizeEntityType(any(EntityType.class), any()))
      .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
      .when(localizationService.localizeEntityTypeColumn(any(EntityType.class), any(), any(EntityTypeColumn.class)))
      .then(invocation -> invocation.getArgument(2));
    lenient().when(executionContext.getTenantId()).thenReturn(TENANT_ID);
  }

  static List<Arguments> allEntities() {
    return List.of(
      Arguments.of(SIMPLE_1),
      Arguments.of(SIMPLE_2),
      Arguments.of(COMPOSITE_1_WRAPPER),
      Arguments.of(COMPOSITE_2_WRAPPER),
      Arguments.of(COMPOSITE_1_TO_2),
      Arguments.of(COMPOSITE_2_TO_1),
      Arguments.of(COMPOSITE_1W_TO_2W),
      Arguments.of(COMPOSITE_COMPOSITE)
    );
  }

  @ParameterizedTest
  @MethodSource("allEntities")
  void testAllFlattenSuccessfully(EntityType entityType) {
    when(userTenantService.getUserTenantsResponse(TENANT_ID)).thenReturn("{'totalRecords': 0}");

    EntityType flattened = entityTypeFlatteningService.getFlattenedEntityType(
      UUID.fromString(entityType.getId()),
      TENANT_ID,
      false
    );

    for (EntityTypeSource source : flattened.getSources()) {
      if (source instanceof EntityTypeSourceEntityType sourceEt) {
        if (sourceEt.getSourceField() != null) {
          assertThat(
            "Source " + source.getAlias() + "'s sourceField exists",
            flattened.getColumns(),
            hasItem(hasProperty("name", equalTo(sourceEt.getSourceField())))
          );
        }
        if (sourceEt.getTargetField() != null) {
          assertThat(
            "Source " + source.getAlias() + "'s targetField exists",
            flattened.getColumns(),
            hasItem(hasProperty("name", equalTo(sourceEt.getAlias() + "." + sourceEt.getTargetField())))
          );
        }
      }
    }
  }

  @Test
  void testFlattenSimpleEntityType() {
    EntityType expectedEntityType = new EntityType()
      .name("simple_1")
      .id(SIMPLE_1.getId())
      .columns(
        List.of(
          new EntityTypeColumn()
            .name("field1")
            .valueGetter("\"source1\".field1")
            .dataType(new StringType().dataType("stringType"))
            .isIdColumn(true),
          new EntityTypeColumn()
            .name("field2")
            .valueGetter("\"source1\".field2")
            .filterValueGetter("lower(\"source1\".field2)")
            .dataType(new StringType().dataType("stringType")),
          new EntityTypeColumn()
            .name("object")
            .valueGetter("\"source1\".field2")
            .filterValueGetter("lower(\"source1\".field2)")
            .dataType(
              new ObjectType()
                .dataType("objectType")
                .addPropertiesItem(
                  new NestedObjectProperty()
                    .name("object_field1")
                    .dataType(new StringType().dataType("stringType"))
                    .valueGetter("\"source1\".object.object_field1")
                    .filterValueGetter("\"source1\".object.object_field1")
                )
                .addPropertiesItem(
                  new NestedObjectProperty()
                    .name("object_field2")
                    .dataType(new StringType().dataType("stringType"))
                    .valueGetter("\"source1\".object.object_field2")
                    .filterValueGetter("\"source1\".object.object_field2")
                )
            ),
          new EntityTypeColumn()
            .name("string_array_field")
            .valueGetter("\"source1\".string_array_field")
            .filterValueGetter("lower(\"source1\".string_array_field)")
            .dataType(new ArrayType().dataType("arrayType").itemDataType(new StringType().dataType("stringType"))),
          new EntityTypeColumn()
            .name("nested_string_array_field")
            .valueGetter("\"source1\".nested_string_array_field")
            .filterValueGetter("lower(\"source1\".nested_string_array_field)")
            .dataType(
              new ArrayType()
                .dataType("arrayType")
                .itemDataType(
                  new ArrayType().dataType("arrayType").itemDataType(new StringType().dataType("stringType"))
                )
            ),
          new EntityTypeColumn()
            .name("object_array_field")
            .valueGetter("\"source1\".object_array_field")
            .filterValueGetter("lower(\"source1\".object_array_field)")
            .dataType(
              new ArrayType()
                .dataType("arrayType")
                .itemDataType(
                  new ObjectType()
                    .dataType("objectType")
                    .addPropertiesItem(
                      new NestedObjectProperty()
                        .name("array_object_field1")
                        .dataType(new StringType().dataType("stringType"))
                        .valueGetter("\"source1\".array_object.array_object_field1")
                        .filterValueGetter("\"source1\".array_object.array_object_field1")
                    )
                    .addPropertiesItem(
                      new NestedObjectProperty()
                        .name("array_object_field2")
                        .dataType(new StringType().dataType("stringType"))
                        .valueGetter("\"source1\".array_object.array_object_field2")
                        .filterValueGetter("\"source1\".array_object.object_field2")
                    )
                )
            )
        )
      )
      .sources(List.of(new EntityTypeSourceDatabase().type("db").alias("source1").target("source1_target")))
      .requiredPermissions(List.of("simple_permission1", "simple_permission2"))
      .sourceViewExtractor("\"source1\".some_view_extractor")
      .filterConditions(List.of("\"source1\".field1 != 'xyz'"))
      .putAdditionalProperty("isCustom", false);

    when(userTenantService.getUserTenantsResponse(TENANT_ID)).thenReturn("{'totalRecords': 0}");

    EntityType actualEntityType = entityTypeFlatteningService.getFlattenedEntityType(
      UUID.fromString(SIMPLE_1.getId()),
      TENANT_ID,
      false
    );

    assertEquals(expectedEntityType, actualEntityType);
  }

  @Test
  void testFlattenCompositeOfSimple() {
    List<String> expectedFilterConditions = List.of(
      "\"simple_1.source1\".field2 != 'abc'",
      "\"simple_1.source1\".field1 != 'xyz'"
    );
    when(userTenantService.getUserTenantsResponse(TENANT_ID)).thenReturn("{'totalRecords': 0}");

    EntityType actual = entityTypeFlatteningService.getFlattenedEntityType(
      UUID.fromString(COMPOSITE_1_WRAPPER.getId()),
      TENANT_ID,
      false
    );

    assertThat(
      actual.getSources(),
      hasItem(
        allOf(
          hasProperty("alias", equalTo("simple_1.source1")),
          hasProperty("joinedViaEntityType", equalTo("simple_1"))
        )
      )
    );

    assertThat(actual.getRequiredPermissions(), contains(SIMPLE_1.getRequiredPermissions().toArray()));
    assertThat(actual.getColumns(), hasSize(6));
    for (EntityTypeColumn column : actual.getColumns()) {
      assertThat(column.getName(), startsWith("simple_1."));
      assertThat(column.getOriginalEntityTypeId(), is(UUID.fromString(SIMPLE_1.getId())));
      assertThat(column.getValueGetter(), stringContainsInOrder("\"simple_1.source1\""));
      assertThat(column.getFilterValueGetter(), is(anyOf(nullValue(), stringContainsInOrder("\"simple_1.source1\""))));
      assertThat(column.getValueFunction(), is(anyOf(nullValue(), stringContainsInOrder("\"simple_1.source1\""))));
    }
    assertEquals(expectedFilterConditions, actual.getFilterConditions());
  }

  @Test
  void testFlattenCompositeOfSimpleJoined() {
    when(userTenantService.getUserTenantsResponse(TENANT_ID)).thenReturn("{'totalRecords': 0}");

    EntityType actual = entityTypeFlatteningService.getFlattenedEntityType(
      UUID.fromString(COMPOSITE_1_TO_2.getId()),
      TENANT_ID,
      false
    );

    assertThat(
      actual.getSources(),
      hasItems(
        allOf(
          instanceOf(EntityTypeSourceEntityType.class),
          hasProperty("alias", equalTo("simple_1")),
          hasProperty("joinedViaEntityType", nullValue()),
          hasProperty("sourceField", equalTo("simple_2.fieldZ")),
          hasProperty("targetField", equalTo("field1"))
        ),
        allOf(
          instanceOf(EntityTypeSourceDatabase.class),
          hasProperty("alias", equalTo("simple_1.source1")),
          hasProperty("joinedViaEntityType", equalTo("simple_1"))
        ),
        allOf(
          instanceOf(EntityTypeSourceDatabase.class),
          hasProperty("alias", equalTo("simple_2.sourceZ")),
          hasProperty("joinedViaEntityType", equalTo("simple_2"))
        )
      )
    );

    assertThat(actual.getRequiredPermissions(), contains(SIMPLE_1.getRequiredPermissions().toArray()));
    assertThat(actual.getColumns(), hasSize(7));
    for (EntityTypeColumn column : actual.getColumns()) {
      if (column.getName().equals("simple_2.fieldZ")) {
        assertThat(column.getName(), startsWith("simple_2."));
        assertThat(column.getOriginalEntityTypeId(), is(UUID.fromString(SIMPLE_2.getId())));
        assertThat(column.getValueGetter(), stringContainsInOrder("\"simple_2.sourceZ\""));
        assertThat(
          column.getFilterValueGetter(),
          is(anyOf(nullValue(), stringContainsInOrder("\"simple_2.sourceZ\"")))
        );
        assertThat(column.getValueFunction(), is(anyOf(nullValue(), stringContainsInOrder("\"simple_2.sourceZ\""))));
      } else {
        assertThat(column.getName(), startsWith("simple_1."));
        assertThat(column.getOriginalEntityTypeId(), is(UUID.fromString(SIMPLE_1.getId())));
        assertThat(column.getValueGetter(), stringContainsInOrder("\"simple_1.source1\""));
        assertThat(
          column.getFilterValueGetter(),
          is(anyOf(nullValue(), stringContainsInOrder("\"simple_1.source1\"")))
        );
        assertThat(column.getValueFunction(), is(anyOf(nullValue(), stringContainsInOrder("\"simple_1.source1\""))));
      }
    }
  }

  @Test
  void testFlattenCompositeOfSimpleNestedJoined() {
    when(userTenantService.getUserTenantsResponse(TENANT_ID)).thenReturn("{'totalRecords': 0}");

    EntityType actual = entityTypeFlatteningService.getFlattenedEntityType(
      UUID.fromString(COMPOSITE_1W_TO_2W.getId()),
      TENANT_ID,
      false
    );

    assertThat(
      actual.getSources(),
      hasItems(
        allOf(
          instanceOf(EntityTypeSourceDatabase.class),
          hasProperty("alias", equalTo("composite_1.simple_1.source1")),
          hasProperty("joinedViaEntityType", equalTo("composite_1.simple_1"))
        ),
        allOf(
          instanceOf(EntityTypeSourceEntityType.class),
          hasProperty("alias", equalTo("composite_1.simple_1")),
          hasProperty("joinedViaEntityType", equalTo("composite_1"))
        ),
        allOf(
          instanceOf(EntityTypeSourceDatabase.class),
          hasProperty("alias", equalTo("composite_2.simple_2.sourceZ")),
          hasProperty("joinedViaEntityType", equalTo("composite_2.simple_2"))
        ),
        allOf(
          instanceOf(EntityTypeSourceEntityType.class),
          hasProperty("alias", equalTo("composite_2.simple_2")),
          hasProperty("joinedViaEntityType", equalTo("composite_2"))
        )
      )
    );

    assertThat(actual.getRequiredPermissions(), contains(SIMPLE_1.getRequiredPermissions().toArray()));
    assertThat(actual.getColumns(), hasSize(7));
    for (EntityTypeColumn column : actual.getColumns()) {
      if (column.getName().equals("composite_2.simple_2.fieldZ")) {
        assertThat(column.getName(), startsWith("composite_2.simple_2."));
        assertThat(column.getOriginalEntityTypeId(), is(UUID.fromString(SIMPLE_2.getId())));
        assertThat(column.getValueGetter(), stringContainsInOrder("\"composite_2.simple_2.sourceZ\""));
        assertThat(
          column.getFilterValueGetter(),
          is(anyOf(nullValue(), stringContainsInOrder("\"composite_2.simple_2.sourceZ\"")))
        );
        assertThat(column.getValueFunction(), is(anyOf(nullValue(), stringContainsInOrder("\"simple_2.sourceZ\""))));
      } else {
        assertThat(column.getName(), startsWith("composite_1.simple_1."));
        assertThat(column.getOriginalEntityTypeId(), is(UUID.fromString(SIMPLE_1.getId())));
        assertThat(column.getValueGetter(), stringContainsInOrder("\"composite_1.simple_1.source1\""));
        assertThat(
          column.getFilterValueGetter(),
          is(anyOf(nullValue(), stringContainsInOrder("\"composite_1.simple_1.source1\"")))
        );
        assertThat(
          column.getValueFunction(),
          is(anyOf(nullValue(), stringContainsInOrder("\"composite_1.simple_1.source1\"")))
        );
      }
    }
  }

  @Test
  void testFlattenCompositeComposite() {
    when(userTenantService.getUserTenantsResponse(TENANT_ID)).thenReturn("{'totalRecords': 0}");

    EntityType actual = entityTypeFlatteningService.getFlattenedEntityType(
      UUID.fromString(COMPOSITE_COMPOSITE.getId()),
      TENANT_ID,
      false
    );

    assertThat(
      actual.getSources(),
      hasItems(
        allOf(
          instanceOf(EntityTypeSourceEntityType.class),
          hasProperty("alias", equalTo("composite_wrapper_to_wrapper.composite_2")),
          hasProperty("joinedViaEntityType", equalTo("composite_wrapper_to_wrapper")),
          hasProperty("sourceField", equalTo("composite_wrapper_to_wrapper.composite_1.simple_1.field1")),
          hasProperty("targetField", equalTo("simple_2.fieldZ"))
        ),
        allOf(
          instanceOf(EntityTypeSourceDatabase.class),
          hasProperty("alias", equalTo("composite_wrapper_to_wrapper.composite_1.simple_1.source1")),
          hasProperty("joinedViaEntityType", equalTo("composite_wrapper_to_wrapper.composite_1.simple_1"))
        ),
        allOf(
          instanceOf(EntityTypeSourceDatabase.class),
          hasProperty("alias", equalTo("composite_wrapper_to_wrapper.composite_2.simple_2.sourceZ")),
          hasProperty("joinedViaEntityType", equalTo("composite_wrapper_to_wrapper.composite_2.simple_2"))
        ),
        allOf(
          instanceOf(EntityTypeSourceEntityType.class),
          hasProperty("alias", equalTo("composite_1_to_2")),
          hasProperty("sourceField", equalTo("composite_wrapper_to_wrapper.composite_1.simple_1.field1")),
          hasProperty("targetField", equalTo("simple_2.fieldZ"))
        ),
        allOf(
          instanceOf(EntityTypeSourceEntityType.class),
          hasProperty("alias", equalTo("composite_1_to_2.simple_1")),
          hasProperty("joinedViaEntityType", equalTo("composite_1_to_2")),
          hasProperty("sourceField", equalTo("composite_1_to_2.simple_2.fieldZ")),
          hasProperty("targetField", equalTo("field1"))
        ),
        allOf(
          instanceOf(EntityTypeSourceDatabase.class),
          hasProperty("alias", equalTo("composite_1_to_2.simple_1.source1")),
          hasProperty("joinedViaEntityType", equalTo("composite_1_to_2.simple_1"))
        ),
        allOf(
          instanceOf(EntityTypeSourceDatabase.class),
          hasProperty("alias", equalTo("composite_1_to_2.simple_2.sourceZ")),
          hasProperty("joinedViaEntityType", equalTo("composite_1_to_2.simple_2"))
        )
      )
    );

    assertThat(
      actual.getRequiredPermissions(),
      containsInAnyOrder("extra_perm", "simple_permission1", "simple_permission2")
    );
    assertThat(actual.getColumns(), hasSize(14));
  }

  @Test
  void testUnknownSourceType() {
    // java 21 switch statements would make this obsolete :(
    EntityType childEntityType = new EntityType()
      .id("f5ecbd82-ca25-5cfd-9a91-4d48dbe357e9")
      .sources(List.of(new EntityTypeSource() {}));

    UUID compositeId = UUID.fromString("334399a1-c7ed-53b6-b7d7-a9b05ccfee99");
    EntityType compositeEntityType = new EntityType()
      .id(compositeId.toString())
      .sources(List.of(new EntityTypeSourceEntityType().targetId(UUID.fromString(childEntityType.getId()))));

    when(entityTypeRepository.getEntityTypeDefinition(eq(UUID.fromString(childEntityType.getId())), any()))
      .thenReturn(Optional.of(childEntityType));
    when(entityTypeRepository.getEntityTypeDefinition(eq(compositeId), any()))
      .thenReturn(Optional.of(compositeEntityType));

    assertThrows(
      IllegalStateException.class,
      () -> entityTypeFlatteningService.getFlattenedEntityType(compositeId, TENANT_ID, false)
    );
  }

  @Test
  void testIncludeEcs() {
    when(userTenantService.getUserTenantsResponse(TENANT_ID)).thenReturn("{'totalRecords': 1}");

    EntityType actual = entityTypeFlatteningService.getFlattenedEntityType(
      UUID.fromString(COMPOSITE_1_WRAPPER.getId()),
      TENANT_ID,
      false
    );

    assertThat(actual.getColumns(), hasItem(hasProperty("name", equalTo("simple_1.ecs_field"))));
  }

  @Test
  void testFieldOrderInComposites() {
    when(userTenantService.getUserTenantsResponse(TENANT_ID)).thenReturn("{'totalRecords': 0}");

    List<EntityTypeColumn> actual = entityTypeFlatteningService.getFlattenedEntityType(
      UUID.fromString(COMPOSITE_1_TO_2.getId()),
      TENANT_ID,
      false
    ).getColumns();

    // In COMPOSITE_1_TO_2, simple_2 has "order: 1" - order is unset in simple_1
    // Therefore, fields from simple_2 should come before fields from simple_1 in the columns list
    String prev = "zzzzzzzz"; // Dummy value that is greater (as in string comparison) than any aliases in the ET
    for (EntityTypeColumn column : actual) {
      String alias = column.getName().split("\\.")[0];
      assertThat(prev.compareTo(alias), greaterThanOrEqualTo(0));
      prev = alias;
    }
  }

  @Test
  void testThrowsExceptionForNullTenantId() {
    UUID entityTypeId = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class, () -> entityTypeFlatteningService.getFlattenedEntityType(
      entityTypeId,
      null,
      false
    ));
  }

  @Test
  void testCustomFieldInheritanceFiltering() {
    EntityType sourceEntityWithCustomFields = new EntityType()
      .id("11111111-1111-1111-1111-111111111111")
      .name("source_with_custom_fields")
      .columns(List.of(
        new EntityTypeColumn()
          .name("regular_field_1")
          .dataType(new StringType().dataType("stringType"))
          .valueGetter(":source.regular_field"),
        new EntityTypeColumn()
          .name("custom_field")
          .dataType(new StringType().dataType("stringType"))
          .valueGetter(":source.custom_field")
          .isCustomField(true),
        new EntityTypeColumn()
          .name("regular_field_2")
          .dataType(new StringType().dataType("stringType"))
          .valueGetter(":source.regular_field_2")
          .isCustomField(false)
      ))
      .sources(List.of(
        new EntityTypeSourceDatabase().type("db").alias("source").target("source_table")
      ));

    // Should inherit custom fields
    EntityType compositeInheritsCustomFields = new EntityType()
      .id("22222222-2222-2222-2222-222222222222")
      .name("composite_inherits_custom_fields")
      .columns(List.of())
      .sources(List.of(
        new EntityTypeSourceEntityType()
          .type("entity-type")
          .alias("source_alias")
          .targetId(UUID.fromString(sourceEntityWithCustomFields.getId()))
          .inheritCustomFields(true)
      ));

    // Should not inherit custom fields
    EntityType compositeExcludesCustomFields = new EntityType()
      .id("33333333-3333-3333-3333-333333333333")
      .name("composite_excludes_custom_fields")
      .columns(List.of())
      .sources(List.of(
        new EntityTypeSourceEntityType()
          .type("entity-type")
          .alias("source_alias")
          .targetId(UUID.fromString(sourceEntityWithCustomFields.getId()))
          .inheritCustomFields(false)
      ));

    when(entityTypeRepository.getEntityTypeDefinition(eq(UUID.fromString(sourceEntityWithCustomFields.getId())), any()))
      .thenReturn(Optional.of(sourceEntityWithCustomFields));
    when(entityTypeRepository.getEntityTypeDefinition(eq(UUID.fromString(compositeInheritsCustomFields.getId())), any()))
      .thenReturn(Optional.of(compositeInheritsCustomFields));
    when(entityTypeRepository.getEntityTypeDefinition(eq(UUID.fromString(compositeExcludesCustomFields.getId())), any()))
      .thenReturn(Optional.of(compositeExcludesCustomFields));

    when(userTenantService.getUserTenantsResponse(TENANT_ID)).thenReturn("{'totalRecords': 0}");

    EntityType flattenedWithCustomFields = entityTypeFlatteningService.getFlattenedEntityType(
      UUID.fromString(compositeInheritsCustomFields.getId()),
      TENANT_ID,
      false
    );

    assertThat(flattenedWithCustomFields.getColumns(), hasSize(3));
    assertThat(flattenedWithCustomFields.getColumns(), hasItem(hasProperty("name", equalTo("source_alias.regular_field_1"))));
    assertThat(flattenedWithCustomFields.getColumns(), hasItem(hasProperty("name", equalTo("source_alias.regular_field_2"))));
    assertThat(flattenedWithCustomFields.getColumns(), hasItem(hasProperty("name", equalTo("source_alias.custom_field"))));

    EntityType flattenedWithoutCustomFields = entityTypeFlatteningService.getFlattenedEntityType(
      UUID.fromString(compositeExcludesCustomFields.getId()),
      TENANT_ID,
      false
    );

    assertThat(flattenedWithoutCustomFields.getColumns(), hasSize(2));
    assertThat(flattenedWithoutCustomFields.getColumns(), hasItem(hasProperty("name", equalTo("source_alias.regular_field_1"))));
    assertThat(flattenedWithoutCustomFields.getColumns(), hasItem(hasProperty("name", equalTo("source_alias.regular_field_2"))));
    assertThat(flattenedWithoutCustomFields.getColumns(), not(hasItem(hasProperty("name", equalTo("source_alias.custom_field")))));
  }

  @SneakyThrows
  private EntityType copyEntityType(EntityType originalEntityType) {
    ObjectMapper objectMapper = new ObjectMapper();
    String json = objectMapper.writeValueAsString(originalEntityType);
    return objectMapper.readValue(json, EntityType.class);
  }
}
