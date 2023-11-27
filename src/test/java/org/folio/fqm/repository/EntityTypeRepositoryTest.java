package org.folio.fqm.repository;

import org.folio.fqm.domain.dto.EntityTypeSummary;
import org.folio.querytool.domain.dto.EntityType;
import org.junit.jupiter.api.Test;
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
  private static final String ENTITY_TYPE_01_LABEL = "entity_type-01";
  private static final String ENTITY_TYPE_02_LABEL = "entity_type-02";

  @Autowired
  private EntityTypeRepository repo;

  @Test
  void shouldFetchAllPublicEntityTypes() {
    List<EntityTypeSummary> expectedSummary = List.of(
      new EntityTypeSummary().id(ENTITY_TYPE_01_ID).label(ENTITY_TYPE_01_LABEL),
      new EntityTypeSummary().id(ENTITY_TYPE_02_ID).label(ENTITY_TYPE_02_LABEL)
    );
    List<EntityTypeSummary> actualSummary = repo.getEntityTypeSummary(Set.of());
    assertEquals(expectedSummary, actualSummary, "Expected Summary should equal Actual Summary");
  }

  @Test
  void shouldFetchEntityTypesOfGivenIds() {
    Set<UUID> ids = Set.of(ENTITY_TYPE_01_ID);
    List<EntityTypeSummary> expectedSummary = List.of(
      new EntityTypeSummary().id(ENTITY_TYPE_01_ID).label(ENTITY_TYPE_01_LABEL));
    List<EntityTypeSummary> actualSummary = repo.getEntityTypeSummary(ids);
    assertEquals(expectedSummary, actualSummary, "Expected Summary should equal Actual Summary");
  }

  @Test
  void shouldReturnValidDerivedTableName() {
    String tenant = "beeuni";
    String actualTableName = repo.getDerivedTableName(tenant, ENTITY_TYPE_01_ID).get();
    assertEquals(tenant + "_mod_fqm_manager." + ENTITY_TYPE_01_LABEL, actualTableName);
  }

  @Test
  void shouldReturnValidEntityTypeDefinition() {
    UUID entityTypeId = UUID.randomUUID();
    Optional<EntityType> actualEntityTypeDefinition = repo.getEntityTypeDefinition("beeuni", entityTypeId);
    assertTrue(actualEntityTypeDefinition.isPresent());
  }
}
