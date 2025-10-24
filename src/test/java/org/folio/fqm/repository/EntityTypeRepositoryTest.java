package org.folio.fqm.repository;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.querytool.domain.dto.BooleanType;
import org.folio.querytool.domain.dto.CustomFieldMetadata;
import org.folio.querytool.domain.dto.CustomFieldType;
import org.folio.querytool.domain.dto.DateType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeDefaultSort;
import org.folio.querytool.domain.dto.JsonbArrayType;
import org.folio.querytool.domain.dto.RangedUUIDType;
import org.folio.querytool.domain.dto.StringType;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.*;
import java.util.stream.Stream;

import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_JSONB_ARRAY_VALUE_GETTER;
import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_BOOLEAN_VALUES;
import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_PREPENDER;
import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_VALUE_GETTER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ActiveProfiles("db-test")
@SpringBootTest
class EntityTypeRepositoryTest {

  // Data pre-configured in postgres test container DB.
  private static final UUID ENTITY_TYPE_01_ID = UUID.fromString("0cb79a4c-f7eb-4941-a104-745224ae0291");
  private static final UUID ENTITY_TYPE_02_ID = UUID.fromString("0cb79a4c-f7eb-4941-a104-745224ae0292");

  @MockitoSpyBean
  private ObjectMapper objectMapper;

  @Autowired
  private EntityTypeRepository repo;

  @MockitoSpyBean
  private FolioExecutionContext folioExecutionContext;

  @BeforeEach
  void setUp() {
    // Clear cache before each test to ensure fresh data
    repo.clearCache();
    // Reset the mock object mapper to avoid side effects from previous tests
    reset(objectMapper);
  }


  @Test
  void shouldReturnValidEntityTypeDefinition() {
    Optional<EntityType> actualEntityTypeDefinition = repo.getEntityTypeDefinition(ENTITY_TYPE_01_ID, "");
    assertTrue(actualEntityTypeDefinition.isPresent());
  }

  @Test
  void shouldGetEntityTypeDefinitionWithCustomFields() {
    String genericValueGetter = "custom_fields_source_view.jsonb -> 'customFields' ->> '%s'";
    String booleanFilterValueGetter = "lower(%s)";
    String refId1 = "customColumn1";
    String refId2 = "customColumn2";
    String refId3 = "customColumn3";
    String refId4 = "customColumn4";
    String refId5 = "customColumn5";
    String refId6 = "customColumn6";
    String valueGetter1 = String.format(genericValueGetter, refId1);
    String valueGetter2 = String.format(CUSTOM_FIELD_VALUE_GETTER, null, "custom_fields_source_view", refId2, "custom_fields_source_view.jsonb -> 'customFields'", refId2);
    String valueGetter3 = String.format(CUSTOM_FIELD_VALUE_GETTER, null, "custom_fields_source_view", refId3, "custom_fields_source_view.jsonb -> 'customFields'", refId3);
    String valueGetter6 = String.format(CUSTOM_FIELD_JSONB_ARRAY_VALUE_GETTER, null, "custom_fields_source_view", refId6, "custom_fields_source_view.jsonb -> 'customFields'", refId6);
    var radioButtonValues = List.of(
      new ValueWithLabel().label("label1").value("opt1"),
      new ValueWithLabel().label("label2").value("opt2")
    );
    var singleSelectValues = List.of(
      new ValueWithLabel().label("label3").value("opt3"),
      new ValueWithLabel().label("label4").value("opt4")
    );
    var multiSelectValues = List.of(
      new ValueWithLabel().label("multi1").value("opt1"),
      new ValueWithLabel().label("multi2").value("opt2")
    );
    List<EntityTypeColumn> expectedColumns = List.of(
      new EntityTypeColumn()
        .name("id")
        .dataType(new RangedUUIDType().dataType("rangedUUIDType"))
        .labelAlias("Entity Type ID")
        .visibleByDefault(false),
      new EntityTypeColumn()
        .name("column-01")
        .dataType(new RangedUUIDType().dataType("rangedUUIDType"))
        .labelAlias("Column 1")
        .visibleByDefault(false),
      new EntityTypeColumn()
        .name("column-02")
        .dataType(new StringType().dataType("stringType"))
        .labelAlias("Column 2")
        .visibleByDefault(true),
      new EntityTypeColumn()
        .name(CUSTOM_FIELD_PREPENDER + "2c4e9797-422f-4962-a302-174af09b23f8")
        .dataType(new BooleanType().dataType("booleanType"))
        .valueGetter(valueGetter1)
        .filterValueGetter(String.format(booleanFilterValueGetter, valueGetter1))
        .valueFunction(String.format(booleanFilterValueGetter, ":value"))
        .labelAlias("custom_column_1")
        .values(CUSTOM_FIELD_BOOLEAN_VALUES)
        .visibleByDefault(false)
        .queryable(true)
        .essential(true)
        .isCustomField(true),
      new EntityTypeColumn()
        .name(CUSTOM_FIELD_PREPENDER + "2c4e9797-422f-4962-a302-174af09b23f9")
        .dataType(new StringType().dataType("stringType"))
        .valueGetter(valueGetter2)
        .filterValueGetter(String.format(genericValueGetter, refId2))
        .labelAlias("custom_column_2")
        .values(radioButtonValues)
        .visibleByDefault(false)
        .queryable(true)
        .essential(true)
        .isCustomField(true),
      new EntityTypeColumn()
        .name(CUSTOM_FIELD_PREPENDER + "2c4e9797-422f-4962-a302-174af09b23fa")
        .dataType(new StringType().dataType("stringType"))
        .valueGetter(valueGetter3)
        .filterValueGetter(String.format(genericValueGetter, refId3))
        .labelAlias("custom_column_3")
        .values(singleSelectValues)
        .visibleByDefault(false)
        .queryable(true)
        .essential(true)
        .isCustomField(true),
      new EntityTypeColumn()
        .name(CUSTOM_FIELD_PREPENDER + "2c4e9797-422f-4962-a302-174af09b23fb")
        .dataType(new StringType().dataType("stringType"))
        .valueGetter(String.format(genericValueGetter, refId4))
        .labelAlias("custom_column_4")
        .visibleByDefault(false)
        .queryable(true)
        .essential(true)
        .isCustomField(true),
      new EntityTypeColumn()
        .name(CUSTOM_FIELD_PREPENDER + "2c4e9797-422f-4962-a302-174af09b23fc")
        .dataType(new StringType().dataType("stringType"))
        .valueGetter(String.format(genericValueGetter, refId5))
        .labelAlias("custom_column_5")
        .visibleByDefault(false)
        .queryable(true)
        .essential(true)
        .isCustomField(true),
      new EntityTypeColumn()
        .name(CUSTOM_FIELD_PREPENDER + "2c4e9797-422f-4962-a302-174af09b23fd")
        .dataType(new StringType().dataType("stringType"))
        .valueGetter(String.format(genericValueGetter, refId5))
        .labelAlias("custom_column_5 (2)")
        .visibleByDefault(false)
        .queryable(true)
        .essential(true)
        .isCustomField(true),
      new EntityTypeColumn()
        .name(CUSTOM_FIELD_PREPENDER + "2c4e9797-422f-4962-a302-174af09b23fe")
        .dataType(new StringType().dataType("stringType"))
        .valueGetter(String.format(genericValueGetter, refId5))
        .labelAlias("custom_column_5 (3)")
        .visibleByDefault(false)
        .queryable(true)
        .essential(true)
        .isCustomField(true),
      new EntityTypeColumn()
        .name(CUSTOM_FIELD_PREPENDER + "3c4e9797-422f-4962-a302-174af09b23fa")
        .dataType(new JsonbArrayType().dataType("jsonbArrayType").itemDataType(new StringType().dataType("stringType")))
        .valueGetter(valueGetter6)
        .filterValueGetter("custom_fields_source_view.jsonb -> 'customFields' -> 'customColumn6'")
        .labelAlias("custom_column_6")
        .values(multiSelectValues)
        .visibleByDefault(false)
        .queryable(true)
        .essential(true)
        .isCustomField(true),
      new EntityTypeColumn()
        .name(CUSTOM_FIELD_PREPENDER + "3c4e9797-422f-4962-a302-174af09b23fe")
        .dataType(new DateType().dataType("dateType"))
        .valueGetter("custom_fields_source_view.jsonb -> 'customFields' ->> 'customDateColumn'")
        .labelAlias("custom_date_column")
        .visibleByDefault(false)
        .queryable(true)
        .essential(true)
        .isCustomField(true)
    );
    EntityType expectedEntityType = new EntityType()
      .name("entity_type-02")
      .labelAlias("entity_type-02")
      .id(ENTITY_TYPE_02_ID.toString())
      ._private(false)
      .defaultSort(List.of(new EntityTypeDefaultSort().columnName("column-01").direction(EntityTypeDefaultSort.DirectionEnum.ASC)))
      .columns(expectedColumns);
    EntityType actualEntityType = repo.getEntityTypeDefinition(ENTITY_TYPE_02_ID, "").orElseThrow();
    assertEquals(expectedEntityType, actualEntityType);
  }

  @Test
  @SneakyThrows
  void getEntityTypeDefinitions_shouldHandleJsonProcessingErrors() {
    // Mock ObjectMapper to throw exception when reading value
    doThrow(new JsonProcessingException("Test JSON processing failure") {})
      .when(objectMapper).readValue(any(String.class), eq(EntityType.class));

    // This should return empty stream instead of throwing exception
    Stream<EntityType> result = repo.getEntityTypeDefinitions(Set.of(ENTITY_TYPE_01_ID), "");

    assertTrue(result.findAny().isEmpty());
  }

  @Test
  @SneakyThrows
  void getEntityTypeDefinitions_shouldHandleCustomFieldProcessingErrors() {
    EntityType entityTypeWithBadCustomField = new EntityType()
      .id(ENTITY_TYPE_02_ID.toString())
      .name("test-entity")
      .columns(List.of(
        new EntityTypeColumn()
          .name("custom_field_column")
          .dataType(new CustomFieldType()
            .dataType("customFieldType")
            .customFieldMetadata(new CustomFieldMetadata()
              .configurationView("non_existent_view")
              .dataExtractionPath("jsonb -> 'customFields'")
            )
          )
      ));

    doReturn(entityTypeWithBadCustomField)
      .when(objectMapper).readValue(
        argThat((String arg) -> arg.contains(ENTITY_TYPE_02_ID.toString())),
        eq(EntityType.class)
      );

    Optional<EntityType> entityType = repo.getEntityTypeDefinitions(Set.of(ENTITY_TYPE_02_ID), "").findAny();

    assertTrue(entityType.isPresent());
    assertTrue(entityType.get().getColumns().isEmpty());
  }

  @Test
  void getEntityTypeDefinitions_shouldFilterOutNullEntities() {
    // Test that the filter(Objects::nonNull) works correctly
    // This test ensures the existing behavior is maintained
    Optional<EntityType> validEntity = repo.getEntityTypeDefinition(ENTITY_TYPE_01_ID, "");
    assertTrue(validEntity.isPresent());

    // Test with non-existent entity ID
    Optional<EntityType> invalidEntity = repo.getEntityTypeDefinition(UUID.randomUUID(), "");
    assertTrue(invalidEntity.isEmpty());
  }

  @Test
  void shouldCreateRetrieveUpdateAndDeleteCustomEntityType() {
    // Generate a random UUID for the test
    UUID entityTypeId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    when(folioExecutionContext.getUserId()).thenReturn(owner);
    when(folioExecutionContext.getTenantId()).thenReturn("diku");
      // 1. Create a custom entity type
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(owner)
      .name("test-custom-entity")
      .labelAlias("Test Custom Entity")
      ._private(false)
      .sources(Collections.emptyList())
      .columns(Collections.emptyList());

    repo.createCustomEntityType(customEntityType);

    // 2. Retrieve and verify
    CustomEntityType retrievedEntityType = repo.getCustomEntityType(entityTypeId);

    assertNotNull(retrievedEntityType);
    assertEquals(entityTypeId.toString(), retrievedEntityType.getId());
    assertEquals("test-custom-entity", retrievedEntityType.getName());
    assertEquals("Test Custom Entity", retrievedEntityType.getLabelAlias());
    assertEquals(false, retrievedEntityType.getPrivate());

    // 3. Update the entity type
    CustomEntityType updatedEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .name("updated-custom-entity")
      .labelAlias("Updated Custom Entity")
      ._private(true)
      .sources(Collections.emptyList())
      .columns(Collections.emptyList());

    repo.updateEntityType(updatedEntityType);

    // 4. Retrieve and verify the update
    CustomEntityType retrievedUpdatedType = repo.getCustomEntityType(entityTypeId);

    assertNotNull(retrievedUpdatedType);
    assertEquals(entityTypeId.toString(), retrievedUpdatedType.getId());
    assertEquals("updated-custom-entity", retrievedUpdatedType.getName());
    assertEquals("Updated Custom Entity", retrievedUpdatedType.getLabelAlias());
    assertEquals(true, retrievedUpdatedType.getPrivate());

  }

  @Test
  @SneakyThrows
  void createCustomEntityType_shouldHandleInvalidJson() {
    // Since we can't easily create an unserializable object directly,
    // we'll test the exception path by mocking ObjectMapper only for this test
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .name("test-invalid-entity");

    when(objectMapper.writeValueAsString(any(CustomEntityType.class)))
      .thenThrow(new JsonParseException("Test JSON processing failure"));

    // This should throw an InvalidEntityTypeDefinitionException
    assertThrows(InvalidEntityTypeDefinitionException.class, () ->
      repo.createCustomEntityType(customEntityType));
  }

  @Test
  @SneakyThrows
  void updateEntityType_shouldHandleInvalidJson() {
    // Similar approach as above but for updating
    UUID entityTypeId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(owner)
      .name("test-invalid-update-entity");
    when(folioExecutionContext.getUserId()).thenReturn(owner);
    when(folioExecutionContext.getTenantId()).thenReturn("diku");

    // First create a valid entity
    repo.createCustomEntityType(customEntityType);

    // Use a spy on our actual ObjectMapper to cause a JsonProcessingException
    when(objectMapper.writeValueAsString(any(CustomEntityType.class)))
      .thenThrow(new JsonParseException("Test JSON processing failure"));

    // This should throw an InvalidEntityTypeDefinitionException
    assertThrows(InvalidEntityTypeDefinitionException.class, () ->
      repo.updateEntityType(customEntityType));
  }

  @Test
  void throwIfEntityTypeInvalid_shouldThrowForInvalidId() {
    EntityType invalidEntityType = new EntityType().id("not-a-uuid");
    InvalidEntityTypeDefinitionException ex = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> EntityTypeRepository.throwIfEntityTypeInvalid(invalidEntityType)
    );
    assertTrue(ex.getMessage().contains("Invalid entity type ID"));
    assertEquals("not-a-uuid", ex.getError().getParameters().get(0).getValue());
  }

  @Test
  void throwIfEntityTypeInvalid_shouldNotThrowForValidId() {
    EntityType validEntityType = new EntityType().id(UUID.randomUUID().toString());
    assertDoesNotThrow(() -> EntityTypeRepository.throwIfEntityTypeInvalid(validEntityType));
  }
}
