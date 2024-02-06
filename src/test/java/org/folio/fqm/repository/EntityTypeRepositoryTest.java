package org.folio.fqm.repository;

import org.folio.fqm.repository.EntityTypeRepository.RawEntityTypeSummary;
import org.folio.querytool.domain.dto.*;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("db-test")
@SpringBootTest
class EntityTypeRepositoryTest {

  // Data pre-configured in postgres test container DB.
  private static final UUID ENTITY_TYPE_01_ID = UUID.fromString("0cb79a4c-f7eb-4941-a104-745224ae0291");
  private static final UUID ENTITY_TYPE_02_ID = UUID.fromString("0cb79a4c-f7eb-4941-a104-745224ae0292");
  private static final UUID CUSTOM_FIELD_ENTITY_TYPE_ID = UUID.fromString("0cb79a4c-f7eb-4941-a104-745224ae0294");
  private static final String ENTITY_TYPE_01_LABEL = "entity_type-01";
  private static final String ENTITY_TYPE_02_LABEL = "entity_type-02";

  @Autowired
  private EntityTypeRepository repo;

  @Mock
  private DSLContext jooqContext;


  @Test
  void shouldFetchAllPublicEntityTypes() {
    List<RawEntityTypeSummary> expectedSummary = List.of(
      new RawEntityTypeSummary(ENTITY_TYPE_01_ID, ENTITY_TYPE_01_LABEL),
      new RawEntityTypeSummary(ENTITY_TYPE_02_ID, ENTITY_TYPE_02_LABEL)
    );

    List<RawEntityTypeSummary> actualSummary = repo.getEntityTypeSummary(Set.of());
    assertEquals(expectedSummary, actualSummary, "Expected Summary should equal Actual Summary");
  }

  @Test
  void shouldFetchEntityTypesOfGivenIds() {
    Set<UUID> ids = Set.of(ENTITY_TYPE_01_ID);
    List<RawEntityTypeSummary> expectedSummary = List.of(
      new RawEntityTypeSummary(ENTITY_TYPE_01_ID, ENTITY_TYPE_01_LABEL));

    List<RawEntityTypeSummary> actualSummary = repo.getEntityTypeSummary(ids);
    assertEquals(expectedSummary, actualSummary, "Expected Summary should equal Actual Summary");
  }

  @Test
  void shouldReturnValidDerivedTableName() {
    String actualTableName = repo.getDerivedTableName(ENTITY_TYPE_01_ID).get();
    assertEquals(ENTITY_TYPE_01_LABEL, actualTableName);
  }

  @Test
  void shouldReturnValidEntityTypeDefinition() {
    Optional<EntityType> actualEntityTypeDefinition = repo.getEntityTypeDefinition(ENTITY_TYPE_01_ID);
    assertTrue(actualEntityTypeDefinition.isPresent());
  }

  @Test
  void shouldGetEntityTypeDefinitionWithCustomFields() {
    String valueGetter1 = "src_users_users.jsonb -> 'customFields' ->> 'customColumn1'";
    String valueGetter2 = "src_users_users.jsonb -> 'customFields' ->> 'customColumn2'";
    var values = List.of(
      new ValueWithLabel().label("True").value("true"),
      new ValueWithLabel().label("False").value("false")
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
        .name("User custom_column_1")
        .dataType(new BooleanType())
        .valueGetter(valueGetter1)
        .labelAlias("custom_column_1")
        .values(values)
        .visibleByDefault(false)
        .isCustomField(true),
      new EntityTypeColumn()
        .name("User custom_column_2")
        .dataType(new BooleanType())
        .valueGetter(valueGetter2)
        .labelAlias("custom_column_2")
        .values(values)
        .visibleByDefault(false)
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
    EntityType actualEntityType = repo.getEntityTypeDefinition(ENTITY_TYPE_02_ID).orElseThrow();
    assertEquals(expectedEntityType, actualEntityType);
  }
}
