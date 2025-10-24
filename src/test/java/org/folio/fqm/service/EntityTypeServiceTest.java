package org.folio.fqm.service;

import feign.FeignException;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.fqm.client.CrossTenantHttpClient;
import org.folio.fqm.client.LanguageClient;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.domain.dto.EntityTypeSummary;
import org.folio.fqm.exception.EntityTypeInUseException;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.testutil.TestDataFixture;
import org.folio.querytool.domain.dto.ColumnValues;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.CustomFieldMetadata;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.querytool.domain.dto.SourceColumn;
import org.folio.querytool.domain.dto.UpdateUsedByRequest;
import org.folio.querytool.domain.dto.UpdateUsedByRequest.OperationEnum;
import org.folio.querytool.domain.dto.ValueSourceApi;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

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
    UUID id1 = new UUID(1, 1);
    UUID id2 = new UUID(2, 2);
    Set<UUID> ids = Set.of(id1, id2);
    List<EntityTypeSummary> expectedSummary = List.of(
      new EntityTypeSummary().id(id1).label("label_01").isCustom(false),
      new EntityTypeSummary().id(id2).label("label_02").isCustom(false));

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
    UUID id1 = new UUID(1, 1);
    UUID id2 = new UUID(2, 2);
    Set<UUID> ids = Set.of(id1, id2);
    List<EntityTypeSummary> expectedSummary = List.of(
      new EntityTypeSummary().id(id1).label("label_01").isCustom(false).crossTenantQueriesEnabled(true),
      new EntityTypeSummary().id(id2).label("label_02").isCustom(false));
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
    UUID id1 = new UUID(1, 1);
    UUID id2 = new UUID(2, 2);
    Set<UUID> ids = Set.of(id1, id2);
    List<EntityTypeSummary> expectedSummary = List.of(new EntityTypeSummary().id(id2).label("label_02").isCustom(false));
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
    UUID id1 = new UUID(1, 1);
    UUID id2 = new UUID(2, 2);
    Set<UUID> ids = Set.of(id1, id2);

    List<EntityTypeSummary> expectedSummary = List.of(
      new EntityTypeSummary().id(id1).label("label_01").isCustom(false),
      new EntityTypeSummary().id(id2).label("label_02").isCustom(false)
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
    UUID id1 = new UUID(1, 1);
    UUID id2 = new UUID(2, 2);
    Set<UUID> ids = Set.of(id1, id2);
    List<EntityTypeSummary> expectedSummary = List.of(
      new EntityTypeSummary().id(id1).label("label_01").isCustom(false).missingPermissions(List.of("perm1")),
      new EntityTypeSummary().id(id2).label("label_02").isCustom(false).missingPermissions(List.of()));
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

  @ParameterizedTest
  @ValueSource(strings = {"organization", "donor_organization"})
  void testReturnsNoValuesForQBSourceTypes(String source) {
    UUID entityTypeId = UUID.fromString("f91cbc3a-29f1-5280-aee4-eb821285cba8");
    List<String> tenantList = List.of(TENANT_ID);
    String valueColumnName = "test_column";
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("the entity type")
      .columns(List.of(new EntityTypeColumn()
        .name(valueColumnName)
        .source(new SourceColumn(entityTypeId, valueColumnName)
          .name(source)
          .type(SourceColumn.TypeEnum.FQM))
      ));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, false)).thenReturn(entityType);
    when(crossTenantQueryService.getTenantsToQueryForColumnValues(entityType)).thenReturn(tenantList);

    ColumnValues actualColumnValueLabel = entityTypeService.getFieldValues(entityTypeId, valueColumnName, "");

    ColumnValues expectedColumnValues = new ColumnValues().content(List.of());
    assertEquals(expectedColumnValues, actualColumnValueLabel);
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
    CustomEntityType inputCustomEntityType = new CustomEntityType()
      .id(customEntityTypeId.toString())
      .shared(false)
      .name("test")
      ._private(false);
    CustomEntityType expectedCustomEntityType = inputCustomEntityType.toBuilder()
      .createdAt(now)
      .updatedAt(now)
      .owner(ownerId)
      .name("test")
      ._private(false)
      .build();

    when(executionContext.getUserId()).thenReturn(ownerId);
    when(clockService.now()).thenReturn(now);

    var actual = entityTypeService.createCustomEntityType(inputCustomEntityType);

    verify(repo, times(1)).createCustomEntityType(refEq(expectedCustomEntityType, "createdAt", "updatedAt"));
    verifyNoMoreInteractions(repo);
    assertEquals(expectedCustomEntityType, actual, "Should return the expected custom entity type");
  }

  @Test
  void createCustomEntityType_shouldGenerateId_whenIdIsNull() {
    UUID ownerId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(null)
      .shared(false)
      .owner(ownerId)
      .name("test")
      ._private(false)
      .isCustom(true);

    when(clockService.now()).thenReturn(new Date());
    when(executionContext.getUserId()).thenReturn(ownerId);

    CustomEntityType result = entityTypeService.createCustomEntityType(customEntityType);

    assertNotNull(result.getId(), "ID should be generated when input ID is null");
    assertDoesNotThrow(() -> UUID.fromString(result.getId()), "Generated ID should be a valid UUID");
    verify(repo).createCustomEntityType(result);
  }

  @Test
  void createCustomEntityType_shouldGenerateId_whenIdIsEmptyString() {
    UUID ownerId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id("")
      .shared(false)
      .owner(ownerId)
      .name("test")
      ._private(false)
      .isCustom(true);

    when(clockService.now()).thenReturn(new Date());
    when(executionContext.getUserId()).thenReturn(ownerId);

    CustomEntityType result = entityTypeService.createCustomEntityType(customEntityType);

    assertNotNull(result.getId(), "ID should be generated when input ID is empty string");
    assertFalse(result.getId().isEmpty(), "Generated ID should not be empty");
    assertDoesNotThrow(() -> UUID.fromString(result.getId()), "Generated ID should be a valid UUID");
    verify(repo).createCustomEntityType(result);
  }

  @Test
  void createCustomEntityType_shouldThrowInvalidEntityTypeDefinitionException_whenIdIsInvalidUUID() {
    CustomEntityType customEntityType = new CustomEntityType()
      .id("not-a-uuid")
      .shared(false)
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(true);

    InvalidEntityTypeDefinitionException ex = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.createCustomEntityType(customEntityType)
    );
    assertEquals("Invalid string provided for entity type ID", ex.getMessage());

    // Provide a truncated UUID to ensure it's not accepted and 0-padded
    customEntityType.id("12343ca1-b910-4e57-9187-d29d0");
    ex = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.createCustomEntityType(customEntityType)
    );
    assertEquals("Invalid string provided for entity type ID", ex.getMessage());
  }

  @Test
  void createCustomEntityType_shouldThrowInvalidEntityTypeDefinitionException_whenOwnerIdDoesNotMatchExecutionContext() {
    UUID ownerId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(UUID.randomUUID().toString())
      .shared(false)
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(true);

    when(executionContext.getUserId()).thenReturn(ownerId);

    Exception ex = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.createCustomEntityType(customEntityType)
    );
    assertTrue(ex.getMessage().contains("owner ID mismatch"));
  }

  @Test
  void updateCustomEntityType_shouldUpdateEntityTypeSuccessfully() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    Date updatedDate = new Date();
    UUID targetId = UUID.randomUUID();

    CustomEntityType existingEntityType = CustomEntityType.builder()
      .owner(ownerId)
      .isCustom(true)
      .id(entityTypeId.toString())
      .name("Original name")
      ._private(false)
      .shared(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().type("entity-type").alias("source1").targetId(targetId).build()))
      .build();

    CustomEntityType customEntityType = CustomEntityType.builder()
      .owner(ownerId)
      .isCustom(true)
      .id(entityTypeId.toString())
      .name("Updated name")
      ._private(false)
      .shared(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().type("entity-type").alias("source1").targetId(targetId).build()))
      .build();

    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(repo.getCustomEntityType(entityTypeId)).thenReturn(existingEntityType);
    when(repo.getEntityTypeDefinition(targetId, TENANT_ID)).thenReturn(Optional.of(new EntityType()));
    when(clockService.now()).thenReturn(updatedDate);
    doNothing().when(repo).updateEntityType(any(CustomEntityType.class));

    // Act
    CustomEntityType result = entityTypeService.updateCustomEntityType(entityTypeId, customEntityType);

    // Assert
    assertEquals(updatedDate, result.getUpdatedAt());
    assertEquals("Updated name", result.getName());
    verify(repo).updateEntityType(result);
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

    verify(repo, never()).updateEntityType(any());
  }

  @Test
  void updateCustomEntityType_shouldAllowUpdate_whenEntityTypeIsShared() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    Date updatedDate = new Date();
    UUID targetId = UUID.randomUUID();

    CustomEntityType existingEntityType = CustomEntityType.builder()
      .owner(ownerId)
      .isCustom(true)
      .id(entityTypeId.toString())
      .name("Original name")
      ._private(false)
      .shared(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().type("entity-type").alias("source1").targetId(targetId).build()))
      .build();

    CustomEntityType customEntityType = CustomEntityType.builder()
      .owner(ownerId)
      .isCustom(true)
      .id(entityTypeId.toString())
      .name("Updated name")
      ._private(false)
      .shared(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().type("entity-type").alias("source1").targetId(targetId).build()))
      .build();

    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(repo.getEntityTypeDefinition(targetId, TENANT_ID)).thenReturn(Optional.of(new EntityType()));
    when(repo.getCustomEntityType(entityTypeId)).thenReturn(existingEntityType);
    when(clockService.now()).thenReturn(updatedDate);
    doNothing().when(repo).updateEntityType(any(CustomEntityType.class));

    // Act
    CustomEntityType result = entityTypeService.updateCustomEntityType(entityTypeId, customEntityType);

    // Assert
    assertEquals(updatedDate, result.getUpdatedAt());
    verify(repo).updateEntityType(result);
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

    when(repo.getCustomEntityType(entityTypeId)).thenReturn(customEntityType);

    // Act & Assert
    assertThrows(InvalidEntityTypeDefinitionException.class, () ->
      entityTypeService.updateCustomEntityType(entityTypeId, customEntityType));

    verify(repo, never()).updateEntityType(any());
  }

  @Test
  void updateCustomEntityType_shouldUpdateTimestamp() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    Date originalDate = new Date(System.currentTimeMillis() - 10000); // 10 seconds ago
    Date updatedDate = new Date();
    UUID targetId = UUID.randomUUID();

    CustomEntityType existingEntityType = CustomEntityType.builder()
      .owner(ownerId)
      .isCustom(true)
      .id(entityTypeId.toString())
      .name("Original name")
      ._private(false)
      .shared(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().type("entity-type").alias("source1").targetId(targetId).build()))
      .updatedAt(originalDate)
      .build();

    CustomEntityType customEntityType = CustomEntityType.builder()
      .owner(ownerId)
      .isCustom(true)
      .id(entityTypeId.toString())
      .name("Updated name")
      ._private(false)
      .shared(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().type("entity-type").alias("source1").targetId(targetId).build()))
      .updatedAt(originalDate) // This should be overwritten
      .build();

    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(repo.getEntityTypeDefinition(targetId, TENANT_ID)).thenReturn(Optional.of(new EntityType()));
    when(repo.getCustomEntityType(entityTypeId)).thenReturn(existingEntityType);
    when(clockService.now()).thenReturn(updatedDate);

    // Act
    CustomEntityType result = entityTypeService.updateCustomEntityType(entityTypeId, customEntityType);

    // Assert
    assertEquals(updatedDate, result.getUpdatedAt());
    verify(repo).updateEntityType(argThat(entity ->
      ((CustomEntityType) entity).getUpdatedAt().equals(updatedDate) && !((CustomEntityType) entity).getUpdatedAt().equals(originalDate)));
  }

  @Test
  void deleteCustomEntityType_shouldDeleteEntityTypeSuccessfully() {
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();

    CustomEntityType existingEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(ownerId);

    when(repo.getCustomEntityType(entityTypeId)).thenReturn(existingEntityType);

    assertDoesNotThrow(() -> entityTypeService.deleteCustomEntityType(entityTypeId));
    verify(repo, times(1)).updateEntityType(any(CustomEntityType.class));
  }

  @Test
  void deleteCustomEntityType_shouldAllowDeletionIfOnlyDependentEntityTypesAreDeleted() {
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    String tenantId = "tenant_01";

    CustomEntityType existingEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(ownerId);

    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(repo.getCustomEntityType(entityTypeId)).thenReturn(existingEntityType);
    when(repo.getEntityTypeDefinitions(Set.of(), tenantId)).thenReturn(
      Stream.of(
        new EntityType().id(UUID.randomUUID().toString()).name("Dependent Type").deleted(true).sources(List.of(
          new EntityTypeSourceEntityType().targetId(entityTypeId)
        )),
        new EntityType().id(UUID.randomUUID().toString()).name("Non-dependent Type"),
        new EntityType().id(UUID.randomUUID().toString()).name("Another non-dependent Type").sources(List.of(
          new EntityTypeSourceEntityType()
        )),
        new EntityType().id(UUID.randomUUID().toString()).name("A third non-dependent Type").sources(List.of(
          new EntityTypeSourceEntityType().targetId(UUID.randomUUID())
        ))
      )
    );

    assertDoesNotThrow(() -> entityTypeService.deleteCustomEntityType(entityTypeId));
    verify(repo, times(1)).updateEntityType(any(CustomEntityType.class));
  }

  @Test
  void deleteCustomEntityType_shouldThrowExceptionIfOtherEntityTypesDependOnEntityType() {
    UUID entityTypeId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    String tenantId = "tenant_01";

    CustomEntityType existingEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(ownerId);

    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(repo.getCustomEntityType(entityTypeId)).thenReturn(existingEntityType);
    when(repo.getEntityTypeDefinitions(Set.of(), tenantId)).thenReturn(
      Stream.of(
        new EntityType().id(UUID.randomUUID().toString()).name("Dependent Type").sources(List.of(
          new EntityTypeSourceEntityType().targetId(entityTypeId)
        ))
      )
    );

    EntityTypeInUseException ex = assertThrows(EntityTypeInUseException.class, () -> entityTypeService.deleteCustomEntityType(entityTypeId));
    assertTrue(ex.getMessage().contains("Cannot delete custom entity type because it is used as a source by other entity types"));
    var error = ex.getError();
    assertEquals("entityTypeId", error.getParameters().get(0).getKey());
    assertEquals(entityTypeId.toString(), error.getParameters().get(0).getValue());

    assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());

  }

  @Test
  void deleteCustomEntityType_shouldThrowNotFoundException_whenEntityTypeDoesNotExist() {
    UUID entityTypeId = UUID.randomUUID();

    when(repo.getCustomEntityType(entityTypeId)).thenReturn(null);

    assertThrows(EntityTypeNotFoundException.class, () -> entityTypeService.deleteCustomEntityType(entityTypeId));
    verify(repo, never()).updateEntityType(any());
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
    verify(repo, never()).updateEntityType(any());
  }

  @Test
  void testVerifyAccessForPossibleCustomEntityTypeDoesNotCheckNonCustom() {
    EntityType entityType = new EntityType().id("87c4fb3e-5055-567b-9091-327c191f1ed2");
    entityType.putAdditionalProperty("isCustom", false);

    when(repo.getEntityTypeDefinition(UUID.fromString(entityType.getId()), executionContext.getTenantId()))
      .thenReturn(Optional.of(entityType));

    entityTypeService.verifyAccessForPossibleCustomEntityType(UUID.fromString(entityType.getId()));

    verifyNoInteractions(permissionsService);
  }

  @Test
  void testVerifyAccessForPossibleCustomEntityTypeChecksWhenCustom() {
    EntityType entityType = new EntityType().id("87c4fb3e-5055-567b-9091-327c191f1ed2");
    entityType.putAdditionalProperty("isCustom", true);
    CustomEntityType customEntity = new CustomEntityType();

    when(repo.getEntityTypeDefinition(UUID.fromString(entityType.getId()), executionContext.getTenantId()))
      .thenReturn(Optional.of(entityType));
    when(repo.getCustomEntityType(UUID.fromString(entityType.getId()))).thenReturn(customEntity);

    entityTypeService.verifyAccessForPossibleCustomEntityType(UUID.fromString(entityType.getId()));

    verify(permissionsService).verifyUserCanAccessCustomEntityType(customEntity);
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenSourceViewIsNotNull() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(true)
      .sourceView("some_source_view");

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when sourceView is not null");

    assertEquals("Custom entity types must not contain a sourceView property", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenSourceViewExtractorIsNotNull() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(true)
      .sourceViewExtractor("some_source_view_extractor");

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when sourceViewExtractor is not null");

    assertEquals("Custom entity types must not contain a sourceViewExtractor property", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenCustomFieldEntityTypeIdIsPresent() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(true)
      .customFieldEntityTypeId(entityTypeId.toString());

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when customFieldEntityTypeId refers to itself");

    assertEquals("Custom field entity type id must not be defined for custom entity types", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenOwnerIsNull() {
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .name("test")
      ._private(false)
      .isCustom(true);

    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when owner is null");

    assertEquals("Custom entity type must have an owner", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenIsCustomIsNull() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(null); // isCustom is null

    // Act & Assert
    EntityTypeNotFoundException exception = assertThrows(EntityTypeNotFoundException.class,
      () -> entityTypeService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw EntityTypeNotFoundException when isCustom is null");

    assertEquals("Entity type " + entityTypeId + " is not a custom entity type", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenSharedIsNull() {
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(true)
      .shared(null);

    InvalidEntityTypeDefinitionException exception = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateCustomEntityType(entityTypeId, customEntityType)
    );
    assertEquals("Custom entity type must have a shared property", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenIsCustomIsFalse() {
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(false)
      .shared(true);

    EntityTypeNotFoundException exception = assertThrows(
      EntityTypeNotFoundException.class,
      () -> entityTypeService.validateCustomEntityType(entityTypeId, customEntityType)
    );

    assertEquals("Entity type " + entityTypeId + " is not a custom entity type", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenSourcesAreNotEntityTypeSourceEntityType() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(true)
      .sources(List.of(new EntityTypeSourceDatabase().type("db").alias("source1"))); // Not an EntityTypeSourceEntityType

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when sources are not all EntityTypeSourceEntityType");

    assertEquals("Custom entity types must contain only entity-type sources", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenColumnsAreNotEmpty() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(true)
      .columns(List.of(new EntityTypeColumn().name("test_column"))); // Non-empty columns

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when columns are not empty");

    assertEquals("Custom entity types must not contain columns", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenCrossTenantQueriesEnabled() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(true)
      .crossTenantQueriesEnabled(true); // Cross-tenant queries enabled

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when crossTenantQueriesEnabled is true");

    assertEquals("Custom entity must not have cross-tenant queries enabled", exception.getMessage());
  }

  @Test
  void validateEntityType_shouldThrowException_whenIdIsNull() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id(null)
      .name("Test")
      ._private(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));

    InvalidEntityTypeDefinitionException ex = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex.getMessage().contains("Entity type ID cannot be null"));

    EntityType entityTypeWithId = entityType.id(entityTypeId.toString());
    ex = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(null, entityTypeWithId, null));
    assertTrue(ex.getMessage().contains("Entity type ID cannot be null"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenIdDoesNotMatch() {
    UUID entityTypeId = UUID.randomUUID();
    UUID differentId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id(differentId.toString())
      .name("Test")
      ._private(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));

    InvalidEntityTypeDefinitionException ex = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex.getMessage().contains("Entity type ID in the request body does not match"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenIdIsInvalidUUID() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id("not-a-uuid")
      .name("Test")
      ._private(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));

    InvalidEntityTypeDefinitionException ex = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex.getMessage().contains("Invalid string provided for entity type ID"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenNameIsNullOrBlank() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name(null)
      ._private(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));

    InvalidEntityTypeDefinitionException ex1 = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex1.getMessage().contains("Entity type name cannot be null or blank"));

    entityType.name("");
    InvalidEntityTypeDefinitionException ex2 = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex2.getMessage().contains("Entity type name cannot be null or blank"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenPrivateIsNull() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(null)
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));

    InvalidEntityTypeDefinitionException ex = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex.getMessage().contains("Entity type must have private property set"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenSourceAliasIsInvalid() {
    UUID entityTypeId = UUID.randomUUID();
    EntityTypeSourceEntityType source = EntityTypeSourceEntityType.builder().alias(null).type("entity-type").targetId(entityTypeId).build();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .sources(List.of(source));

    InvalidEntityTypeDefinitionException ex1 = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex1.getMessage().contains("Source alias cannot be null or blank"));

    source.alias("");
    InvalidEntityTypeDefinitionException ex2 = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex2.getMessage().contains("Source alias cannot be null or blank"));

    source.alias("dot.alias");
    InvalidEntityTypeDefinitionException ex3 = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex3.getMessage().contains("must not contain '.'"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenSourceTypeIsNull() {
    UUID entityTypeId = UUID.randomUUID();
    EntityTypeSourceEntityType source = EntityTypeSourceEntityType.builder().alias("alias").type(null).targetId(entityTypeId).build();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .sources(List.of(source));

    InvalidEntityTypeDefinitionException ex = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex.getMessage().contains("Source type cannot be null"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenSourceTypeIsInvalid() {
    UUID entityTypeId = UUID.randomUUID();
    EntityTypeSourceEntityType source = EntityTypeSourceEntityType.builder()
      .alias("alias")
      .type("invalid-type")
      .targetId(entityTypeId)
      .build();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .sources(List.of(source));

    InvalidEntityTypeDefinitionException ex = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityType, null)
    );
    assertTrue(ex.getMessage().contains("Source type must be either 'db' or 'entity-type'"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenSourceEntityTypeIdIsNull() {
    UUID entityTypeId = UUID.randomUUID();
    EntityTypeSourceEntityType source = EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(null).build();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .sources(List.of(source));

    InvalidEntityTypeDefinitionException ex = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex.getMessage().contains("Source entity type ID cannot be null for entity-type sources"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenSourceEntityTypeIdDoesNotExist() {
    UUID entityTypeId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    EntityTypeSourceEntityType source = EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(targetId).build();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .sources(List.of(source));

    when(repo.getEntityTypeDefinition(targetId, null)).thenReturn(Optional.empty());

    InvalidEntityTypeDefinitionException ex = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex.getMessage().contains("Source with target ID " + targetId + " does not correspond to a valid entity type"));
  }

  @Test
  void validateEntityType_shouldNotThrowException_whenSourceEntityTypeIdIsValid() {
    UUID entityTypeId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    EntityTypeSourceEntityType source = EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(targetId).build();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .sources(List.of(source));

    when(repo.getEntityTypeDefinition(targetId, null)).thenReturn(Optional.of(new EntityType()));
    assertDoesNotThrow(() -> entityTypeService.validateEntityType(entityTypeId, entityType, null));
  }

  @Test
  void validateEntityType_shouldThrowException_whenCustomFieldMetadataConfigurationViewIsNullOrBlank() {
    UUID entityTypeId = UUID.randomUUID();
    CustomFieldMetadata metadataWithNullView = new CustomFieldMetadata().configurationView(null).dataExtractionPath("path");
    CustomFieldMetadata metadataWithBlankView = new CustomFieldMetadata().configurationView("").dataExtractionPath("path");
    EntityTypeColumn columnWithNullView = new EntityTypeColumn()
      .name("cf1")
      .dataType(new org.folio.querytool.domain.dto.CustomFieldType().customFieldMetadata(metadataWithNullView));
    EntityTypeColumn columnWithBlankView = new EntityTypeColumn()
      .name("cf2")
      .dataType(new org.folio.querytool.domain.dto.CustomFieldType().customFieldMetadata(metadataWithBlankView));
    EntityType entityTypeNull = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .columns(List.of(columnWithNullView))
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));
    EntityType entityTypeBlank = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .columns(List.of(columnWithBlankView))
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));

    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(repo.getEntityTypeDefinition(entityTypeId, TENANT_ID)).thenReturn(Optional.of(new EntityType()));

    InvalidEntityTypeDefinitionException ex1 = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityTypeNull, null)
    );
    assertEquals("Custom field metadata must have a configuration view defined", ex1.getMessage());

    InvalidEntityTypeDefinitionException ex2 = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityTypeBlank, null)
    );
    assertEquals("Custom field metadata must have a configuration view defined", ex2.getMessage());
  }

  @Test
  void validateEntityType_shouldThrowException_whenCustomFieldMetadataDataExtractionPathIsNullOrBlank() {
    UUID entityTypeId = UUID.randomUUID();
    CustomFieldMetadata metadataWithNullPath = new CustomFieldMetadata().configurationView("view").dataExtractionPath(null);
    CustomFieldMetadata metadataWithBlankPath = new CustomFieldMetadata().configurationView("view").dataExtractionPath("");
    EntityTypeColumn columnWithNullPath = new EntityTypeColumn()
      .name("cf1")
      .dataType(new org.folio.querytool.domain.dto.CustomFieldType().customFieldMetadata(metadataWithNullPath));
    EntityTypeColumn columnWithBlankPath = new EntityTypeColumn()
      .name("cf2")
      .dataType(new org.folio.querytool.domain.dto.CustomFieldType().customFieldMetadata(metadataWithBlankPath));
    EntityType entityTypeNull = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .columns(List.of(columnWithNullPath))
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));
    EntityType entityTypeBlank = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .columns(List.of(columnWithBlankPath))
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));

    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(repo.getEntityTypeDefinition(entityTypeId, TENANT_ID)).thenReturn(Optional.of(new EntityType()));

    InvalidEntityTypeDefinitionException ex1 = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityTypeNull, null)
    );
    assertEquals("Custom field metadata must have a data extraction path defined", ex1.getMessage());

    InvalidEntityTypeDefinitionException ex2 = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityTypeBlank, null)
    );
    assertEquals("Custom field metadata must have a data extraction path defined", ex2.getMessage());
  }

  @Test
  void validateEntityType_shouldNotThrow_whenCustomFieldMetadataIsValid() {
    UUID entityTypeId = UUID.randomUUID();
    CustomFieldMetadata validMetadata = new CustomFieldMetadata().configurationView("view").dataExtractionPath("path");
    EntityTypeColumn validColumn = new EntityTypeColumn()
      .name("cf1")
      .dataType(new org.folio.querytool.domain.dto.CustomFieldType().customFieldMetadata(validMetadata));
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .columns(List.of(validColumn))
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));

    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(repo.getEntityTypeDefinition(entityTypeId, TENANT_ID)).thenReturn(Optional.of(new EntityType()));

    assertDoesNotThrow(() -> entityTypeService.validateEntityType(entityTypeId, entityType, null));
  }

  @Test
  void validateEntityType_shouldThrowException_whenSourceIdNotInValidSources() {
    UUID entityTypeId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    List<String> validSources = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());

    EntityTypeSourceEntityType source = new EntityTypeSourceEntityType();
    source.setAlias("sourceAlias");
    source.setType("entity-type");
    source.setTargetId(sourceId);

    EntityType entityType = new EntityType();
    entityType.setId(entityTypeId.toString());
    entityType.setName("Test Entity");
    entityType.setPrivate(false);
    entityType.setSources(List.of(source));
    entityType.setColumns(List.of());

    InvalidEntityTypeDefinitionException ex = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityType, validSources)
    );
    assertTrue(ex.getMessage().contains("Source with target ID " + sourceId + " does not correspond to a valid entity type"));
  }

  @Test
  void validateEntityType_shouldNotThrow_whenSourceIdIsInValidEntityTypeIds() {
    UUID entityTypeId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    List<String> validEntityTypeIds = List.of(sourceId.toString(), UUID.randomUUID().toString());

    EntityTypeSourceEntityType source = EntityTypeSourceEntityType.builder()
      .alias("sourceAlias")
      .type("entity-type")
      .targetId(sourceId)
      .build();

    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test Entity")
      ._private(false)
      .sources(List.of(source))
      .columns(List.of());

    assertDoesNotThrow(() -> entityTypeService.validateEntityType(entityTypeId, entityType, validEntityTypeIds));
  }

  @Test
  void validateEntityType_shouldThrowException_whenSourcesIsNull() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test Entity")
      ._private(true)
      .sources(null);

    InvalidEntityTypeDefinitionException ex = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeService.validateEntityType(entityTypeId, entityType, null)
    );
    assertEquals("Entity type must have at least one source defined", ex.getMessage());
  }


  @Test
  void shouldAddUsedByWhenOperationIsAdd() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entity = new EntityType()
      .id(entityTypeId.toString())
      .usedBy(new ArrayList<>(List.of("existing-module")));

    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(repo.getEntityTypeDefinition(entityTypeId, TENANT_ID))
      .thenReturn(Optional.of(entity));

    Optional<EntityType> result = entityTypeService.updateEntityTypeUsedBy(
      entityTypeId, "new-module", OperationEnum.ADD);

    assertTrue(result.isPresent());
    assertTrue(result.get().getUsedBy().containsAll(List.of("existing-module", "new-module")));

    ArgumentCaptor<EntityType> captor = ArgumentCaptor.forClass(EntityType.class);
    verify(repo).updateEntityType(captor.capture());
    assertTrue(captor.getValue().getUsedBy().contains("new-module"));
  }

  @Test
  void shouldRemoveUsedByWhenOperationIsRemove() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entity = new EntityType()
      .id(entityTypeId.toString())
      .usedBy(new ArrayList<>(List.of("module-to-keep", "module-to-remove")));

    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(repo.getEntityTypeDefinition(entityTypeId, TENANT_ID))
      .thenReturn(Optional.of(entity));

    Optional<EntityType> result = entityTypeService.updateEntityTypeUsedBy(
      entityTypeId, "module-to-remove", OperationEnum.REMOVE);

    assertTrue(result.isPresent());
    assertEquals(List.of("module-to-keep"), result.get().getUsedBy());
    verify(repo).updateEntityType(any(EntityType.class));
  }

  @Test
  void shouldReturnEmptyWhenEntityTypeNotFound() {
    UUID entityTypeId = UUID.randomUUID();

    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(repo.getEntityTypeDefinition(entityTypeId, TENANT_ID))
      .thenReturn(Optional.empty());

    Optional<EntityType> result = entityTypeService.updateEntityTypeUsedBy(
      entityTypeId, "my-module", OperationEnum.ADD);

    assertTrue(result.isEmpty());
    verify(repo, never()).updateEntityType(any());
  }

  @Test
  void shouldHandleNullUsedByListGracefully() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entity = new EntityType()
      .id(entityTypeId.toString())
      .usedBy(null);

    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(repo.getEntityTypeDefinition(entityTypeId, TENANT_ID))
      .thenReturn(Optional.of(entity));

    Optional<EntityType> result = entityTypeService.updateEntityTypeUsedBy(
      entityTypeId, "my-module", UpdateUsedByRequest.OperationEnum.ADD);

    assertTrue(result.isPresent());
    assertEquals(List.of("my-module"), result.get().getUsedBy());
    verify(repo).updateEntityType(any(EntityType.class));
  }
}
