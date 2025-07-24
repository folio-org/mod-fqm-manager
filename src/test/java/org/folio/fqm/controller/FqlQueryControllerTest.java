package org.folio.fqm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.folio.fqm.exception.InvalidFqlException;
import org.folio.fqm.exception.QueryNotFoundException;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.resource.FqlQueryController;
import org.folio.fqm.service.QueryManagementService;
import org.folio.fqm.service.QueryProcessorService;
import org.folio.querytool.domain.dto.ContentsRequest;
import org.folio.querytool.domain.dto.QueryDetails;
import org.folio.querytool.domain.dto.QueryIdentifier;
import org.folio.querytool.domain.dto.ResultsetPage;
import org.folio.querytool.domain.dto.SubmitQuery;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.integration.XOkapiHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FqlQueryController.class)
class FqlQueryControllerTest {
  private static final String TENANT_ID = "tenant_01";

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private QueryManagementService queryManagementService;
  @MockitoBean
  private QueryProcessorService queryProcessorService;
  @MockitoBean
  private FolioExecutionContext executionContext;

  @Test
  void shouldReturnQueryIdentifierForValidFqlQuery() throws Exception {
    UUID expectedId = UUID.randomUUID();
    UUID entityTypeId = UUID.randomUUID();
    String fqlQuery = """
                      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
                      """;
    SubmitQuery submitQuery = new SubmitQuery().entityTypeId(entityTypeId).fqlQuery(fqlQuery);
    QueryIdentifier expectedIdentifier = new QueryIdentifier().queryId(expectedId);
    when(queryManagementService.runFqlQueryAsync(submitQuery)).thenReturn(expectedIdentifier);
    RequestBuilder builder = post("/query").contentType(MediaType.APPLICATION_JSON)
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .content(new ObjectMapper().writeValueAsString(submitQuery));
    mockMvc.perform(builder)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.queryId", is(expectedId.toString())));
  }

  @Test
  void shouldReturn400ErrorForRequestMissingFqlQuery() throws Exception {
    UUID entityTypeId = UUID.randomUUID();
    SubmitQuery submitQuery = new SubmitQuery().entityTypeId(entityTypeId);
    RequestBuilder builder = post("/query").contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .content(new ObjectMapper().writeValueAsString(submitQuery));
    mockMvc.perform(builder)
      .andExpect(status().isBadRequest());
  }

  @Test
  void shouldGetQueryDetailsForValidQueryId() throws Exception {
    UUID queryId = UUID.randomUUID();
    UUID entityTypeId = UUID.randomUUID();
    Integer defaultOffset = 0;
    Integer defaultLimit = 100;
    boolean includeResults = false;
    Optional<QueryDetails> expectedDetails = Optional.of(new QueryDetails().entityTypeId(entityTypeId).status(QueryDetails.StatusEnum.IN_PROGRESS));
    when(queryManagementService.getQuery(queryId, includeResults, defaultOffset, defaultLimit)).thenReturn(expectedDetails);
    RequestBuilder builder = get("/query/" + queryId).contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);
    mockMvc.perform(builder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.entityTypeId", is(entityTypeId.toString())))
      .andExpect(jsonPath("$.status", is("IN_PROGRESS")));
  }

  @Test
  void shouldGetQueryDetailsForValidQueryIdWithParameters() throws Exception {
    UUID queryId = UUID.randomUUID();
    UUID entityTypeId = UUID.randomUUID();
    Boolean includeResults = false;
    Integer offset = 0;
    Integer limit = 1;
    Optional<QueryDetails> expectedDetails = Optional.of(new QueryDetails().entityTypeId(entityTypeId).status(QueryDetails.StatusEnum.IN_PROGRESS));
    when(queryManagementService.getQuery(queryId, includeResults, offset, limit)).thenReturn(expectedDetails);
    RequestBuilder builder = get("/query/" + queryId).contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .queryParam("includeResults", includeResults.toString())
      .queryParam("offset", offset.toString())
      .queryParam("limit", limit.toString());
    mockMvc.perform(builder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.entityTypeId", is(entityTypeId.toString())))
      .andExpect(jsonPath("$.status", is("IN_PROGRESS")));
  }

  @Test
  void shouldReturn404NotFoundForMissingQuery() throws Exception {
    UUID queryId = UUID.randomUUID();
    int defaultOffset = 0;
    int defaultLimit = 100;
    boolean includeResults = false;
    when(queryManagementService.getQuery(queryId, includeResults, defaultOffset, defaultLimit)).thenReturn(Optional.empty());
    RequestBuilder builder = get("/query/" + queryId).contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);
    mockMvc.perform(builder)
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldRunSynchronousQueryAndReturnResults() throws Exception{
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
    List<String> fieldsList = List.of("field1", "field2");
    String fields = "field1,field2";
    ResultsetPage expectedResults = new ResultsetPage().content(expectedContent);
    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(queryManagementService.runFqlQuery(fqlQuery, entityTypeId, fieldsList, defaultLimit)).thenReturn(expectedResults);
    RequestBuilder builder = get("/query").contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .queryParam("query", fqlQuery)
      .queryParam("entityTypeId", entityTypeId.toString())
      .queryParam("fields", fields);
    mockMvc.perform(builder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content", is(expectedResults.getContent())));
  }

  @Test
  void shouldRunSynchronousQueryAndReturnOnlyIdsIfFieldsNotProvided() throws Exception{
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
    ResultsetPage expectedResults = new ResultsetPage().content(expectedContent);
    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(queryManagementService.runFqlQuery(fqlQuery, entityTypeId, null, defaultLimit)).thenReturn(expectedResults);
    RequestBuilder builder = get("/query").contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .queryParam("query", fqlQuery)
      .queryParam("entityTypeId", entityTypeId.toString());
    mockMvc.perform(builder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content", is(expectedResults.getContent())));
  }

  @Test
  void shouldRunSynchronousQueryWithOptionalParametersAndReturnResults() throws Exception {
    UUID entityTypeId = UUID.randomUUID();
    UUID afterUUID = UUID.randomUUID();
    String fqlQuery = """
                      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
                      """;
    Integer limit = 100;
    List<UUID> resultIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    List<Map<String, Object>> expectedContent = List.of(
      Map.of("id", resultIds.get(0).toString(), "field1", "value1", "field2", "value2"),
      Map.of("id", resultIds.get(1).toString(), "field1", "value3", "field2", "value4")
    );
    List<String> fieldsList = List.of("id", "field1", "field2");
    String fields = "id,field1,field2";
    ResultsetPage expectedResults = new ResultsetPage().content(expectedContent);
    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(queryManagementService.runFqlQuery(fqlQuery, entityTypeId, fieldsList, limit)).thenReturn(expectedResults);
    RequestBuilder builder = get("/query").contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .queryParam("query", fqlQuery)
      .queryParam("entityTypeId", entityTypeId.toString())
      .queryParam("fields", fields)
      .queryParam("afterId", afterUUID.toString())
      .queryParam("limit", limit.toString());
    mockMvc.perform(builder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content", is(expectedResults.getContent())));
  }

  @Test
  void getQueryShouldReturn400ForRequestMissingRequiredParameters() throws Exception {
    String fqlQuery = """
                      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
                      """;
    UUID entityTypeId = UUID.randomUUID();
    RequestBuilder builderWithoutEntityTypeId = get("/query").contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .queryParam("query", fqlQuery);
    mockMvc.perform(builderWithoutEntityTypeId)
      .andExpect(status().isBadRequest());

    RequestBuilder builderWithoutQuery = get("/query").contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .queryParam("entityTypeId", entityTypeId.toString());
    mockMvc.perform(builderWithoutQuery)
      .andExpect(status().isBadRequest());
  }

  @Test
  void getQueryShouldThrowErrorForMissingFieldInEntityType() throws Exception {
    String fqlQuery = """
                      {"field1": {"$in": ["value1", "value2", "value3", "value4", "value5" ] }}
                      """;
    UUID entityTypeId = UUID.randomUUID();

    doThrow(new InvalidFqlException(fqlQuery, Map.of("field1", "Field not present")))
      .when(queryManagementService).runFqlQuery(fqlQuery, entityTypeId, null, 100);

    RequestBuilder builder = get("/query").contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .queryParam("query", fqlQuery)
      .queryParam("entityTypeId", entityTypeId.toString());

    mockMvc.perform(builder)
      .andExpect(status().isBadRequest());
  }

  @Test
  void testDeleteQuery() throws Exception {
    UUID queryId = UUID.randomUUID();
    doNothing().when(queryManagementService).deleteQuery(queryId);
    RequestBuilder builder = delete("/query/" + queryId).contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .header(XOkapiHeaders.USER_ID, UUID.randomUUID());
    mockMvc.perform(builder)
      .andExpect(status().isNoContent());
  }

  @Test
  void testDeleteQueryNotFound() throws Exception {
    UUID queryId = UUID.randomUUID();
    doThrow(new QueryNotFoundException(queryId)).when(queryManagementService).deleteQuery(queryId);
    RequestBuilder builder = delete("/query/" + queryId).contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .header(XOkapiHeaders.USER_ID, UUID.randomUUID());
    mockMvc.perform(builder)
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldGetSortedIds() throws Exception {
    UUID queryId = UUID.randomUUID();
    Integer offset = 0;
    Integer limit = 100;
    List<List<String>> expectedIds = List.of(
      List.of(UUID.randomUUID().toString()),
      List.of(UUID.randomUUID().toString())
    );
    when(queryManagementService.getSortedIds(queryId, offset, limit)).thenReturn(expectedIds);
    RequestBuilder builder = get("/query/" + queryId + "/sortedIds").contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .queryParam("offset", offset.toString())
      .queryParam("limit", limit.toString());
    mockMvc.perform(builder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.[0]", is(expectedIds.get(0))))
      .andExpect(jsonPath("$.[1]", is(expectedIds.get(1))));
  }

  @Test
  void getSortedIdsShouldReturn404WhenQueryNotFound() throws Exception {
    UUID queryId = UUID.randomUUID();
    Integer offset = 0;
    Integer limit = 100;
    when(queryManagementService.getSortedIds(queryId, offset, limit)).thenThrow(new QueryNotFoundException(queryId));
    RequestBuilder builder = get("/query/" + queryId + "/sortedIds").contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .queryParam("offset", offset.toString())
      .queryParam("limit", limit.toString());
    mockMvc.perform(builder)
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldGetContents() throws Exception {
    UUID entityTypeId = UUID.randomUUID();
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    List<String> fields = List.of("id", "field1", "field2");
    List<List<String>> ids = List.of(
      List.of(id1.toString()),
      List.of(id2.toString())
    );
    ContentsRequest contentsRequest = new ContentsRequest().entityTypeId(entityTypeId)
      .fields(fields)
      .ids(ids);
    List<Map<String, Object>> expectedContent = List.of(
      Map.of("id", id1, "field1", "value1", "field2", "value2"),
      Map.of("id", id2, "field1", "value3", "field2", "value4")
    );
    when(queryManagementService.getContents(entityTypeId, fields, ids, null, false, false)).thenReturn(expectedContent);
    RequestBuilder builder = post("/query/contents").contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString(contentsRequest));
    mockMvc.perform(builder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.[0].id", is(expectedContent.get(0).get("id").toString())))
      .andExpect(jsonPath("$.[0].field1", is(expectedContent.get(0).get("field1"))))
      .andExpect(jsonPath("$.[0].field2", is(expectedContent.get(0).get("field2"))))
      .andExpect(jsonPath("$.[1].id", is(expectedContent.get(1).get("id").toString())))
      .andExpect(jsonPath("$.[1].field1", is(expectedContent.get(1).get("field1"))))
      .andExpect(jsonPath("$.[1].field2", is(expectedContent.get(1).get("field2"))));
  }


  @Test
  void getContentsShouldReturn404WhenEntityTypeNotFound() throws Exception {
    UUID entityTypeId = UUID.randomUUID();
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    List<String> fields = List.of("id", "field1", "field2");
    List<List<String>> ids = List.of(
      List.of(id1.toString()),
      List.of(id2.toString())
    );
    ContentsRequest contentsRequest = new ContentsRequest().entityTypeId(entityTypeId)
      .fields(fields)
      .ids(ids);
    when(queryManagementService.getContents(entityTypeId, fields, ids, null, false, false)).thenThrow(new EntityTypeNotFoundException(entityTypeId));
    RequestBuilder builder = post("/query/contents").contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString(contentsRequest));
    mockMvc.perform(builder)
      .andExpect(status().isNotFound());
  }

  @Test
  void getContentsShouldReturn400WhenParametersNotProvided() throws Exception {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    List<String> fields = List.of("id", "field1", "field2");
    List<List<String>> ids = List.of(
      List.of(id1.toString()),
      List.of(id2.toString())
    );
    ContentsRequest contentsRequest = new ContentsRequest().fields(fields)
      .ids(ids);
    RequestBuilder builder = post("/query/contents").contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString(contentsRequest));
    mockMvc.perform(builder)
      .andExpect(status().isBadRequest());
  }

  @Test
  void shouldGetContentsPrivileged() throws Exception {
    UUID entityTypeId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID id1 = UUID.randomUUID();
    List<String> fields = List.of("id");
    List<List<String>> ids = List.of(
      List.of(id1.toString())
    );
    ContentsRequest contentsRequest = new ContentsRequest().entityTypeId(entityTypeId)
      .fields(fields)
      .ids(ids)
      .userId(userId);
    List<Map<String, Object>> expectedContent = List.of(
      Map.of("id", id1)
    );
    when(queryManagementService.getContents(entityTypeId, fields, ids, userId, false, true)).thenReturn(expectedContent);
    RequestBuilder builder = post("/query/contents/privileged").contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString(contentsRequest));
    mockMvc.perform(builder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.[0].id", is(expectedContent.get(0).get("id").toString())));
  }

  @Test
  void shouldReturn400WhenMissingUserIdForContentsPrivileged() throws Exception {
    UUID entityTypeId = UUID.randomUUID();
    UUID id1 = UUID.randomUUID();
    List<String> fields = List.of("id");
    List<List<String>> ids = List.of(
      List.of(id1.toString())
    );
    ContentsRequest contentsRequest = new ContentsRequest().entityTypeId(entityTypeId)
      .fields(fields)
      .ids(ids);
    RequestBuilder builder = post("/query/contents/privileged").contentType(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString(contentsRequest));
    mockMvc.perform(builder)
      .andExpect(status().isBadRequest());
  }
}
