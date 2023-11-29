package org.folio.fqm.repository;

import org.folio.fqm.repository.EntityTypeRepository.RawEntityTypeSummary;
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
}
