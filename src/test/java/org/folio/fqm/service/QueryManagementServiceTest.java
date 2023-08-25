package org.folio.fqm.service;

import org.folio.fql.FqlService;
import org.folio.fqm.lib.service.QueryProcessorService;
import org.folio.fqm.util.TestDataFixture;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.domain.dto.PurgedQueries;
import org.folio.fqm.exception.InvalidFqlException;
import org.folio.fqm.exception.QueryNotFoundException;
import org.folio.fqm.lib.service.FqlValidationService;
import org.folio.fqm.lib.service.ResultSetService;
import org.folio.fqm.repository.QueryRepository;
import org.folio.fqm.repository.QueryResultsRepository;
import org.folio.querytool.domain.dto.QueryDetails;
import org.folio.querytool.domain.dto.QueryIdentifier;
import org.folio.querytool.domain.dto.ResultsetPage;
import org.folio.querytool.domain.dto.SubmitQuery;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryManagementServiceTest {
  @InjectMocks
  private QueryManagementService queryManagementService;

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
  private ResultSetService resultSetService;

  @Mock
  private FqlValidationService fqlValidationService;

  @Mock
  private FqlService fqlService;

  @Test
  void shouldSaveValidFqlQuery() {
    String tenantId = "tenant_01";
    UUID createdById = UUID.randomUUID();
    UUID entityTypeId = UUID.randomUUID();
    String fqlQuery = """
      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    SubmitQuery submitQuery = new SubmitQuery().entityTypeId(entityTypeId).fqlQuery(fqlQuery);
    QueryIdentifier expectedIdentifier = new QueryIdentifier().queryId(UUID.randomUUID());
    when(executionContext.getUserId()).thenReturn(createdById);
    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(fqlValidationService.validateFql(tenantId, entityTypeId, fqlQuery)).thenReturn(Map.of());
    when(queryRepository.saveQuery(any())).thenReturn(expectedIdentifier);
    QueryIdentifier actualIdentifier = queryManagementService.runFqlQueryAsync(submitQuery);
    assertEquals(expectedIdentifier, actualIdentifier);
    verify(queryExecutionService, times(1)).executeQueryAsync(any());
  }

  @Test
  void shouldNotSaveInvalidFqlQuery() {
    String tenantId = "tenant_01";
    UUID createdById = UUID.randomUUID();
    UUID entityTypeId = UUID.randomUUID();
    String fqlQuery = """
      {"field1": {"$xy": ["value1", "value2", "value3", "value4", "value5" ] }}
      """;
    SubmitQuery submitQuery = new SubmitQuery().entityTypeId(entityTypeId).fqlQuery(fqlQuery);
    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(executionContext.getUserId()).thenReturn(createdById);
    when(fqlValidationService.validateFql(tenantId, entityTypeId, fqlQuery)).thenReturn(Map.of("field1", "Field is invalid"));
    assertThrows(InvalidFqlException.class, () -> queryManagementService.runFqlQueryAsync(submitQuery));
    verify(queryExecutionService, times(0)).executeQueryAsync(any());
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
    List<UUID> resultIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    List<Map<String, Object>> contents = List.of(
      Map.of("id", resultIds.get(0), "field1", "value1", "field2", "value2"),
      Map.of("id", resultIds.get(1), "field1", "value1", "field2", "value2")
    );
    Query expectedQuery = TestDataFixture.getMockQuery();
    Optional<QueryDetails> expectedDetails = Optional.of(new QueryDetails()
      .queryId(expectedQuery.queryId())
      .entityTypeId(expectedQuery.entityTypeId())
      .fqlQuery(expectedQuery.fqlQuery())
      .fields(expectedQuery.fields())
      .status(QueryDetails.StatusEnum.valueOf(expectedQuery.status().toString()))
      .startDate(offsetDateTimeAsDate(expectedQuery.startDate()))
      .totalRecords(2)
      .content(contents));
    when(queryRepository.getQuery(expectedQuery.queryId(), false)).thenReturn(Optional.of(expectedQuery));
    when(queryResultsRepository.getQueryResultsCount(expectedQuery.queryId())).thenReturn(2);
    when(queryResultsRepository.getQueryResultIds(expectedQuery.queryId(), offset, limit)).thenReturn(resultIds);
    when(resultSetService.getResultSet(any(), eq(expectedQuery.entityTypeId()), eq(expectedQuery.fields()), eq(resultIds))).thenReturn(contents);
    Optional<QueryDetails> actualDetails = queryManagementService.getQuery(expectedQuery.queryId(), includeResults, offset, limit);
    assertEquals(expectedDetails, actualDetails);
  }

  @Test
  void shouldReturnQueryContentsWithOnlyIdsForEmptyFields() {
    boolean includeResults = true;
    int offset = 0;
    int limit = 100;
    List<UUID> resultIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    List<Map<String, Object>> contents = List.of(
      Map.of("id", resultIds.get(0)),
      Map.of("id", resultIds.get(1))
    );
    // Query's fields parameter is empty, so only ids should be requested and retrieved
    List<String> fields = new ArrayList<>();
    Query expectedQuery = TestDataFixture.getMockQuery(fields);
    Optional<QueryDetails> expectedDetails = Optional.of(new QueryDetails()
      .queryId(expectedQuery.queryId())
      .entityTypeId(expectedQuery.entityTypeId())
      .fqlQuery(expectedQuery.fqlQuery())
      .fields(expectedQuery.fields())
      .status(QueryDetails.StatusEnum.valueOf(expectedQuery.status().toString()))
      .startDate(offsetDateTimeAsDate(expectedQuery.startDate()))
      .totalRecords(2)
      .content(contents));
    when(queryRepository.getQuery(expectedQuery.queryId(), false)).thenReturn(Optional.of(expectedQuery));
    when(queryResultsRepository.getQueryResultsCount(expectedQuery.queryId())).thenReturn(2);
    when(queryResultsRepository.getQueryResultIds(expectedQuery.queryId(), offset, limit)).thenReturn(resultIds);
    when(resultSetService.getResultSet(any(), eq(expectedQuery.entityTypeId()), eq(List.of("id")), eq(resultIds))).thenReturn(contents);
    Optional<QueryDetails> actualDetails = queryManagementService.getQuery(expectedQuery.queryId(), includeResults, offset, limit);
    assertEquals(expectedDetails, actualDetails);
  }

  @Test
  void shouldReturnQueryContentsWithOnlyIdsForNullFields() {
    boolean includeResults = true;
    int offset = 0;
    int limit = 100;
    List<UUID> resultIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    List<Map<String, Object>> contents = List.of(
      Map.of("id", resultIds.get(0)),
      Map.of("id", resultIds.get(1))
    );
    // Query's fields parameter is null, so only ids should be requested and retrieved
    List<String> fields = null;
    Query expectedQuery = TestDataFixture.getMockQuery(fields);
    Optional<QueryDetails> expectedDetails = Optional.of(new QueryDetails()
      .queryId(expectedQuery.queryId())
      .entityTypeId(expectedQuery.entityTypeId())
      .fqlQuery(expectedQuery.fqlQuery())
      .fields(expectedQuery.fields())
      .status(QueryDetails.StatusEnum.valueOf(expectedQuery.status().toString()))
      .startDate(offsetDateTimeAsDate(expectedQuery.startDate()))
      .totalRecords(2)
      .content(contents));
    when(queryRepository.getQuery(expectedQuery.queryId(), false)).thenReturn(Optional.of(expectedQuery));
    when(queryResultsRepository.getQueryResultsCount(expectedQuery.queryId())).thenReturn(2);
    when(queryResultsRepository.getQueryResultIds(expectedQuery.queryId(), offset, limit)).thenReturn(resultIds);
    when(resultSetService.getResultSet(any(), eq(expectedQuery.entityTypeId()), eq(List.of("id")), eq(resultIds))).thenReturn(contents);
    Optional<QueryDetails> actualDetails = queryManagementService.getQuery(expectedQuery.queryId(), includeResults, offset, limit);
    assertEquals(expectedDetails, actualDetails);
  }

  // This test ensures that record ids are retrieved even if they are not included in the query's fields parameter
  @Test
  void shouldReturnRequestedFieldsWithIdsIfIdsNotIncludedInFields() {
    boolean includeResults = true;
    int offset = 0;
    int limit = 100;
    List<UUID> resultIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    List<Map<String, Object>> contents = List.of(
      Map.of("id", resultIds.get(0), "field1", "value1", "field2", "value2"),
      Map.of("id", resultIds.get(1), "field1", "value1", "field2", "value2")
    );
    List<String> fields = new ArrayList<>(List.of("field1", "field2"));
    List<String> expectedFields = new ArrayList<>(List.of("field1", "field2", "id"));
    Query expectedQuery = TestDataFixture.getMockQuery(fields);
    Optional<QueryDetails> expectedDetails = Optional.of(new QueryDetails()
      .queryId(expectedQuery.queryId())
      .entityTypeId(expectedQuery.entityTypeId())
      .fqlQuery(expectedQuery.fqlQuery())
      .fields(expectedQuery.fields())
      .status(QueryDetails.StatusEnum.valueOf(expectedQuery.status().toString()))
      .startDate(offsetDateTimeAsDate(expectedQuery.startDate()))
      .totalRecords(2)
      .content(contents));
    when(queryRepository.getQuery(expectedQuery.queryId(), false)).thenReturn(Optional.of(expectedQuery));
    when(queryResultsRepository.getQueryResultsCount(expectedQuery.queryId())).thenReturn(2);
    when(queryResultsRepository.getQueryResultIds(expectedQuery.queryId(), offset, limit)).thenReturn(resultIds);
    when(resultSetService.getResultSet(any(), eq(expectedQuery.entityTypeId()), eq(expectedFields), eq(resultIds))).thenReturn(contents);
    Optional<QueryDetails> actualDetails = queryManagementService.getQuery(expectedQuery.queryId(), includeResults, offset, limit);
    assertEquals(expectedDetails, actualDetails);
  }

  @Test
  void shouldRunSynchronousQueryAndReturnResultSet() {
    String tenantId = "tenant_01";
    UUID entityTypeId = UUID.randomUUID();
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
    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(queryProcessorService.processQuery(tenantId, entityTypeId, fqlQuery, fields, null, defaultLimit)).thenReturn(expectedContent);
    ResultsetPage actualResults = queryManagementService.runFqlQuery(fqlQuery, entityTypeId, fields, null, defaultLimit);
    assertEquals(expectedResults, actualResults);
  }

  @Test
  void shouldRunSynchronousQueryAndReturnResultWithFieldAndIdsIfIdsNotProvided() {
    String tenantId = "tenant_01";
    UUID entityTypeId = UUID.randomUUID();
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
    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(queryProcessorService.processQuery(tenantId, entityTypeId, fqlQuery, List.of("field1", "field2", "id"), null, defaultLimit))
      .thenReturn(expectedContent);
    ResultsetPage actualResults = queryManagementService.runFqlQuery(fqlQuery, entityTypeId, fields, null, defaultLimit);
    assertEquals(expectedResults, actualResults);
  }

  @Test
  void shouldRunSynchronousQueryAndReturnResultWithOnlyIdsIfFieldsNotProvided() {
    String tenantId = "tenant_01";
    UUID entityTypeId = UUID.randomUUID();
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
    when(executionContext.getTenantId()).thenReturn(tenantId);
    when(queryProcessorService.processQuery(tenantId, entityTypeId, fqlQuery, List.of("id"), null, defaultLimit))
      .thenReturn(expectedContent);
    ResultsetPage actualResults = queryManagementService.runFqlQuery(fqlQuery, entityTypeId, null, null, defaultLimit);
    assertEquals(expectedResults, actualResults);
  }

  @Test
  void runFqlQueryShouldThrowErrorIfEntityTypeIdNotProvided() {
    String tenantId = "tenant_01";
    String fqlQuery = """
                      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
                      """;
    Integer defaultLimit = 100;
    when(executionContext.getTenantId()).thenReturn(tenantId);
    doThrow(new InvalidFqlException(fqlQuery, Map.of("field1", "Field not present")))
      .when(fqlValidationService).validateFql(tenantId, null, fqlQuery);
    assertThrows(InvalidFqlException.class,
      () -> queryManagementService.runFqlQuery(fqlQuery, null, null, null, defaultLimit));
  }

  @Test
  void runFqlQueryShouldThrowErrorIEntityTypeIdNotProvided() {
    String tenantId = "tenant_01";
    UUID entityTypeId = UUID.randomUUID();
    Integer defaultLimit = 100;
    when(executionContext.getTenantId()).thenReturn(tenantId);
    doThrow(new InvalidFqlException(null, Map.of("field1", "Field not present")))
      .when(fqlValidationService).validateFql(tenantId, entityTypeId, null);
    assertThrows(InvalidFqlException.class,
      () -> queryManagementService.runFqlQuery(null, entityTypeId, null, null, defaultLimit));
  }

  @Test
  void shouldPurgeQueries() {
    List<UUID> queryIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    PurgedQueries expectedPurgedQueries = new PurgedQueries().deletedQueryIds(queryIds);
    when(queryRepository.getQueryIdsCompletedBefore(Mockito.any())).thenReturn(queryIds);
    PurgedQueries actualPurgedQueries = queryManagementService.deleteOldQueries();
    verify(queryResultsRepository, times(1)).deleteQueryResults(queryIds);
    verify(queryRepository, times(1)).deleteQueries(queryIds);
    assertEquals(expectedPurgedQueries, actualPurgedQueries);
  }

  @Test
  void deleteQuerySuccessScenario() {
    Query query = TestDataFixture.getMockQuery(QueryStatus.SUCCESS);
    when(queryRepository.getQuery(query.queryId(), false)).thenReturn(Optional.of(query));
    queryManagementService.deleteQuery(query.queryId());
    verify(queryResultsRepository).deleteQueryResults(List.of(query.queryId()));
    verify(queryRepository).deleteQueries(List.of(query.queryId()));
  }

  @Test
  void deleteQueryInProgressScenario() {
    Query query = TestDataFixture.getMockQuery(QueryStatus.IN_PROGRESS);
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

  private static Date offsetDateTimeAsDate(OffsetDateTime offsetDateTime) {
    return offsetDateTime == null ? null : Date.from(offsetDateTime.toInstant());
  }
}
