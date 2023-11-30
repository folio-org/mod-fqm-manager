package org.folio.fqm.service;

import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.repository.EntityTypeRepository.RawEntityTypeSummary;
import org.folio.fqm.testutil.TestDataFixture;
import org.folio.fqm.domain.dto.EntityTypeSummary;
import org.folio.querytool.domain.dto.*;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class EntityTypeServiceTest {

  @Mock
  private EntityTypeRepository repo;

  @Mock
  private LocalizationService localizationService;

  @Mock
  private QueryProcessorService queryProcessorService;

  @InjectMocks
  private EntityTypeService entityTypeService;

  @Test
  void shouldGetEntityTypeSummaryForValidIds() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Set<UUID> ids = Set.of(id1, id2);
    List<EntityTypeSummary> expectedSummary = List.of(
      new EntityTypeSummary().id(id1).label("label_01"),
      new EntityTypeSummary().id(id2).label("label_02"));

    when(repo.getEntityTypeSummary(ids)).thenReturn(List.of(
      new RawEntityTypeSummary(id1, "translation_label_01"),
      new RawEntityTypeSummary(id2, "translation_label_02")));
    when(localizationService.getEntityTypeLabel("translation_label_01")).thenReturn("label_01");
    when(localizationService.getEntityTypeLabel("translation_label_02")).thenReturn("label_02");

    List<EntityTypeSummary> actualSummary = entityTypeService.getEntityTypeSummary(ids);

    assertEquals(expectedSummary, actualSummary, "Expected Summary should equal Actual Summary");

    verify(repo, times(1)).getEntityTypeSummary(ids);

    verify(localizationService, times(1)).getEntityTypeLabel("translation_label_01");
    verify(localizationService, times(1)).getEntityTypeLabel("translation_label_02");

    verifyNoMoreInteractions(repo, localizationService);
  }

  @Test
  void shouldGetValueWithLabel() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    List<String> fields = List.of("id", valueColumnName);

    ColumnValues expectedColumnValueLabel = new ColumnValues()
      .content(
        List.of(
          new ValueWithLabel().value("value_01").label("label_01"),
          new ValueWithLabel().value("value_02").label("label_02")
        )
      );
    String expectedFql = "{\"" + valueColumnName + "\": {\"$regex\": " + "\"\"}}";

    when(queryProcessorService.processQuery(entityTypeId, expectedFql, fields, null, 1000))
      .thenReturn(
        List.of(
          Map.of("id", "value_01", valueColumnName, "label_01"),
          Map.of("id", "value_02", valueColumnName, "label_02")
        )
      );

    ColumnValues actualColumnValueLabel = entityTypeService.getColumnValues(entityTypeId, valueColumnName, "");
    assertEquals(expectedColumnValueLabel, actualColumnValueLabel);
  }

  @Test
  void shouldReturnValueAsLabelIfIdColumnDoNotExist() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    List<String> fields = List.of("id", valueColumnName);

    ColumnValues expectedColumnValueLabel = new ColumnValues()
      .content(
        List.of(
          new ValueWithLabel().value("value_01").label("value_01"),
          new ValueWithLabel().value("value_02").label("value_02")
        )
      );
    String expectedFql = "{\"" + valueColumnName + "\": {\"$regex\": " + "\"\"}}";

    when(queryProcessorService.processQuery(entityTypeId, expectedFql, fields, null, 1000))
      .thenReturn(
        List.of(
          Map.of(valueColumnName, "value_01"),
          Map.of(valueColumnName, "value_02")
        )
      );

    ColumnValues actualColumnValueLabel = entityTypeService.getColumnValues(entityTypeId, valueColumnName, "");
    assertEquals(expectedColumnValueLabel, actualColumnValueLabel);
  }

  @Test
  void shouldFilterBySearchText() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    List<String> fields = List.of("id", valueColumnName);
    String searchText = "search text";
    String expectedFql = "{\"" + valueColumnName + "\": {\"$regex\": " + "\"" + searchText + "\"}}";

    entityTypeService.getColumnValues(entityTypeId, valueColumnName, searchText);
    verify(queryProcessorService).processQuery(entityTypeId, expectedFql, fields, null, 1000);
  }

  @Test
  void shouldHandleNullSearchText() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    List<String> fields = List.of("id", valueColumnName);
    String expectedFql = "{\"" + valueColumnName + "\": {\"$regex\": " + "\"\"}}";

    entityTypeService.getColumnValues(entityTypeId, valueColumnName, null);
    verify(queryProcessorService).processQuery(entityTypeId, expectedFql, fields, null, 1000);
  }

  @Test
  void shouldReturnEntityTypeDefinition() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType expectedEntityType = TestDataFixture.getEntityDefinition();

    when(repo.getEntityTypeDefinition(entityTypeId))
      .thenReturn(Optional.of(expectedEntityType));
    EntityType actualDefinition = entityTypeService
      .getEntityTypeDefinition(entityTypeId)
      .get();

    assertEquals(expectedEntityType, actualDefinition);
  }

  @Test
  void shouldReturnDerivedTableName() {
    UUID entityTypeId = UUID.randomUUID();
    String derivedTableName = "derived_table_01";

    when(repo.getDerivedTableName(entityTypeId))
      .thenReturn(Optional.of(derivedTableName));

    String actualDerivedTableName = entityTypeService.getDerivedTableName(
      entityTypeId
    );
    assertEquals(derivedTableName, actualDerivedTableName);
  }
}
