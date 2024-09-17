package org.folio.fqm.service;

import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.testutil.TestDataFixture;
import org.folio.fql.service.FqlValidationService;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.domain.dto.PurgedQueries;
import org.folio.fqm.exception.InvalidFqlException;
import org.folio.fqm.exception.QueryNotFoundException;
import org.folio.fqm.repository.QueryRepository;
import org.folio.fqm.repository.QueryResultsRepository;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.QueryDetails;
import org.folio.querytool.domain.dto.QueryIdentifier;
import org.folio.querytool.domain.dto.ResultsetPage;
import org.folio.querytool.domain.dto.SubmitQuery;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryManagementServiceTest {

  @Mock
  private QueryRepository queryRepository;

  @Mock
  private QueryResultsRepository queryResultsRepository;

  @Mock
  private FolioExecutionContext executionContext;

  @Mock
  private QueryExecutionService queryExecutionService;

  @Mock
  private QueryProcessorService queryProcessorService;

  @Mock
  private QueryResultsSorterService queryResultsSorterService;

  @Mock
  private EntityTypeService entityTypeService;

  @Mock
  private ResultSetService resultSetService;

  @Mock
  private FqlValidationService fqlValidationService;

  @Mock
  private CrossTenantQueryService crossTenantQueryService;

  private final int maxConfiguredQuerySize = 1000;

  @InjectMocks
  private QueryManagementService queryManagementService;

  @BeforeEach
  void setup() {
    queryManagementService.setMaxConfiguredQuerySize(maxConfiguredQuerySize);
  }

  @Test
  void shouldSaveValidFqlQuery() {
    UUID createdById = UUID.randomUUID();
    UUID entityTypeId = UUID.randomUUID();
    List<EntityTypeColumn> columns = List.of(
      new EntityTypeColumn().name("id").isIdColumn(true),
      new EntityTypeColumn().name("field1")
    );
    EntityType entityType = new EntityType()
      .name("test-entity")
      .crossTenantQueriesEnabled(true)
      .columns(columns);
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    int maxQuerySize = 100;
    SubmitQuery submitQuery = new SubmitQuery().entityTypeId(entityTypeId).fqlQuery(fqlQuery).maxSize(maxQuerySize);
    QueryIdentifier expectedIdentifier = new QueryIdentifier().queryId(UUID.randomUUID());
    when(executionContext.getUserId()).thenReturn(createdById);
    when(entityTypeService.getEntityTypeDefinition(entityTypeId, true, false)).thenReturn(entityType);
    when(fqlValidationService.validateFql(entityType, fqlQuery)).thenReturn(Map.of());
    when(queryRepository.saveQuery(any())).thenReturn(expectedIdentifier);
    QueryIdentifier actualIdentifier = queryManagementService.runFqlQueryAsync(submitQuery);
    assertEquals(expectedIdentifier, actualIdentifier);
    verify(queryExecutionService, times(1)).executeQueryAsync(queryCaptor.capture(), eq(entityType), eq(maxQuerySize));
    Query savedQuery = queryCaptor.getValue();
    assertFalse(savedQuery.crossTenant());
  }

  @Test
  void shouldSaveCrossTenantQuery() {
    UUID createdById = UUID.randomUUID();
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .name("test-entity")
      .crossTenantQueriesEnabled(true);
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    SubmitQuery submitQuery = new SubmitQuery().entityTypeId(entityTypeId).fqlQuery(fqlQuery);
    QueryIdentifier expectedIdentifier = new QueryIdentifier().queryId(UUID.randomUUID());
    when(executionContext.getUserId()).thenReturn(createdById);
    when(entityTypeService.getEntityTypeDefinition(entityTypeId, true, false)).thenReturn(entityType);
    when(fqlValidationService.validateFql(entityType, fqlQuery)).thenReturn(Map.of());
    when(queryRepository.saveQuery(any())).thenReturn(expectedIdentifier);
    when(crossTenantQueryService.ecsEnabled()).thenReturn(true);
    QueryIdentifier actualIdentifier = queryManagementService.runFqlQueryAsync(submitQuery);
    assertEquals(expectedIdentifier, actualIdentifier);
    verify(queryExecutionService, times(1)).executeQueryAsync(queryCaptor.capture(), eq(entityType), any());
    Query savedQuery = queryCaptor.getValue();
    assertTrue(savedQuery.crossTenant());
  }

  @Test
  void shouldUseConfiguredMaxQuerySizeIfMaxSizeNotProvidedInQuery() {
    UUID createdById = UUID.randomUUID();
    UUID entityTypeId = UUID.randomUUID();
    List<EntityTypeColumn> columns = List.of(
      new EntityTypeColumn().name("id").isIdColumn(true),
      new EntityTypeColumn().name("field1")
    );
    EntityType entityType = new EntityType()
      .name("test-entity")
      .columns(columns);
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    SubmitQuery submitQuery = new SubmitQuery().entityTypeId(entityTypeId).fqlQuery(fqlQuery);
    QueryIdentifier expectedIdentifier = new QueryIdentifier().queryId(UUID.randomUUID());
    when(executionContext.getUserId()).thenReturn(createdById);
    when(entityTypeService.getEntityTypeDefinition(entityTypeId,true, false)).thenReturn(entityType);
    when(fqlValidationService.validateFql(entityType, fqlQuery)).thenReturn(Map.of());
    when(queryRepository.saveQuery(any())).thenReturn(expectedIdentifier);
    QueryIdentifier actualIdentifier = queryManagementService.runFqlQueryAsync(submitQuery);
    assertEquals(expectedIdentifier, actualIdentifier);
    verify(queryExecutionService, times(1)).executeQueryAsync(any(), eq(entityType), eq(maxConfiguredQuerySize));
  }

  @Test
  void shouldAddIdColumnsToQueryIfNotPresent() {
    UUID createdById = UUID.randomUUID();
    UUID entityTypeId = UUID.randomUUID();
    List<EntityTypeColumn> columns = List.of(
      new EntityTypeColumn().name("id").isIdColumn(true),
      new EntityTypeColumn().name("field1")
    );
    EntityType entityType = new EntityType()
      .name("test-entity")
      .columns(columns);
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    int maxQuerySize = 10000;
    List<String> expectedFields = List.of("field1", "id");
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    SubmitQuery submitQuery = new SubmitQuery()
      .entityTypeId(entityTypeId)
      .fqlQuery(fqlQuery)
      .fields(new ArrayList<>(List.of("field1")))
      .maxSize(maxQuerySize);
    QueryIdentifier expectedIdentifier = new QueryIdentifier().queryId(UUID.randomUUID());
    when(executionContext.getUserId()).thenReturn(createdById);
    when(entityTypeService.getEntityTypeDefinition(entityTypeId,true, false)).thenReturn(entityType);
    when(fqlValidationService.validateFql(entityType, fqlQuery)).thenReturn(Map.of());
    when(queryRepository.saveQuery(any())).thenReturn(expectedIdentifier);
    QueryIdentifier actualIdentifier = queryManagementService.runFqlQueryAsync(submitQuery);
    assertEquals(expectedIdentifier, actualIdentifier);

    verify(queryExecutionService, times(1)).executeQueryAsync(queryCaptor.capture(), eq(entityType), eq(maxConfiguredQuerySize));
    assertEquals(expectedFields, queryCaptor.getValue().fields());
  }

  @Test
  void shouldNotSaveInvalidFqlQuery() {
    UUID createdById = UUID.randomUUID();
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType().name("test-entity");
    String fqlQuery = """
      {"field1": {"$xy": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    int maxQuerySize = 100;
    SubmitQuery submitQuery = new SubmitQuery().entityTypeId(entityTypeId).fqlQuery(fqlQuery);
    when(executionContext.getUserId()).thenReturn(createdById);
    when(entityTypeService.getEntityTypeDefinition(entityTypeId,true, false)).thenReturn(entityType);
    when(fqlValidationService.validateFql(entityType, fqlQuery)).thenReturn(Map.of("field1", "Field is invalid"));
    assertThrows(InvalidFqlException.class, () -> queryManagementService.runFqlQueryAsync(submitQuery));
    verify(queryExecutionService, times(0)).executeQueryAsync(any(), eq(entityType), eq(maxQuerySize));
  }

  @Test
  void shouldReturnQueryDetailsForValidQueryId() {
    boolean includeResults = false;
    int offset = 0;
    int limit = 100;
    Query expectedQuery = TestDataFixture.getMockQuery();
    Optional<QueryDetails> expectedDetails = Optional.of(new QueryDetails()
      .queryId(expectedQuery.queryId())
      .entityTypeId(expectedQuery.entityTypeId())
      .fqlQuery(expectedQuery.fqlQuery())
      .fields(expectedQuery.fields())
      .status(QueryDetails.StatusEnum.valueOf(expectedQuery.status().toString()))
      .startDate(offsetDateTimeAsDate(expectedQuery.startDate()))
      .totalRecords(5)
      .crossTenant(false)
      .content(List.of()));
    when(queryRepository.getQuery(expectedQuery.queryId(), false)).thenReturn(Optional.of(expectedQuery));
    when(queryResultsRepository.getQueryResultsCount(expectedQuery.queryId())).thenReturn(5);
    Optional<QueryDetails> actualDetails = queryManagementService.getQuery(expectedQuery.queryId(), includeResults, offset, limit);
    assertEquals(expectedDetails, actualDetails);
  }

  @Test
  void shouldReturnQueryDetailsWithContents() {
    boolean includeResults = true;
    int offset = 0;
    int limit = 100;
    List<List<String>> resultIds = List.of(
      List.of(UUID.randomUUID().toString()),
      List.of(UUID.randomUUID().toString())
    );
    List<Map<String, Object>> contents = List.of(
      Map.of("id", resultIds.get(0), "field1", "value1", "field2", "value2"),
      Map.of("id", resultIds.get(1), "field1", "value1", "field2", "value2")
    );
    List<String> tenantIds = List.of("tenant_01");
    Query expectedQuery = TestDataFixture.getMockQuery();
    Optional<QueryDetails> expectedDetails = Optional.of(new QueryDetails()
      .queryId(expectedQuery.queryId())
      .entityTypeId(expectedQuery.entityTypeId())
      .fqlQuery(expectedQuery.fqlQuery())
      .fields(expectedQuery.fields())
      .status(QueryDetails.StatusEnum.valueOf(expectedQuery.status().toString()))
      .startDate(offsetDateTimeAsDate(expectedQuery.startDate()))
      .totalRecords(2)
      .crossTenant(false)
      .content(contents));
    when(queryRepository.getQuery(expectedQuery.queryId(), false)).thenReturn(Optional.of(expectedQuery));
    when(queryResultsRepository.getQueryResultsCount(expectedQuery.queryId())).thenReturn(2);
    when(queryResultsRepository.getQueryResultIds(expectedQuery.queryId(), offset, limit)).thenReturn(resultIds);
    when(crossTenantQueryService.getTenantsToQuery(any(), eq(false))).thenReturn(tenantIds);
    when(resultSetService.getResultSet(expectedQuery.entityTypeId(), expectedQuery.fields(), resultIds, tenantIds)).thenReturn(contents);
    Optional<QueryDetails> actualDetails = queryManagementService.getQuery(expectedQuery.queryId(), includeResults, offset, limit);
    assertEquals(expectedDetails, actualDetails);
  }

  @Test
  void shouldRunSynchronousQueryAndReturnResultSet() {
    UUID entityTypeId = UUID.randomUUID();
    List<EntityTypeColumn> columns = List.of(
      new EntityTypeColumn().name("id").isIdColumn(true),
      new EntityTypeColumn().name("field1")
    );
    EntityType entityType = new EntityType()
      .name("test-entity")
      .columns(columns);
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    Integer defaultLimit = 100;
    List<UUID> resultIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    List<Map<String, Object>> expectedContent = List.of(
      Map.of("id", resultIds.get(0).toString(), "field1", "value1", "field2", "value2"),
      Map.of("id", resultIds.get(1).toString(), "field1", "value3", "field2", "value4")
    );
    List<String> fields = List.of("id", "field1", "field2");
    ResultsetPage expectedResults = new ResultsetPage().content(expectedContent);
    when(entityTypeService.getEntityTypeDefinition(entityTypeId,true, false)).thenReturn(entityType);
    when(fqlValidationService.validateFql(entityType, fqlQuery)).thenReturn(Map.of());
    when(queryProcessorService.processQuery(any(EntityType.class), eq(fqlQuery), eq(fields), isNull(), eq(defaultLimit))).thenReturn(expectedContent);
    ResultsetPage actualResults = queryManagementService.runFqlQuery(fqlQuery, entityTypeId, fields, null, defaultLimit);
    assertEquals(expectedResults, actualResults);
  }

  @Test
  void shouldRunSynchronousQueryAndReturnResultWithFieldAndIdsIfIdsNotProvided() {
    UUID entityTypeId = UUID.randomUUID();
    List<EntityTypeColumn> columns = List.of(
      new EntityTypeColumn().name("id").isIdColumn(true),
      new EntityTypeColumn().name("field1")
    );
    EntityType entityType = new EntityType()
      .name("test-entity")
      .columns(columns);
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    Integer defaultLimit = 100;
    List<UUID> resultIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    List<Map<String, Object>> expectedContent = List.of(
      Map.of("id", resultIds.get(0).toString(), "field1", "value1", "field2", "value2"),
      Map.of("id", resultIds.get(1).toString(), "field1", "value3", "field2", "value4")
    );
    List<String> fields = new ArrayList<>(List.of("field1", "field2"));
    ResultsetPage expectedResults = new ResultsetPage().content(expectedContent);
    when(entityTypeService.getEntityTypeDefinition(entityTypeId, true, false)).thenReturn(entityType);
    when(fqlValidationService.validateFql(entityType, fqlQuery)).thenReturn(Map.of());
    when(queryProcessorService.processQuery(any(EntityType.class), eq(fqlQuery), eq(List.of("field1", "field2", "id")), isNull(), eq(defaultLimit)))
      .thenReturn(expectedContent);
    ResultsetPage actualResults = queryManagementService.runFqlQuery(fqlQuery, entityTypeId, fields, null, defaultLimit);
    assertEquals(expectedResults, actualResults);
  }

  @Test
  void shouldRunSynchronousQueryAndReturnResultWithOnlyIdsIfFieldsNotProvided() {
    UUID entityTypeId = UUID.randomUUID();
    EntityTypeColumn idColumn = new EntityTypeColumn()
      .name("id")
      .isIdColumn(true);
    EntityType entityType = new EntityType()
      .name("test-entity")
      .columns(List.of(idColumn));
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    Integer defaultLimit = 100;
    List<UUID> resultIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    List<Map<String, Object>> expectedContent = List.of(
      Map.of("id", resultIds.get(0).toString()),
      Map.of("id", resultIds.get(1).toString())
    );
    ResultsetPage expectedResults = new ResultsetPage().content(expectedContent);
    when(entityTypeService.getEntityTypeDefinition(entityTypeId, true, false)).thenReturn(entityType);
    when(fqlValidationService.validateFql(entityType, fqlQuery)).thenReturn(Map.of());
    when(queryProcessorService.processQuery(any(EntityType.class), eq(fqlQuery), eq(List.of("id")), isNull(), eq(defaultLimit)))
      .thenReturn(expectedContent);
    ResultsetPage actualResults = queryManagementService.runFqlQuery(fqlQuery, entityTypeId, null, null, defaultLimit);
    assertEquals(expectedResults, actualResults);
  }

  @Test
  void shouldValidateQuery() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType().name("test-entity");
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    when(entityTypeService.getEntityTypeDefinition(entityTypeId, true, false)).thenReturn(entityType);
    when(fqlValidationService.validateFql(entityType, fqlQuery))
      .thenReturn(Map.of());
    assertDoesNotThrow(() -> queryManagementService.validateQuery(entityTypeId, fqlQuery));

  }

  @Test
  void validateQueryShouldThrowErrorIfEntityTypeNotFound() {
    UUID entityTypeId = UUID.randomUUID();
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    doThrow(new EntityTypeNotFoundException(entityTypeId))
      .when(entityTypeService).getEntityTypeDefinition(entityTypeId, true, false);
    assertThrows(EntityTypeNotFoundException.class,
      () -> queryManagementService.validateQuery(entityTypeId, fqlQuery));
  }

  @Test
  void validateQueryShouldThrowErrorForInvalidFql() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType().name("test-entity");
    String fqlQuery = """
      {"field1": {"$nn": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    when(entityTypeService.getEntityTypeDefinition(entityTypeId, true, false)).thenReturn(entityType);
    when(fqlValidationService.validateFql(entityType, fqlQuery))
      .thenReturn(Map.of("field1", "field is invalid"));
    assertThrows(InvalidFqlException.class,
      () -> queryManagementService.validateQuery(entityTypeId, fqlQuery));
  }

  @Test
  void shouldPurgeQueries() {
    List<UUID> queryIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    PurgedQueries expectedPurgedQueries = new PurgedQueries().deletedQueryIds(queryIds);
    when(queryRepository.getQueryIdsStartedBefore(Mockito.any())).thenReturn(queryIds);
    PurgedQueries actualPurgedQueries = queryManagementService.deleteOldQueries();
    verify(queryResultsRepository, times(1)).deleteQueryResults(queryIds);
    verify(queryRepository, times(1)).deleteQueries(queryIds);
    assertEquals(expectedPurgedQueries, actualPurgedQueries);
  }

  @Test
  void deleteQuerySuccessScenario() {
    Query query = TestDataFixture.getMockQuery(QueryStatus.SUCCESS, false);
    when(queryRepository.getQuery(query.queryId(), false)).thenReturn(Optional.of(query));
    queryManagementService.deleteQuery(query.queryId());
    verify(queryResultsRepository).deleteQueryResults(List.of(query.queryId()));
    verify(queryRepository).deleteQueries(List.of(query.queryId()));
  }

  @Test
  void deleteQueryInProgressScenario() {
    Query query = TestDataFixture.getMockQuery(QueryStatus.IN_PROGRESS, false);
    when(queryRepository.getQuery(query.queryId(), false)).thenReturn(Optional.of(query));
    queryManagementService.deleteQuery(query.queryId());
    verify(queryRepository, times(1)).updateQuery(eq(query.queryId()), eq(QueryStatus.CANCELLED), any(), eq(null));
  }

  @Test
  void deleteQueryNotFoundScenario() {
    UUID queryId = UUID.randomUUID();
    when(queryRepository.getQuery(queryId, false)).thenReturn(Optional.empty());
    assertThrows(QueryNotFoundException.class, () -> queryManagementService.deleteQuery(queryId));
  }

  @Test
  void shouldGetSortedIds() {
    Query query = TestDataFixture.getMockQuery(QueryStatus.SUCCESS, false);
    int offset = 0;
    int limit = 0;
    List<List<String>> expectedIds = List.of(
      List.of(UUID.randomUUID().toString()),
      List.of(UUID.randomUUID().toString())
    );

    when(queryRepository.getQuery(query.queryId(), false)).thenReturn(Optional.of(query));
    when(entityTypeService.getEntityTypeDefinition(query.entityTypeId(), true, false)).thenReturn(new EntityType());
    when(queryResultsSorterService.getSortedIds(query.queryId(), offset, limit)).thenReturn(expectedIds);

    List<List<String>> actualIds = queryManagementService.getSortedIds(query.queryId(), offset, limit);
    assertEquals(expectedIds, actualIds);
  }

  // TODO: possibly remove this test
  @Test
  @Disabled
  void getSortedIdsShouldThrowErrorIfEntityTypeNotFound() {
    Query query = TestDataFixture.getMockQuery(QueryStatus.SUCCESS, false);
    UUID queryId = query.queryId();
    int offset = 0;
    int limit = 0;
    when(queryRepository.getQuery(query.queryId(), false)).thenReturn(Optional.of(query));
    when(entityTypeService.getEntityTypeDefinition(query.entityTypeId(), false, false)).thenReturn(null);
    assertThrows(EntityTypeNotFoundException.class, () -> queryManagementService.getSortedIds(queryId, offset, limit));
  }

  @Test
  void getSortedIdsShouldThrowErrorIfQueryNotFound() {
    Query query = TestDataFixture.getMockQuery(QueryStatus.SUCCESS, false);
    UUID queryId = query.queryId();
    int offset = 0;
    int limit = 0;
    when(queryRepository.getQuery(query.queryId(), false)).thenThrow(new QueryNotFoundException(query.queryId()));
    assertThrows(QueryNotFoundException.class, () -> queryManagementService.getSortedIds(queryId, offset, limit));
  }

  @Test
  void shouldGetContents() {
    UUID entityTypeId = UUID.randomUUID();
    List<EntityTypeColumn> columns = List.of(
      new EntityTypeColumn().name("id").isIdColumn(true),
      new EntityTypeColumn().name("field1")
    );
    EntityType entityType = new EntityType().columns(columns);
    List<List<String>> ids = List.of(
      List.of(UUID.randomUUID().toString()),
      List.of(UUID.randomUUID().toString())
    );
    List<String> tenantIds = List.of("tenant_01");
    List<String> fields = List.of("id", "field1", "field2");
    List<Map<String, Object>> expectedContents = List.of(
      Map.of("id", UUID.randomUUID(), "field1", "value1", "field2", "value2"),
      Map.of("id", UUID.randomUUID(), "field1", "value3", "field2", "value4")
    );
    when(entityTypeService.getEntityTypeDefinition(entityTypeId,true, false)).thenReturn(entityType);
    when(crossTenantQueryService.getTenantsToQuery(any(EntityType.class), eq(false))).thenReturn(tenantIds);
    when(resultSetService.getResultSet(entityTypeId, fields, ids, tenantIds)).thenReturn(expectedContents);
    List<Map<String, Object>> actualContents = queryManagementService.getContents(entityTypeId, fields, ids);
    assertEquals(expectedContents, actualContents);
  }

  @Test
  void shouldGetContentsWithIdsIfIdsNotProvided() {
    UUID entityTypeId = UUID.randomUUID();
    List<EntityTypeColumn> columns = List.of(
      new EntityTypeColumn().name("id").isIdColumn(true),
      new EntityTypeColumn().name("field1")
    );
    EntityType entityType = new EntityType().columns(columns);
    List<List<String>> ids = List.of(
      List.of(UUID.randomUUID().toString()),
      List.of(UUID.randomUUID().toString())
    );
    List<String> tenantIds = List.of("tenant_01");
    List<String> providedFields = new ArrayList<>(List.of("field1", "field2"));
    List<String> expectedFields = List.of("field1", "field2", "id");
    List<Map<String, Object>> expectedContents = List.of(
      Map.of("id", UUID.randomUUID(), "field1", "value1", "field2", "value2"),
      Map.of("id", UUID.randomUUID(), "field1", "value3", "field2", "value4")
    );
    when(entityTypeService.getEntityTypeDefinition(entityTypeId, true, false)).thenReturn(entityType);
    when(crossTenantQueryService.getTenantsToQuery(any(EntityType.class), eq(false))).thenReturn(tenantIds);
    when(resultSetService.getResultSet(entityTypeId, expectedFields, ids, tenantIds)).thenReturn(expectedContents);
    List<Map<String, Object>> actualContents = queryManagementService.getContents(entityTypeId, providedFields, ids);
    assertEquals(expectedContents, actualContents);
  }

  private static Date offsetDateTimeAsDate(OffsetDateTime offsetDateTime) {
    return offsetDateTime == null ? null : Date.from(offsetDateTime.toInstant());
  }
}
