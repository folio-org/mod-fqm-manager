package org.folio.fqm.service;

import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.testutil.TestDataFixture;
import org.folio.fqm.domain.dto.EntityTypeSummary;
import org.folio.querytool.domain.dto.ColumnValues;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.ValueSourceApi;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
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

  @Mock
  private SimpleHttpClient simpleHttpClient;

  @Mock
  private PermissionsService permissionsService;

  @Mock
  private EntityTypeFlatteningService entityTypeFlatteningService;

  @Mock
  private FolioExecutionContext executionContext;

  @InjectMocks
  private EntityTypeService entityTypeService;

  @Test
  void shouldGetEntityTypeDefinitionIncludingHidden() {
    UUID entityTypeId = UUID.randomUUID();

    List<EntityTypeColumn> columns = List.of(
      new EntityTypeColumn().name("A").labelAlias("A").hidden(true),
      new EntityTypeColumn().name("B").labelAlias("B").hidden(false),
      new EntityTypeColumn().name("C").labelAlias("C").hidden(true)
    );
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .columns(columns);

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null)).thenReturn(entityType);
    EntityType result = entityTypeService.getEntityTypeDefinition(entityTypeId, true);
    List<EntityTypeColumn> expectedColumns = columns.stream()
      .sorted(nullsLast(comparing(EntityTypeColumn::getLabelAlias, String.CASE_INSENSITIVE_ORDER)))
      .toList();

    assertEquals(expectedColumns, result.getColumns(), "Columns should include hidden ones and be sorted");

    verify(entityTypeFlatteningService, times(1)).getFlattenedEntityType(entityTypeId, null);
    verifyNoMoreInteractions(entityTypeFlatteningService);
  }


  @Test
  void shouldGetEntityTypeSummaryForValidIds() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Set<UUID> ids = Set.of(id1, id2);
    List<EntityTypeSummary> expectedSummary = List.of(
      new EntityTypeSummary().id(id1).label("label_01"),
      new EntityTypeSummary().id(id2).label("label_02"));

    when(repo.getEntityTypeDefinitions(ids, null)).thenReturn(Stream.of(
      new EntityType(id1.toString(), "translation_label_01", true, false),
      new EntityType(id2.toString(), "translation_label_02", true, false)));
    when(localizationService.getEntityTypeLabel("translation_label_01")).thenReturn("label_01");
    when(localizationService.getEntityTypeLabel("translation_label_02")).thenReturn("label_02");

    List<EntityTypeSummary> actualSummary = entityTypeService.getEntityTypeSummary(ids, false);

    assertEquals(expectedSummary, actualSummary, "Expected Summary should equal Actual Summary");

    verify(repo, times(1)).getEntityTypeDefinitions(ids, null);

    verify(localizationService, times(1)).getEntityTypeLabel("translation_label_01");
    verify(localizationService, times(1)).getEntityTypeLabel("translation_label_02");

    verifyNoMoreInteractions(repo, localizationService);
  }

  @Test
  void testEntityTypeSummaryDoesNotIncludeInaccessibleWhenNotRequested() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Set<UUID> ids = Set.of(id1, id2);
    List<EntityTypeSummary> expectedSummary = List.of(new EntityTypeSummary().id(id2).label("label_02"));

    when(repo.getEntityTypeDefinitions(ids, null)).thenReturn(Stream.of(
      new EntityType(id1.toString(), "translation_label_01", true, false).requiredPermissions(List.of("perm1")),
      new EntityType(id2.toString(), "translation_label_02", true, false).requiredPermissions(List.of("perm2"))));
    when(permissionsService.getUserPermissions()).thenReturn(Set.of("perm2"));
    when(permissionsService.getRequiredPermissions(any(EntityType.class)))
      .then(invocationOnMock -> new HashSet<>(invocationOnMock.<EntityType>getArgument(0).getRequiredPermissions()));
    when(localizationService.getEntityTypeLabel("translation_label_02")).thenReturn("label_02");

    List<EntityTypeSummary> actualSummary = entityTypeService.getEntityTypeSummary(ids, false);

    assertEquals(expectedSummary, actualSummary, "Expected Summary should equal Actual Summary");

    verify(repo, times(1)).getEntityTypeDefinitions(ids, null);

    verify(localizationService, times(1)).getEntityTypeLabel("translation_label_02");

    verifyNoMoreInteractions(repo, localizationService);
  }


  @Test
  void testEntityTypeSummaryIncludesInaccessible() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Set<UUID> ids = Set.of(id1, id2);
    List<EntityTypeSummary> expectedSummary = List.of(
      new EntityTypeSummary().id(id1).label("label_01").missingPermissions(List.of("perm1")),
      new EntityTypeSummary().id(id2).label("label_02").missingPermissions(List.of()));

    when(repo.getEntityTypeDefinitions(ids, null)).thenReturn(Stream.of(
      new EntityType(id1.toString(), "translation_label_01", true, false).requiredPermissions(List.of("perm1")),
      new EntityType(id2.toString(), "translation_label_02", true, false).requiredPermissions(List.of("perm2"))));
    when(permissionsService.getUserPermissions()).thenReturn(Set.of("perm2"));
    when(permissionsService.getRequiredPermissions(any(EntityType.class)))
      .then(invocationOnMock -> new HashSet<>(invocationOnMock.<EntityType>getArgument(0).getRequiredPermissions()));
    when(localizationService.getEntityTypeLabel("translation_label_01")).thenReturn("label_01");
    when(localizationService.getEntityTypeLabel("translation_label_02")).thenReturn("label_02");

    List<EntityTypeSummary> actualSummary = entityTypeService.getEntityTypeSummary(ids, true);

    assertEquals(expectedSummary, actualSummary, "Expected Summary should equal Actual Summary");

    verify(repo, times(1)).getEntityTypeDefinitions(ids, null);

    verify(localizationService, times(1)).getEntityTypeLabel("translation_label_01");
    verify(localizationService, times(1)).getEntityTypeLabel("translation_label_02");

    verifyNoMoreInteractions(repo, localizationService);
  }

  @Test
  void shouldGetValueWithLabel() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("whatever")
      .columns(List.of(new EntityTypeColumn().name(valueColumnName)));

    ColumnValues expectedColumnValueLabel = new ColumnValues()
      .content(
        List.of(
          new ValueWithLabel().value("value_01").label("label_01"),
          new ValueWithLabel().value("value_02").label("label_02")
        )
      );

    when(queryProcessorService.processQuery(any(), any(), any(), any(), any()))
      .thenReturn(
        List.of(
          Map.of("id", "value_01", valueColumnName, "label_01"),
          Map.of("id", "value_02", valueColumnName, "label_02")
        )
      );
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null)).thenReturn(entityType);

    ColumnValues actualColumnValueLabel = entityTypeService.getFieldValues(entityTypeId, valueColumnName, "");
    assertEquals(expectedColumnValueLabel, actualColumnValueLabel);
  }

  @Test
  void shouldReturnValueAsLabelIfIdColumnDoNotExist() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("the entity type")
      .columns(List.of(new EntityTypeColumn().name(valueColumnName)));

    when(queryProcessorService.processQuery(eq(entityTypeId), any(), any(), any(), any()))
      .thenReturn(
        List.of(
          Map.of(valueColumnName, "value_01"),
          Map.of(valueColumnName, "value_02")
        )
      );
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null)).thenReturn(entityType);

    ColumnValues expectedColumnValues = new ColumnValues().content(
      List.of(
        new ValueWithLabel().value("value_01").label("value_01"),
        new ValueWithLabel().value("value_02").label("value_02")
      ));
    ColumnValues actualColumnValueLabel = entityTypeService.getFieldValues(entityTypeId, valueColumnName, "");

    assertEquals(expectedColumnValues, actualColumnValueLabel);
  }

  @Test
  void shouldFilterBySearchText() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    List<String> fields = List.of("id", valueColumnName);
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("this is a thing")
      .columns(List.of(new EntityTypeColumn().name(valueColumnName)));
    String searchText = "search text";
    String expectedFql = "{\"" + valueColumnName + "\": {\"$regex\": " + "\"" + searchText + "\"}}";
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null)).thenReturn(entityType);
    entityTypeService.getFieldValues(entityTypeId, valueColumnName, searchText);
    verify(queryProcessorService).processQuery(entityTypeId, expectedFql, fields, null, 1000);
  }

  @Test
  void shouldHandleNullSearchText() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("yep")
      .columns(List.of(new EntityTypeColumn().name(valueColumnName)));
    List<String> fields = List.of("id", valueColumnName);
    String expectedFql = "{\"" + valueColumnName + "\": {\"$regex\": " + "\"\"}}";
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null)).thenReturn(entityType);
    entityTypeService.getFieldValues(entityTypeId, valueColumnName, null);
    verify(queryProcessorService).processQuery(entityTypeId, expectedFql, fields, null, 1000);
  }

  @Test
  void shouldReturnPredefinedValues() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    List<ValueWithLabel> values = List.of(
      new ValueWithLabel().value("value_01").label("value_01"),
      new ValueWithLabel().value("value_02").label("value_02")
    );
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("the entity type")
      .columns(List.of(new EntityTypeColumn().name(valueColumnName).values(values)));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null)).thenReturn(entityType);

    ColumnValues actualColumnValueLabel = entityTypeService.getFieldValues(entityTypeId, valueColumnName, "");
    assertEquals(new ColumnValues().content(values), actualColumnValueLabel);
  }

  @Test
  void shouldRemoveDuplicatePredefinedValues() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    List<ValueWithLabel> values = List.of(
      new ValueWithLabel().value("value_01").label("value_01"),
      new ValueWithLabel().value("value_01").label("value_01")
    );
    List<ValueWithLabel> expectedValues = List.of(
      new ValueWithLabel().value("value_01").label("value_01")
    );
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("the entity type")
      .columns(List.of(new EntityTypeColumn().name(valueColumnName).values(values)));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null)).thenReturn(entityType);

    ColumnValues actualColumnValueLabel = entityTypeService.getFieldValues(entityTypeId, valueColumnName, "");
    assertEquals(new ColumnValues().content(expectedValues), actualColumnValueLabel);
  }

  @Test
  void shouldReturnValuesFromApi() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("the entity type")
      .columns(List.of(new EntityTypeColumn()
        .name(valueColumnName)
        .valueSourceApi(new ValueSourceApi()
          .path("fake-path")
          .valueJsonPath("$.what.ever.dude.*.theValue") // Approach 1: Explicitly use the full path
          .labelJsonPath("$..theLabel") // Approach 2: Just dive all the way down and find everything with this key
        )
      ));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null)).thenReturn(entityType);
    when(simpleHttpClient.get(eq("fake-path"), anyMap())).thenReturn("""
           {
             "what": {
               "ever": {
                 "dude": [
                   {
                     "theValue": "who",
                     "theLabel": "cares?"
                   },
                   {
                     "theValue": "so",
                     "theLabel": "lame"
                   },
                   {
                     "theValue": "yeah",
                     "theLabel": "right"
                   }
                 ]
               }
             }
           }
      """);

    ColumnValues actualColumnValueLabel = entityTypeService.getFieldValues(entityTypeId, valueColumnName, "r");

    ColumnValues expectedColumnValues = new ColumnValues().content(List.of(
      new ValueWithLabel().value("who").label("cares?"),
      new ValueWithLabel().value("yeah").label("right")
    ));
    assertEquals(expectedColumnValues, actualColumnValueLabel);
  }

  @Test
  void shouldReturnEntityTypeDefinition() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType expectedEntityType = TestDataFixture.getEntityDefinition();

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null))
      .thenReturn(expectedEntityType);

    EntityType actualDefinition = entityTypeService
      .getEntityTypeDefinition(entityTypeId, false);

    assertEquals(expectedEntityType, actualDefinition);
  }

  @Test
  void shouldReturnCurrencies() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "pol_currency";
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("currency-test")
      .columns(List.of(new EntityTypeColumn()
        .name("pol_currency")
      ));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null)).thenReturn(entityType);

    List<ValueWithLabel> actualColumnValues = entityTypeService
      .getFieldValues(entityTypeId, valueColumnName, "")
      .getContent();

    // Check that a few known currencies are present
    assertTrue(actualColumnValues.contains(new ValueWithLabel().value("USD").label("US Dollar (USD)")));
    assertTrue(actualColumnValues.contains(new ValueWithLabel().value("INR").label("Indian Rupee (INR)")));
    assertTrue(actualColumnValues.contains(new ValueWithLabel().value("AMD").label("Armenian Dram (AMD)")));
    assertTrue(actualColumnValues.contains(new ValueWithLabel().value("GEL").label("Georgian Lari (GEL)")));
  }
}
