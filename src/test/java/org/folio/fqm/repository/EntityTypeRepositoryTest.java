package org.folio.fqm.repository;

import org.folio.fqm.repository.EntityTypeRepository.RawEntityTypeSummary;
import org.folio.querytool.domain.dto.BooleanType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.jooq.*;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("db-test")
@SpringBootTest
class EntityTypeRepositoryTest {

  // Data pre-configured in postgres test container DB.
  private static final UUID ENTITY_TYPE_01_ID = UUID.fromString("0cb79a4c-f7eb-4941-a104-745224ae0291");
  private static final UUID ENTITY_TYPE_02_ID = UUID.fromString("0cb79a4c-f7eb-4941-a104-745224ae0292");
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
  void retrieveCustomFieldNamesForSingleCheckboxTest() {
    UUID entityTypeId = UUID.randomUUID();
    UUID customFieldEntityTypeId = UUID.randomUUID();
    String sourceViewName = "user_custom_fields";
    EntityTypeColumn entityTypeColumn1 = new EntityTypeColumn().name("name").dataType(new BooleanType()).visibleByDefault(false);
    ValueWithLabel trueValue = new ValueWithLabel().label("True").value("true");
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test_entity_type")
      .customFieldEntityTypeId(customFieldEntityTypeId.toString())
      .columns(List.of(entityTypeColumn1))
      .root(true);
    assertEquals(entityType.getColumns().size(), 1);
    Optional<EntityType> actualEntityType = repo.getEntityTypeDefinition(UUID.fromString(entityType.getId()));

    EntityType customEntityType = new EntityType().id(customFieldEntityTypeId.toString()).sourceView(sourceViewName);
    EntityTypeColumn customColumn = new EntityTypeColumn().name("name").visibleByDefault(false).valueGetter("src_users_users.jsonb-> 'customFields' ->> '" + "refId" + "'").values(List.of(trueValue));
    assertEquals(actualEntityType.get().getColumns().size(), 2);
  }

  @Test
  void retrieveDefaultFieldNamesForSingleCheckboxTest() {
    UUID entityTypeId = UUID.randomUUID();
    UUID customFieldEntityTypeId = UUID.randomUUID();
    EntityTypeColumn entityTypeColumn1 = new EntityTypeColumn().name("name").dataType(new BooleanType()).visibleByDefault(false);
    ValueWithLabel trueValue = new ValueWithLabel().label("True").value("true");
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test_entity_type")
      .customFieldEntityTypeId(customFieldEntityTypeId.toString())
      .columns(List.of(entityTypeColumn1))
      .root(true);
    assertEquals(entityType.getColumns().size(), 1);
    Optional<EntityType> actualEntityType = repo.getEntityTypeDefinition(UUID.fromString(entityType.getId()));
    assertEquals(actualEntityType.get().getColumns().size(), 1);
  }
}
