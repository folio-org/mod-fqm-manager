package org.folio.fqm.repository;

import org.folio.fqm.repository.EntityTypeRepository.RawEntityTypeSummary;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.stream.Stream;


import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

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
  void fetchNamesForSingleCheckboxTest() {
    UUID id = UUID.randomUUID();
    when(repo.getDerivedTableName(id)).thenReturn(Optional.of("mockedTableName"));
    Result mockedResult = mock(Result.class);
    when(mockedResult.stream()).thenReturn(Stream.of(
      mockRecord("value1", "refId1"),
      mockRecord("value2", "refId2")
    ));

    // Adjusting the method call with any() if the parameters are UUID
    when(repo.fetchNamesForSingleCheckbox(any(UUID.class))).thenReturn(Collections.emptyList());

    // Calling the method
    List<EntityTypeColumn> result = repo.fetchNamesForSingleCheckbox(UUID.randomUUID());

    // Assertions
    assertEquals(2, result.size());
    assertEquals("value1", result.get(0).getValues().toString());
  }

  private Record2<Object, Object> mockRecord(Object value, Object refId) {
    Record2<Object, Object> record = mock(Record2.class);
    when(record.get(0)).thenReturn(value);
    when(record.get(1)).thenReturn(refId);
    return record;
  }

}
