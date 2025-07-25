package org.folio.fqm.service;

import feign.FeignException;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.fqm.client.CrossTenantHttpClient;
import org.folio.fqm.client.LanguageClient;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.domain.dto.EntityTypeSummary;
import org.folio.fqm.exception.CustomEntityTypeAccessDeniedException;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.testutil.TestDataFixture;
import org.folio.querytool.domain.dto.ColumnValues;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.querytool.domain.dto.SourceColumn;
import org.folio.querytool.domain.dto.ValueSourceApi;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntityTypeServiceTest {

  private static final String TENANT_ID = "tenant_01";

  @Mock
  private EntityTypeRepository repo;

  @Mock
  private LocalizationService localizationService;

  @Mock
  private QueryProcessorService queryProcessorService;

  @Mock
  private SimpleHttpClient simpleHttpClient;

  @Mock
  private CrossTenantHttpClient crossTenantHttpClient;

  @Mock
  private PermissionsService permissionsService;

  @Mock
  private EntityTypeFlatteningService entityTypeFlatteningService;

  @Mock
  private CrossTenantQueryService crossTenantQueryService;

  @Mock
  private LanguageClient languageClient;

  @Mock
  private FolioExecutionContext executionContext;

  @Spy
  private ClockService clockService;

  @Spy
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

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, false)).thenReturn(entityType);
    EntityType result = entityTypeService.getEntityTypeDefinition(entityTypeId, true);
    List<EntityTypeColumn> expectedColumns = columns.stream()
      .sorted(nullsLast(comparing(EntityTypeColumn::getLabelAlias, String.CASE_INSENSITIVE_ORDER)))
      .toList();

    assertEquals(expectedColumns, result.getColumns(), "Columns should include hidden ones and be sorted");

    verify(entityTypeFlatteningService, times(1)).getFlattenedEntityType(entityTypeId, null, false);
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

    var et1 = new EntityType(id1.toString(), "translation_label_01", false).crossTenantQueriesEnabled(true);
    var et2 = new EntityType(id2.toString(), "translation_label_02", false);

    when(repo.getEntityTypeDefinitions(ids, null)).thenReturn(Stream.of(et1, et2));
    when(localizationService.getEntityTypeLabel(et1)).thenReturn("label_01");
    when(localizationService.getEntityTypeLabel(et2)).thenReturn("label_02");

    List<EntityTypeSummary> actualSummary = entityTypeService.getEntityTypeSummary(ids, false, false);

    assertEquals(expectedSummary, actualSummary, "Expected Summary should equal Actual Summary");

    verify(repo, times(1)).getEntityTypeDefinitions(ids, null);

    verify(localizationService, times(1)).getEntityTypeLabel(et1);
    verify(localizationService, times(1)).getEntityTypeLabel(et2);

    verifyNoMoreInteractions(repo, localizationService);
  }

  @Test
  void shouldIncludeCrossTenantEntityTypesWhenInCentralTenant() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Set<UUID> ids = Set.of(id1, id2);
    List<EntityTypeSummary> expectedSummary = List.of(
      new EntityTypeSummary().id(id1).label("label_01").crossTenantQueriesEnabled(true),
      new EntityTypeSummary().id(id2).label("label_02"));
    var et1 = new EntityType(id1.toString(), "translation_label_01", false).crossTenantQueriesEnabled(true);
    var et2 = new EntityType(id2.toString(), "translation_label_02", false);

    when(repo.getEntityTypeDefinitions(ids, null)).thenReturn(Stream.of(et1, et2));
    when(localizationService.getEntityTypeLabel(et1)).thenReturn("label_01");
    when(localizationService.getEntityTypeLabel(et2)).thenReturn("label_02");
    when(crossTenantQueryService.isCentralTenant()).thenReturn(true);

    List<EntityTypeSummary> actualSummary = entityTypeService.getEntityTypeSummary(ids, false, false);
    assertEquals(expectedSummary, actualSummary);
  }

  @Test
  void testEntityTypeSummaryDoesNotIncludeInaccessibleWhenNotRequested() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Set<UUID> ids = Set.of(id1, id2);
    List<EntityTypeSummary> expectedSummary = List.of(new EntityTypeSummary().id(id2).label("label_02"));
    var et1 = new EntityType(id1.toString(), "translation_label_01", false).requiredPermissions(List.of("perm1"));
    var et2 = new EntityType(id2.toString(), "translation_label_02", false).requiredPermissions(List.of("perm2"));

    when(repo.getEntityTypeDefinitions(ids, null)).thenReturn(Stream.of(et1, et2));
    when(permissionsService.getUserPermissions()).thenReturn(Set.of("perm2"));
    when(permissionsService.getRequiredPermissions(any(EntityType.class)))
      .then(invocationOnMock -> new HashSet<>(invocationOnMock.<EntityType>getArgument(0).getRequiredPermissions()));
    when(localizationService.getEntityTypeLabel(et2)).thenReturn("label_02");

    List<EntityTypeSummary> actualSummary = entityTypeService.getEntityTypeSummary(ids, false, false);

    assertEquals(expectedSummary, actualSummary, "Expected Summary should equal Actual Summary");

    verify(repo, times(1)).getEntityTypeDefinitions(ids, null);

    verify(localizationService, times(1)).getEntityTypeLabel(et2);

    verifyNoMoreInteractions(repo, localizationService);
  }

  @Test
  void testEntityTypeSummaryIncludesAllWhenRequested() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Set<UUID> ids = Set.of(id1, id2);

    List<EntityTypeSummary> expectedSummary = List.of(
      new EntityTypeSummary().id(id1).label("label_01"),
      new EntityTypeSummary().id(id2).label("label_02")
    );
    var et1 = new EntityType(id1.toString(), "translation_label_01", true).requiredPermissions(List.of("perm1")); // Private entity
    var et2 = new EntityType(id2.toString(), "translation_label_02", true).requiredPermissions(List.of("perm2")); // Non-private entity

    when(repo.getEntityTypeDefinitions(ids, null)).thenReturn(Stream.of(et1, et2));

    when(permissionsService.getUserPermissions()).thenReturn(Set.of("perm2", "perm1"));
    when(permissionsService.getRequiredPermissions(any(EntityType.class)))
      .then(invocationOnMock -> new HashSet<>(invocationOnMock.<EntityType>getArgument(0).getRequiredPermissions()));

    when(localizationService.getEntityTypeLabel(et1)).thenReturn("label_01");
    when(localizationService.getEntityTypeLabel(et2)).thenReturn("label_02");

    List<EntityTypeSummary> actualSummary = entityTypeService.getEntityTypeSummary(ids, false, true);

    assertEquals(expectedSummary, actualSummary, "Expected Summary should equal Actual Summary");

    verify(repo, times(1)).getEntityTypeDefinitions(ids, null);
    verify(localizationService, times(1)).getEntityTypeLabel(et1);
    verify(localizationService, times(1)).getEntityTypeLabel(et2);
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
    var et1 = new EntityType(id1.toString(), "translation_label_01", false).requiredPermissions(List.of("perm1"));
    var et2 = new EntityType(id2.toString(), "translation_label_02", false).requiredPermissions(List.of("perm2"));

    when(repo.getEntityTypeDefinitions(ids, null)).thenReturn(Stream.of(et1, et2));
    when(permissionsService.getUserPermissions()).thenReturn(Set.of("perm2"));
    when(permissionsService.getRequiredPermissions(any(EntityType.class)))
      .then(invocationOnMock -> new HashSet<>(invocationOnMock.<EntityType>getArgument(0).getRequiredPermissions()));
    when(localizationService.getEntityTypeLabel(et1)).thenReturn("label_01");
    when(localizationService.getEntityTypeLabel(et2)).thenReturn("label_02");

    List<EntityTypeSummary> actualSummary = entityTypeService.getEntityTypeSummary(ids, true, false);

    assertEquals(expectedSummary, actualSummary, "Expected Summary should equal Actual Summary");

    verify(repo, times(1)).getEntityTypeDefinitions(ids, null);

    verify(localizationService, times(1)).getEntityTypeLabel(et1);
    verify(localizationService, times(1)).getEntityTypeLabel(et2);

    verifyNoMoreInteractions(repo, localizationService);
  }

  @Test
  void shouldGetValueWithLabel() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("whatever")
      .columns(List.of(new EntityTypeColumn().name(valueColumnName)
        .source(new SourceColumn(entityTypeId, valueColumnName)
          .type(SourceColumn.TypeEnum.ENTITY_TYPE))));

    ColumnValues expectedColumnValueLabel = new ColumnValues()
      .content(
        List.of(
          new ValueWithLabel().value("value_01").label("label_01"),
          new ValueWithLabel().value("value_02").label("label_02")
        )
      );

    when(queryProcessorService.processQuery(any(), any(), any(), any()))
      .thenReturn(
        List.of(
          Map.of("id", "value_01", valueColumnName, "label_01"),
          Map.of("id", "value_02", valueColumnName, "label_02")
        )
      );
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, false)).thenReturn(entityType);

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
      .columns(List.of(new EntityTypeColumn().name(valueColumnName)
        .source(new SourceColumn(entityTypeId, valueColumnName)
          .type(SourceColumn.TypeEnum.ENTITY_TYPE))));

    when(queryProcessorService.processQuery(any(EntityType.class), any(), any(), any()))
      .thenReturn(
        List.of(
          Map.of(valueColumnName, "value_01"),
          Map.of(valueColumnName, "value_02")
        )
      );
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, false)).thenReturn(entityType);

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
      .columns(List.of(new EntityTypeColumn().name(valueColumnName)
        .source(new SourceColumn(entityTypeId, valueColumnName)
          .type(SourceColumn.TypeEnum.ENTITY_TYPE))));
    String searchText = "search text";
    String expectedFql = "{\"" + valueColumnName + "\": {\"$regex\": " + "\"" + searchText + "\"}}";
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, false)).thenReturn(entityType);
    entityTypeService.getFieldValues(entityTypeId, valueColumnName, searchText);
    verify(queryProcessorService).processQuery(entityType, expectedFql, fields, 1000);
  }

  @Test
  void shouldHandleNullSearchText() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("yep")
      .columns(List.of(new EntityTypeColumn().name(valueColumnName)
        .source(new SourceColumn(entityTypeId, valueColumnName)
          .type(SourceColumn.TypeEnum.ENTITY_TYPE))));
    List<String> fields = List.of("id", valueColumnName);
    String expectedFql = "{\"" + valueColumnName + "\": {\"$regex\": " + "\"\"}}";
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, false)).thenReturn(entityType);
    entityTypeService.getFieldValues(entityTypeId, valueColumnName, null);
    verify(queryProcessorService).processQuery(entityType, expectedFql, fields, 1000);
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

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, false)).thenReturn(entityType);

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

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, false)).thenReturn(entityType);

    ColumnValues actualColumnValueLabel = entityTypeService.getFieldValues(entityTypeId, valueColumnName, "");
    assertEquals(new ColumnValues().content(expectedValues), actualColumnValueLabel);
  }

  @Test
  void shouldReturnValuesFromApi() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    List<String> tenantList = List.of(TENANT_ID, "tenant_02");
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

    when(crossTenantQueryService.getTenantsToQueryForColumnValues(entityType)).thenReturn(tenantList);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, false)).thenReturn(entityType);
    when(crossTenantHttpClient.get(eq("fake-path"), anyMap(), eq(TENANT_ID))).thenReturn("""
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
    when(crossTenantHttpClient.get(eq("fake-path"), anyMap(), eq("tenant_02"))).thenThrow(FeignException.Unauthorized.class);

    ColumnValues actualColumnValueLabel = entityTypeService.getFieldValues(entityTypeId, valueColumnName, "r");

    ColumnValues expectedColumnValues = new ColumnValues().content(List.of(
      new ValueWithLabel().value("who").label("cares?"),
      new ValueWithLabel().value("yeah").label("right")
    ));
    assertEquals(expectedColumnValues, actualColumnValueLabel);
  }

  @Test
  void shouldReturnLanguagesFromApi() {
    UUID entityTypeId = UUID.randomUUID();
    List<String> tenantList = List.of(TENANT_ID);
    String valueColumnName = "languages";
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("the entity type")
      .columns(List.of(new EntityTypeColumn()
        .name(valueColumnName)
        .source(new SourceColumn(entityTypeId, valueColumnName)
          .name("languages")
          .type(SourceColumn.TypeEnum.FQM))
      ));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, false)).thenReturn(entityType);
    when(crossTenantQueryService.getTenantsToQueryForColumnValues(entityType)).thenReturn(tenantList);
    when(languageClient.get(TENANT_ID)).thenReturn("""
           {
             "facets": {
               "languages": {
                 "values": [
                   {
                     "id": "eng",
                     "value": "eng"
                   },
                   {
                     "id": "ger",
                     "value": "ger"
                   },
                   {
                     "id": "fre",
                     "value": "fre"
                   },
                   {
                     "id": "xyze",
                     "value": "xyze"
                   }
                 ]
               }
             }
           }
      """);

    ColumnValues actualColumnValueLabel = entityTypeService.getFieldValues(entityTypeId, valueColumnName, "e");

    ColumnValues expectedColumnValues = new ColumnValues().content(List.of(
      new ValueWithLabel().value("eng").label("English"),
      new ValueWithLabel().value("fre").label("French"),
      new ValueWithLabel().value("ger").label("German"),
      new ValueWithLabel().value("xyze").label("xyze") // non-existent language code should use code as display name
    ));
    assertEquals(expectedColumnValues, actualColumnValueLabel);
  }

  @Test
  void shouldReturnLocalizedLanguagesFromApi() {
    UUID entityTypeId = UUID.randomUUID();
    List<String> tenantList = List.of(TENANT_ID);
    String valueColumnName = "languages";
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("the entity type")
      .columns(List.of(new EntityTypeColumn()
        .name(valueColumnName)
        .source(new SourceColumn(entityTypeId, valueColumnName)
          .name("languages")
          .type(SourceColumn.TypeEnum.FQM))
      ));

    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, TENANT_ID, false)).thenReturn(entityType);
    when(crossTenantQueryService.getTenantsToQueryForColumnValues(entityType)).thenReturn(tenantList);
    when(languageClient.get(TENANT_ID)).thenReturn("""
           {
             "facets": {
               "languages": {
                 "values": [
                   {
                     "id": "eng",
                     "value": "eng"
                   },
                   {
                     "id": "ger",
                     "value": "ger"
                   },
                   {
                     "id": "mus",
                     "value": "mus"
                   },
                   {
                     "id": "",
                     "value": ""
                   }
                 ]
               }
             }
           }
      """);
    when(simpleHttpClient.get(eq("configurations/entries"), anyMap())).thenReturn("""
           {
             "configs": [
               {
                 "id":"2a132a01-623b-4d3a-9d9a-2feb777665c2",
                 "module":"ORG",
                 "configName":"localeSettings",
                 "enabled":true,
                 "value":"{\\"locale\\":\\"de\\",\\"timezone\\":\\"UTC\\",\\"currency\\":\\"USD\\"}","metadata":{"createdDate":"2024-03-25T17:37:22.309+00:00","createdByUserId":"db760bf8-e05a-4a5d-a4c3-8d49dc0d4e48"}
               }
             ],
             "totalRecords": 1,
             "resultInfo": {"totalRecords":1,"facets":[],"diagnostics":[]}
           }
      """);

    ColumnValues actualColumnValueLabel = entityTypeService.getFieldValues(entityTypeId, valueColumnName, "");

    ColumnValues expectedColumnValues = new ColumnValues().content(List.of(
      new ValueWithLabel().value("mus").label("Creek"),
      new ValueWithLabel().value("ger").label("Deutsch"),
      new ValueWithLabel().value("eng").label("Englisch")
    ));
    assertEquals(expectedColumnValues, actualColumnValueLabel);
  }

  @Test
  void shouldCatchExceptionFromLanguagesApi() {
    UUID entityTypeId = UUID.randomUUID();
    List<String> tenantList = List.of(TENANT_ID);
    String valueColumnName = "languages";
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("the entity type")
      .columns(List.of(new EntityTypeColumn()
        .name(valueColumnName)
        .source(new SourceColumn(entityTypeId, valueColumnName)
          .name("languages")
          .type(SourceColumn.TypeEnum.FQM))
      ));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, false)).thenReturn(entityType);
    when(crossTenantQueryService.getTenantsToQueryForColumnValues(entityType)).thenReturn(tenantList);
    when(languageClient.get(TENANT_ID)).thenThrow(FeignException.BadRequest.class);

    assertDoesNotThrow(() -> entityTypeService.getFieldValues(entityTypeId, valueColumnName, ""));
  }

  @Test
  void shouldReturnEntityTypeDefinition() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType expectedEntityType = TestDataFixture.getEntityDefinition();

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, false))
      .thenReturn(expectedEntityType);

    EntityType actualDefinition = entityTypeService
      .getEntityTypeDefinition(entityTypeId, false);

    assertEquals(expectedEntityType, actualDefinition);
  }

  @Test
  void shouldReturnCrossTenantDefinitionWhenEcsEnabled() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .crossTenantQueriesEnabled(true);
    EntityType expectedEntityType = new EntityType()
      .id(entityTypeId.toString())
      .crossTenantQueriesEnabled(true);

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, false))
      .thenReturn(entityType);
    when(crossTenantQueryService.isCentralTenant()).thenReturn(true);

    EntityType actualEntityType = entityTypeService
      .getEntityTypeDefinition(entityTypeId, false);

    assertEquals(expectedEntityType, actualEntityType);
  }

  @Test
  void shouldReturnNonCrossTenantDefinitionWhenEcsNotEnabled() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .crossTenantQueriesEnabled(true);
    EntityType expectedEntityType = new EntityType()
      .id(entityTypeId.toString())
      .crossTenantQueriesEnabled(false);

    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, TENANT_ID, false))
      .thenReturn(entityType);
    when(crossTenantQueryService.isCentralTenant()).thenReturn(false);

    EntityType actualEntityType = entityTypeService
      .getEntityTypeDefinition(entityTypeId, false);

    assertEquals(expectedEntityType, actualEntityType);
  }

  @Test
  void shouldReturnCurrencies() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "pol_currency";
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("currency-test")
      .columns(List.of(new EntityTypeColumn()
        .name(valueColumnName)
        .source(new SourceColumn(entityTypeId, valueColumnName)
          .name("currency")  // The special FQM source uses "currency" as the name of the currency value source
          .type(SourceColumn.TypeEnum.FQM))
      ));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, false)).thenReturn(entityType);

    List<ValueWithLabel> actualColumnValues = entityTypeService
      .getFieldValues(entityTypeId, valueColumnName, "")
      .getContent();

    // Check that a few known currencies are present
    assertTrue(actualColumnValues.contains(new ValueWithLabel().value("USD").label("US Dollar (USD)")));
    assertTrue(actualColumnValues.contains(new ValueWithLabel().value("INR").label("Indian Rupee (INR)")));
    assertTrue(actualColumnValues.contains(new ValueWithLabel().value("AMD").label("Armenian Dram (AMD)")));
    assertTrue(actualColumnValues.contains(new ValueWithLabel().value("GEL").label("Georgian Lari (GEL)")));
  }

  @Test
  void shouldReturnTenantId() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "this_is_a_tenant_id_column";
    List<String> tenantList = List.of("tenant1", "tenant2");
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("tenant-id-test")
      .columns(List.of(new EntityTypeColumn()
        .name(valueColumnName)
        .source(new SourceColumn(entityTypeId, valueColumnName)
          .name("tenant_id")  // The special FQM source uses "tenant_id" as the name of the currency value source
          .type(SourceColumn.TypeEnum.FQM))
      ));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, false)).thenReturn(entityType);
    when(crossTenantQueryService.getTenantsToQueryForColumnValues(entityType)).thenReturn(tenantList);

    List<ValueWithLabel> actualColumnValues = entityTypeService
      .getFieldValues(entityTypeId, valueColumnName, "")
      .getContent();


    // Check the response from the cross-tenant query service has been turned into a list of ValueWithLabels
    assertEquals(actualColumnValues, List.of(new ValueWithLabel("tenant1").label("tenant1"), new ValueWithLabel("tenant2").label("tenant2")));
  }

  @Test
  void shouldReturnTenantNames() {
    UUID entityTypeId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String valueColumnName = "this_is_a_tenant_name_column";
    List<Pair<String, String>> expectedIdNamePairs = List.of(
      Pair.of("tenant1", "Tenant 1"),
      Pair.of("tenant2", "Tenant 2")
    );
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("tenant-name-test")
      .columns(List.of(new EntityTypeColumn()
        .name(valueColumnName)
        .source(new SourceColumn(entityTypeId, valueColumnName)
          .name("tenant_name")  // The special FQM source uses "tenant_id" as the name of the currency value source
          .type(SourceColumn.TypeEnum.FQM))
      ));

    when(executionContext.getUserId()).thenReturn(userId);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, false)).thenReturn(entityType);
    when(crossTenantQueryService.getTenantIdNamePairs(entityType, userId)).thenReturn(expectedIdNamePairs);

    List<ValueWithLabel> actualColumnValues = entityTypeService
      .getFieldValues(entityTypeId, valueColumnName, "")
      .getContent();


    // Check the response from the cross-tenant query service has been turned into a list of ValueWithLabels
    assertEquals(actualColumnValues, List.of(new ValueWithLabel("tenant1").label("Tenant 1"), new ValueWithLabel("tenant2").label("Tenant 2")));
  }

  @Test
  void shouldIncludeCentralTenantIdInResponseForSimpleInstanceEntityType() {
    UUID entityTypeId = UUID.fromString("8fc4a9d2-7ccf-4233-afb8-796911839862"); // simple_instance
    String valueColumnName = "this_is_a_tenant_id_column";
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("tenant-id-test")
      .columns(List.of(new EntityTypeColumn()
        .name(valueColumnName)
        .source(new SourceColumn(entityTypeId, valueColumnName)
          .name("tenant_id")  // The special FQM source uses "tenant_id" as the name of the currency value source
          .type(SourceColumn.TypeEnum.FQM))
      ));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, false)).thenReturn(entityType);
    when(crossTenantQueryService.getTenantsToQueryForColumnValues(entityType)).thenReturn(List.of("tenant1", "central"));

    List<ValueWithLabel> actualColumnValues = entityTypeService
      .getFieldValues(entityTypeId, valueColumnName, "")
      .getContent();

    // Check the response from the cross-tenant query service has been turned into a list of ValueWithLabels
    assertEquals(List.of(new ValueWithLabel("tenant1").label("tenant1"), new ValueWithLabel("central").label("central")), actualColumnValues);
  }

  @Test
  void shouldGetCustomEntityTypeForValidId() {
    UUID customEntityTypeId = UUID.randomUUID();
    CustomEntityType expectedCustomEntityType = new CustomEntityType().id(customEntityTypeId.toString()).shared(false);

    when(repo.getCustomEntityType(customEntityTypeId)).thenReturn(expectedCustomEntityType);

    CustomEntityType result = entityTypeService.getCustomEntityType(customEntityTypeId);

    assertEquals(expectedCustomEntityType, result, "Should return the expected custom entity type");
    verify(repo, times(1)).getCustomEntityType(customEntityTypeId);
    verifyNoMoreInteractions(repo);
  }

  @Test
  void shouldThrowExceptionWhenCustomEntityTypeNotFound() {
    UUID customEntityTypeId = UUID.randomUUID();

    when(repo.getCustomEntityType(customEntityTypeId)).thenReturn(null);

    assertThrows(EntityTypeNotFoundException.class,
      () -> entityTypeService.getCustomEntityType(customEntityTypeId),
      "Should throw exception when custom entity type is not found");

    verify(repo, times(1)).getCustomEntityType(customEntityTypeId);
    verifyNoMoreInteractions(repo);
  }

  @Test
  void shouldCreateCustomEntityType() {
    UUID customEntityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    Date now = new Date();
    CustomEntityType inputCustomEntityType = new CustomEntityType().id(customEntityTypeId.toString()).shared(false);
    CustomEntityType expectedCustomEntityType = inputCustomEntityType.toBuilder()
      .createdAt(now)
      .updatedAt(now)
      .owner(ownerId)
      .idView(null)
      .build();

    when(executionContext.getUserId()).thenReturn(ownerId);
    when(clockService.now()).thenReturn(now);

    var actual = entityTypeService.createCustomEntityType(inputCustomEntityType);

    verify(repo, times(1)).createCustomEntityType(refEq(expectedCustomEntityType, "createdAt", "updatedAt"));
    verifyNoMoreInteractions(repo);
    assertEquals(expectedCustomEntityType, actual, "Should return the expected custom entity type");
  }

  @Test
  void updateCustomEntityType_shouldUpdateEntityTypeSuccessfully() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    Date updatedDate = new Date();

    CustomEntityType existingEntityType = CustomEntityType.builder()
      .owner(ownerId)
      .isCustom(true)
      .id(entityTypeId.toString())
      .name("Original name")
      ._private(false)
      .shared(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().type("entity-type").build()))
      .build();

    CustomEntityType customEntityType = CustomEntityType.builder()
      .owner(ownerId)
      .isCustom(true)
      .id(entityTypeId.toString())
      .name("Updated name")
      ._private(false)
      .shared(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().type("entity-type").build()))
      .build();

    when(repo.getCustomEntityType(entityTypeId)).thenReturn(existingEntityType);
    when(clockService.now()).thenReturn(updatedDate);
    doNothing().when(repo).updateCustomEntityType(any(CustomEntityType.class));

    // Act
    CustomEntityType result = entityTypeService.updateCustomEntityType(entityTypeId, customEntityType);

    // Assert
    assertEquals(updatedDate, result.getUpdatedAt());
    assertEquals("Updated name", result.getName());
    verify(repo).updateCustomEntityType(result);
  }

  @Test
  void updateCustomEntityType_shouldThrowNotFoundException_whenEntityTypeDoesNotExist() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();

    CustomEntityType customEntityType = CustomEntityType.builder()
      .owner(ownerId)
      .isCustom(true)
      .id(entityTypeId.toString())
      .name("Test Entity")
      ._private(false)
      .shared(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().type("entity-type").build()))
      .build();

    when(repo.getCustomEntityType(entityTypeId)).thenReturn(null);

    // Act & Assert
    assertThrows(EntityTypeNotFoundException.class, () ->
      entityTypeService.updateCustomEntityType(entityTypeId, customEntityType));

    verify(repo, never()).updateCustomEntityType(any());
  }

  @Test
  void updateCustomEntityType_shouldAllowUpdate_whenEntityTypeIsShared() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    Date updatedDate = new Date();

    CustomEntityType existingEntityType = CustomEntityType.builder()
      .owner(ownerId)
      .isCustom(true)
      .id(entityTypeId.toString())
      .name("Original name")
      ._private(false)
      .shared(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().type("entity-type").build()))
      .build();

    CustomEntityType customEntityType = CustomEntityType.builder()
      .owner(ownerId)
      .isCustom(true)
      .id(entityTypeId.toString())
      .name("Updated name")
      ._private(false)
      .shared(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().type("entity-type").build()))
      .build();

    when(repo.getCustomEntityType(entityTypeId)).thenReturn(existingEntityType);
    when(clockService.now()).thenReturn(updatedDate);
    doNothing().when(repo).updateCustomEntityType(any(CustomEntityType.class));

    // Act
    CustomEntityType result = entityTypeService.updateCustomEntityType(entityTypeId, customEntityType);

    // Assert
    assertEquals(updatedDate, result.getUpdatedAt());
    verify(repo).updateCustomEntityType(result);
  }

  @Test
  void updateCustomEntityType_shouldValidateCustomEntityType() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    UUID differentEntityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();

    CustomEntityType customEntityType = CustomEntityType.builder()
      .owner(ownerId)
      .isCustom(true)
      .id(differentEntityTypeId.toString())
      .name("Test Entity")
      ._private(false)
      .shared(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().type("entity-type").build()))
      .build();

    // Act & Assert
    assertThrows(InvalidEntityTypeDefinitionException.class, () ->
      entityTypeService.updateCustomEntityType(entityTypeId, customEntityType));

    verify(repo, never()).getCustomEntityType(any());
    verify(repo, never()).updateCustomEntityType(any());
  }

  @Test
  void updateCustomEntityType_shouldUpdateTimestamp() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    Date originalDate = new Date(System.currentTimeMillis() - 10000); // 10 seconds ago
    Date updatedDate = new Date();

    CustomEntityType existingEntityType = CustomEntityType.builder()
      .owner(ownerId)
      .isCustom(true)
      .id(entityTypeId.toString())
      .name("Original name")
      ._private(false)
      .shared(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().type("entity-type").build()))
      .updatedAt(originalDate)
      .build();

    CustomEntityType customEntityType = CustomEntityType.builder()
      .owner(ownerId)
      .isCustom(true)
      .id(entityTypeId.toString())
      .name("Updated name")
      ._private(false)
      .shared(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().type("entity-type").build()))
      .updatedAt(originalDate) // This should be overwritten
      .build();

    when(repo.getCustomEntityType(entityTypeId)).thenReturn(existingEntityType);
    when(clockService.now()).thenReturn(updatedDate);

    // Act
    CustomEntityType result = entityTypeService.updateCustomEntityType(entityTypeId, customEntityType);

    // Assert
    assertEquals(updatedDate, result.getUpdatedAt());
    verify(repo).updateCustomEntityType(argThat(entity ->
      entity.getUpdatedAt().equals(updatedDate) && !entity.getUpdatedAt().equals(originalDate)));
  }

  @Test
  void deleteCustomEntityType_shouldDeleteEntityTypeSuccessfully() {
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();

    CustomEntityType existingEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(ownerId);

    when(repo.getCustomEntityType(entityTypeId)).thenReturn(existingEntityType);
    when(executionContext.getUserId()).thenReturn(ownerId);

    assertDoesNotThrow(() -> entityTypeService.deleteCustomEntityType(entityTypeId));
    verify(repo, times(1)).deleteEntityType(entityTypeId);
  }

  @Test
  void deleteCustomEntityType_shouldThrowNotFoundException_whenEntityTypeDoesNotExist() {
    UUID entityTypeId = UUID.randomUUID();

    when(repo.getCustomEntityType(entityTypeId)).thenReturn(null);

    assertThrows(EntityTypeNotFoundException.class, () -> entityTypeService.deleteCustomEntityType(entityTypeId));
    verify(repo, never()).deleteEntityType(any());
  }

  @Test
  void deleteCustomEntityType_shouldThrowNotFoundException_whenEntityTypeIsNotCustom() {
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = CustomEntityType.builder()
      .id(entityTypeId.toString())
      .name("Test Entity")
      ._private(false)
      .build()
      .isCustom(null);
    when(repo.getCustomEntityType(entityTypeId)).thenReturn(customEntityType);

    assertThrows(EntityTypeNotFoundException.class, () -> entityTypeService.deleteCustomEntityType(entityTypeId));
    verify(repo, never()).deleteEntityType(any());
  }


  @Test
  void currentUserCanAccessCustomEntityType_whenEntityTypeIsShared_shouldReturnTrue() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();

    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(ownerId)
      .shared(true)
      .isCustom(true);

    when(repo.getCustomEntityType(entityTypeId)).thenReturn(customEntityType);

    // Act
    boolean result = entityTypeService.currentUserCanAccessCustomEntityType(entityTypeId.toString());

    // Assert
    assertTrue(result, "User should be able to access a shared custom entity type");
    verify(repo).getCustomEntityType(entityTypeId);
  }

  @Test
  void currentUserCanAccessCustomEntityType_whenEntityTypeIsOwnedByCurrentUser_shouldReturnTrue() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();

    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(ownerId)
      .shared(false)
      .isCustom(true);

    when(executionContext.getUserId()).thenReturn(ownerId); // Same user ID as owner
    when(repo.getCustomEntityType(entityTypeId)).thenReturn(customEntityType);

    // Act
    boolean result = entityTypeService.currentUserCanAccessCustomEntityType(entityTypeId.toString());

    // Assert
    assertTrue(result, "User should be able to access their own custom entity type");
    verify(repo).getCustomEntityType(entityTypeId);
  }

  @Test
  void currentUserCanAccessCustomEntityType_whenEntityTypeIsNotSharedAndNotOwned_shouldReturnFalse() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID(); // Different user ID

    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(ownerId)
      .shared(false)
      .isCustom(true);

    when(executionContext.getUserId()).thenReturn(currentUserId);
    when(repo.getCustomEntityType(entityTypeId)).thenReturn(customEntityType);

    // Act
    boolean result = entityTypeService.currentUserCanAccessCustomEntityType(entityTypeId.toString());

    // Assert
    assertFalse(result, "User should not be able to access a non-shared custom entity type they don't own");
    verify(repo).getCustomEntityType(entityTypeId);
  }

  @Test
  void enforceAccessForPossibleCustomEntityType_whenEntityTypeIsNotCustom_shouldNotEnforceAccess() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType().id(entityTypeId.toString());
    entityType.putAdditionalProperty("isCustom", false);

    when(repo.getEntityTypeDefinition(entityTypeId, executionContext.getTenantId()))
      .thenReturn(Optional.of(entityType));

    // Act
    entityTypeService.enforceAccessForPossibleCustomEntityType(entityTypeId);

    // Assert
    verify(repo).getEntityTypeDefinition(entityTypeId, executionContext.getTenantId());
    // No further interaction should happen - not getting the custom entity type
    verifyNoMoreInteractions(repo);
  }

  @Test
  void enforceAccessForPossibleCustomEntityType_whenEntityTypeIsCustomAndAccessible_shouldNotThrowException() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    EntityType entityType = new EntityType().id(entityTypeId.toString());
    entityType.putAdditionalProperty("isCustom", true);

    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(currentUserId)
      .shared(false)
      .isCustom(true);

    when(repo.getEntityTypeDefinition(entityTypeId, executionContext.getTenantId()))
      .thenReturn(Optional.of(entityType));
    when(repo.getCustomEntityType(entityTypeId)).thenReturn(customEntityType);
    when(executionContext.getUserId()).thenReturn(currentUserId);

    // Act & Assert
    entityTypeService.enforceAccessForPossibleCustomEntityType(entityTypeId);

    // Verify the expected method calls
    verify(repo).getEntityTypeDefinition(entityTypeId, executionContext.getTenantId());
    verify(repo).getCustomEntityType(entityTypeId);
  }

  @Test
  void enforceAccessForPossibleCustomEntityType_whenEntityTypeIsCustomAndNotAccessible_shouldThrowException() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID(); // Different from owner

    EntityType entityType = new EntityType().id(entityTypeId.toString());
    entityType.putAdditionalProperty("isCustom", true);

    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(ownerId)
      .shared(false)
      .isCustom(true);

    when(repo.getEntityTypeDefinition(entityTypeId, executionContext.getTenantId()))
      .thenReturn(Optional.of(entityType));
    when(repo.getCustomEntityType(entityTypeId)).thenReturn(customEntityType);
    when(executionContext.getUserId()).thenReturn(currentUserId);

    // Act & Assert
    assertThrows(CustomEntityTypeAccessDeniedException.class,
      () -> entityTypeService.enforceAccessForPossibleCustomEntityType(entityTypeId),
      "Should throw CustomEntityTypeAccessDeniedException when custom entity type is not accessible");

    verify(repo).getEntityTypeDefinition(entityTypeId, executionContext.getTenantId());
    verify(repo).getCustomEntityType(entityTypeId);
  }

  @Test
  void enforceCustomEntityTypeAccess_whenEntityTypeIsShared_shouldNotThrowException() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();

    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(ownerId)
      .shared(true)
      .isCustom(true);

    // Act & Assert
    entityTypeService.enforceCustomEntityTypeAccess(customEntityType);
    // No exception should be thrown
  }

  @Test
  void enforceCustomEntityTypeAccess_whenEntityTypeIsOwnedByCurrentUser_shouldNotThrowException() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();

    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(ownerId)
      .shared(false)
      .isCustom(true);

    when(executionContext.getUserId()).thenReturn(ownerId); // Same as owner

    // Act & Assert
    entityTypeService.enforceCustomEntityTypeAccess(customEntityType);
    // No exception should be thrown
  }

  @Test
  void enforceCustomEntityTypeAccess_whenEntityTypeIsNotSharedAndNotOwned_shouldThrowException() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID(); // Different user ID

    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(ownerId)
      .shared(false)
      .isCustom(true);

    when(executionContext.getUserId()).thenReturn(currentUserId);

    // Act & Assert
    assertThrows(CustomEntityTypeAccessDeniedException.class,
      () -> entityTypeService.enforceCustomEntityTypeAccess(customEntityType),
      "Should throw CustomEntityTypeAccessDeniedException when custom entity type is not accessible");
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenSourceViewIsNotNull() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .isCustom(true)
      .sourceView("some_source_view");

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> EntityTypeService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when sourceView is not null");

    assertEquals("Custom entity types must not contain a sourceView property", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenSourceViewExtractorIsNotNull() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .isCustom(true)
      .sourceViewExtractor("some_source_view_extractor");

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> EntityTypeService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when sourceViewExtractor is not null");

    assertEquals("Custom entity types must not contain a sourceViewExtractor property", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenIdViewIsNotNull() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .isCustom(true)
      .idView("some_id_view");

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> EntityTypeService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when idView is not null");

    assertEquals("Custom entity types must not contain a idView property", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenCustomFieldEntityTypeIdRefersSelf() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .isCustom(true)
      .customFieldEntityTypeId(entityTypeId.toString());

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> EntityTypeService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when customFieldEntityTypeId refers to itself");

    assertEquals("Custom entity types must not refer to themselves with the customFieldEntityTypeId property", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenIsCustomIsNull() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .isCustom(null); // isCustom is null

    // Act & Assert
    EntityTypeNotFoundException exception = assertThrows(EntityTypeNotFoundException.class,
      () -> EntityTypeService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw EntityTypeNotFoundException when isCustom is null");

    assertEquals("Entity type " + entityTypeId + " is not a custom entity type", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenIsCustomIsFalse() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .isCustom(false); // isCustom is false

    // Act & Assert
    EntityTypeNotFoundException exception = assertThrows(EntityTypeNotFoundException.class,
      () -> EntityTypeService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw EntityTypeNotFoundException when isCustom is false");

    assertEquals("Entity type " + entityTypeId + " is not a custom entity type", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenSourcesAreNotEntityTypeSourceEntityType() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .isCustom(true)
      .sources(List.of(new EntityTypeSourceDatabase().type("db").alias("source1"))); // Not an EntityTypeSourceEntityType

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> EntityTypeService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when sources are not all EntityTypeSourceEntityType");

    assertEquals("Custom entity types must contain only entity-type sources", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenColumnsAreNotEmpty() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .isCustom(true)
      .columns(List.of(new EntityTypeColumn().name("test_column"))); // Non-empty columns

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> EntityTypeService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when columns are not empty");

    assertEquals("Custom entity types must not contain columns", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenCrossTenantQueriesEnabled() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .isCustom(true)
      .crossTenantQueriesEnabled(true); // Cross-tenant queries enabled

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> EntityTypeService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when crossTenantQueriesEnabled is true");

    assertEquals("Custom entity must not have cross-tenant queries enabled", exception.getMessage());
  }
}
