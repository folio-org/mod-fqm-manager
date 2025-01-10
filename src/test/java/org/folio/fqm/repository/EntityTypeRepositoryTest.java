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
    String valueGetter1 = "custom_fields_source_view.jsonb -> 'customFields' ->> 'customColumn1'";
    String filterValueGetter2 = "custom_fields_source_view.jsonb -> 'customFields' ->> 'customColumn2'";
    String filterValueGetter3 = "custom_fields_source_view.jsonb -> 'customFields' ->> 'customColumn3'";
    String valueGetter2 = String.format(CUSTOM_FIELD_VALUE_GETTER, null, "custom_fields_source_view", "customColumn2", "custom_fields_source_view.jsonb -> 'customFields'", "customColumn2");
    String valueGetter3 = String.format(CUSTOM_FIELD_VALUE_GETTER, null, "custom_fields_source_view", "customColumn3", "custom_fields_source_view.jsonb -> 'customFields'", "customColumn3");
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
        .name("custom_column_1")
        .dataType(new BooleanType().dataType("booleanType"))
        .valueGetter(valueGetter1)
        .labelAlias("custom_column_1")
        .values(CUSTOM_FIELD_BOOLEAN_VALUES)
        .visibleByDefault(false)
        .queryable(true)
        .isCustomField(true),
      new EntityTypeColumn()
        .name("custom_column_2")
        .dataType(new StringType().dataType("stringType"))
        .valueGetter(valueGetter2)
        .filterValueGetter(filterValueGetter2)
        .labelAlias("custom_column_2")
        .values(radioButtonValues)
        .visibleByDefault(false)
        .queryable(true)
        .isCustomField(true),
      new EntityTypeColumn()
        .name("custom_column_3")
        .dataType(new StringType().dataType("stringType"))
        .valueGetter(valueGetter3)
        .filterValueGetter(filterValueGetter3)
        .labelAlias("custom_column_3")
        .values(singleSelectValues)
        .visibleByDefault(false)
        .queryable(true)
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
