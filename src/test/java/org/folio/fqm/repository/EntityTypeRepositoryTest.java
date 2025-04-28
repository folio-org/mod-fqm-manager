package org.folio.fqm.repository;

import org.folio.querytool.domain.dto.BooleanType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeDefaultSort;
import org.folio.querytool.domain.dto.RangedUUIDType;
import org.folio.querytool.domain.dto.StringType;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_BOOLEAN_VALUES;
import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_PREPENDER;
import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_VALUE_GETTER;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("db-test")
@SpringBootTest
class EntityTypeRepositoryTest {

  // Data pre-configured in postgres test container DB.
  private static final UUID ENTITY_TYPE_01_ID = UUID.fromString("0cb79a4c-f7eb-4941-a104-745224ae0291");
  private static final UUID ENTITY_TYPE_02_ID = UUID.fromString("0cb79a4c-f7eb-4941-a104-745224ae0292");
  private static final UUID CUSTOM_FIELD_ENTITY_TYPE_ID = UUID.fromString("0cb79a4c-f7eb-4941-a104-745224ae0294");

  @Autowired
  private EntityTypeRepository repo;

  @Mock
  @Qualifier("readerJooqContext") private DSLContext jooqContext;

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
    String valueGetter1 = String.format(genericValueGetter, refId1);
    String valueGetter2 = String.format(CUSTOM_FIELD_VALUE_GETTER, null, "custom_fields_source_view", refId2, "custom_fields_source_view.jsonb -> 'customFields'", refId2);
    String valueGetter3 = String.format(CUSTOM_FIELD_VALUE_GETTER, null, "custom_fields_source_view", refId3, "custom_fields_source_view.jsonb -> 'customFields'", refId3);
    var radioButtonValues = List.of(
      new ValueWithLabel().label("label1").value("opt1"),
      new ValueWithLabel().label("label2").value("opt2")
    );
    var singleSelectValues = List.of(
      new ValueWithLabel().label("label3").value("opt3"),
      new ValueWithLabel().label("label4").value("opt4")
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
        .valueGetter(valueGetter1 )
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
        .isCustomField(true)
    );
    EntityType expectedEntityType = new EntityType()
      .name("entity_type-02")
      .labelAlias("entity_type-02")
      .id(ENTITY_TYPE_02_ID.toString())
      ._private(false)
      .defaultSort(List.of(new EntityTypeDefaultSort().columnName("column-01").direction(EntityTypeDefaultSort.DirectionEnum.ASC)))
      .columns(expectedColumns)
      .customFieldEntityTypeId(CUSTOM_FIELD_ENTITY_TYPE_ID.toString());
    EntityType actualEntityType = repo.getEntityTypeDefinition(ENTITY_TYPE_02_ID, "").orElseThrow();
    assertEquals(expectedEntityType, actualEntityType);
  }
}
