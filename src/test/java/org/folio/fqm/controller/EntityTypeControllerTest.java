package org.folio.fqm.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.folio.fqm.domain.dto.EntityTypeSummary;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.exception.FieldNotFoundException;
import org.folio.fqm.resource.EntityTypeController;
import org.folio.fqm.service.EntityTypeService;
import org.folio.querytool.domain.dto.ColumnValues;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.StringType;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.integration.XOkapiHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@WebMvcTest(EntityTypeController.class)
class EntityTypeControllerTest {

  private static final String GET_DEFINITION_URL = "/entity-types/{entity-type-id}";

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private EntityTypeService entityTypeService;

  @MockBean
  private FolioExecutionContext folioExecutionContext;

  @Test
  void shouldReturnEntityTypeDefinition() throws Exception {
    UUID id = UUID.randomUUID();
    String derivedTableName = "derived_table_01";
    EntityTypeColumn col = getEntityTypeColumn();
    EntityType mockDefinition = getEntityType(col);
    when(folioExecutionContext.getTenantId()).thenReturn("tenant_01");
    when(entityTypeService.getEntityTypeDefinition(id)).thenReturn(Optional.of(mockDefinition));
    RequestBuilder builder = MockMvcRequestBuilders
      .get(GET_DEFINITION_URL, id)
      .accept(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, "tenant_01");
    mockMvc
      .perform(builder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name", is(derivedTableName)))
      .andExpect(jsonPath("$.labelAlias", is(mockDefinition.getLabelAlias())))
      .andExpect(jsonPath("$.columns[0].name", is(col.getName())))
      .andExpect(jsonPath("$.columns[0].dataType.dataType", is(col.getDataType().getDataType())))
      .andExpect(jsonPath("$.columns[0].labelAlias", is(col.getLabelAlias())))
      .andExpect(jsonPath("$.columns[0].visibleByDefault", is(col.getVisibleByDefault())));
  }

  @Test
  void shouldReturnNotFoundErrorWhenEntityNotFound() throws Exception {
    UUID id = UUID.randomUUID();
    when(folioExecutionContext.getTenantId()).thenReturn("tenant_01");
    when(entityTypeService.getEntityTypeDefinition(UUID.randomUUID())).thenReturn(Optional.empty());
    RequestBuilder builder = MockMvcRequestBuilders
      .get(GET_DEFINITION_URL, id)
      .accept(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, "tenant_01");
    mockMvc.perform(builder).andExpect(status().isNotFound());
  }

  @Test
  void shouldGetEntityTypeSummaryForValidIds() throws Exception {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Set<UUID> ids = Set.of(id1, id2);
    List<EntityTypeSummary> expectedSummary = List.of(
      new EntityTypeSummary().id(id1).label("label_01"),
      new EntityTypeSummary().id(id2).label("label_02")
    );
    RequestBuilder requestBuilder = MockMvcRequestBuilders
      .get("/entity-types")
      .header(XOkapiHeaders.TENANT, "tenant_01")
      .queryParam("ids", id1.toString(), id2.toString());
    when(entityTypeService.getEntityTypeSummary(ids, false)).thenReturn(expectedSummary);
    mockMvc
      .perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.[0].id", is(expectedSummary.get(0).getId().toString())))
      .andExpect(jsonPath("$.[0].label", is(expectedSummary.get(0).getLabel())))
      .andExpect(jsonPath("$.[0].missingPermissions").doesNotExist())
      .andExpect(jsonPath("$.[1].id", is(expectedSummary.get(1).getId().toString())))
      .andExpect(jsonPath("$.[1].label", is(expectedSummary.get(1).getLabel())))
      .andExpect(jsonPath("$.[1].missingPermissions").doesNotExist());

    verify(entityTypeService, times(1)).getEntityTypeSummary(ids, false);
    verifyNoMoreInteractions(entityTypeService);
  }

  @Test
  void testSummaryIncludesMissingPermissionsIfRequested() throws Exception {
    RequestBuilder requestBuilder = MockMvcRequestBuilders
      .get("/entity-types")
      .header(XOkapiHeaders.TENANT, "tenant_01")
      .queryParam("includeInaccessible", "true");

    when(entityTypeService.getEntityTypeSummary(Set.of(), true)).thenReturn(List.of());

    // all we really want to check here is that the includeInaccessible parameter is correctly unboxed
    // no sense making fake data to pass through to ourself; that's redundant with shouldGetEntityTypeSummaryForValidIds
    mockMvc.perform(requestBuilder).andExpect(status().isOk());

    verify(entityTypeService, times(1)).getEntityTypeSummary(Set.of(), true);
    verifyNoMoreInteractions(entityTypeService);
  }

  @Test
  void shouldReturnEmptyListWhenEntityTypeSummaryNotFound() throws Exception {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Set<UUID> ids = Set.of(id1, id2);
    List<EntityTypeSummary> expectedSummary = List.of();
    RequestBuilder requestBuilder = MockMvcRequestBuilders
      .get("/entity-types")
      .header(XOkapiHeaders.TENANT, "tenant_01")
      .queryParam("ids", id1.toString(), id2.toString());
    when(entityTypeService.getEntityTypeSummary(ids, false)).thenReturn(expectedSummary);
    mockMvc.perform(requestBuilder).andExpect(status().isOk()).andExpect(jsonPath("$", is(expectedSummary)));
  }

  @Test
  void shouldReturnColumnValuesWithLabel() throws Exception {
    UUID entityTypeId = UUID.randomUUID();
    String columnName = "column_name";
    ColumnValues columnValues = new ColumnValues();
    List<ValueWithLabel> expectedColumnValueLabel = List.of(
      new ValueWithLabel().value("value_01").label("label_01"),
      new ValueWithLabel().value("value_02").label("label_02")
    );
    RequestBuilder requestBuilder = MockMvcRequestBuilders
      .get("/entity-types/{id}/columns/{columnName}/values", entityTypeId, columnName)
      .accept(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, "tenant_01");
    when(entityTypeService.getFieldValues(entityTypeId, columnName, null))
      .thenReturn(columnValues.content(expectedColumnValueLabel));
    mockMvc
      .perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content[0].value", is(expectedColumnValueLabel.get(0).getValue())))
      .andExpect(jsonPath("$.content[0].label", is(expectedColumnValueLabel.get(0).getLabel())))
      .andExpect(jsonPath("$.content[1].value", is(expectedColumnValueLabel.get(1).getValue())))
      .andExpect(jsonPath("$.content[1].label", is(expectedColumnValueLabel.get(1).getLabel())));
  }

  @Test
  void shouldReturnColumnValuesWithLabelWithSearch() throws Exception {
    UUID entityTypeId = UUID.randomUUID();
    String columnName = "column_name";
    ColumnValues columnValues = new ColumnValues();
    List<ValueWithLabel> expectedColumnValueLabel = List.of(
      new ValueWithLabel().value("value_01").label("label_01"),
      new ValueWithLabel().value("value_02").label("label_02")
    );
    RequestBuilder requestBuilder = MockMvcRequestBuilders
      .get("/entity-types/{id}/columns/{columnName}/values", entityTypeId, columnName)
      .accept(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, "tenant_01")
      .queryParam("search", "label_01");
    when(entityTypeService.getFieldValues(entityTypeId, columnName, "label_01"))
      .thenReturn(columnValues.content(expectedColumnValueLabel));
    mockMvc
      .perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content[0].value", is(expectedColumnValueLabel.get(0).getValue())))
      .andExpect(jsonPath("$.content[0].label", is(expectedColumnValueLabel.get(0).getLabel())));
  }

  @Test
  void shouldReturnColumnValues() throws Exception {
    UUID entityTypeId = UUID.randomUUID();
    String columnName = "column_name";
    ColumnValues columnValues = new ColumnValues();
    List<ValueWithLabel> expectedColumnValueLabel = List.of(
      new ValueWithLabel().value("value_01"),
      new ValueWithLabel().value("value_02")
    );
    RequestBuilder requestBuilder = MockMvcRequestBuilders
      .get("/entity-types/{id}/columns/{columnName}/values", entityTypeId, columnName)
      .accept(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, "tenant_01");
    when(entityTypeService.getFieldValues(entityTypeId, columnName, null))
      .thenReturn(columnValues.content(expectedColumnValueLabel));
    mockMvc
      .perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content[0].value", is(expectedColumnValueLabel.get(0).getValue())))
      .andExpect(jsonPath("$.content[1].value", is(expectedColumnValueLabel.get(1).getValue())));
  }

  @Test
  void shouldReturnColumnValuesWithSearch() throws Exception {
    UUID entityTypeId = UUID.randomUUID();
    String columnName = "column_name";
    ColumnValues columnValues = new ColumnValues();
    List<ValueWithLabel> expectedColumnValueLabel = List.of(
      new ValueWithLabel().value("value_01"),
      new ValueWithLabel().value("value_02")
    );
    RequestBuilder requestBuilder = MockMvcRequestBuilders
      .get("/entity-types/{id}/columns/{columnName}/values", entityTypeId, columnName)
      .accept(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, "tenant_01")
      .queryParam("search", "value_01");
    when(entityTypeService.getFieldValues(entityTypeId, columnName, "value_01"))
      .thenReturn(columnValues.content(expectedColumnValueLabel));
    mockMvc
      .perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content[0].value", is(expectedColumnValueLabel.get(0).getValue())));
  }

  @Test
  void shouldReturnErrorWhenColumnNameNotFound() throws Exception {
    UUID entityTypeId = UUID.randomUUID();
    String columnName = "column_name";
    RequestBuilder requestBuilder = MockMvcRequestBuilders
      .get("/entity-types/{id}/columns/{columnName}/values", entityTypeId, columnName)
      .accept(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, "tenant_01");
    when(entityTypeService.getFieldValues(entityTypeId, columnName, null))
      .thenThrow(new FieldNotFoundException("entity_type", columnName));
    mockMvc.perform(requestBuilder).andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnErrorWhenEntityTypeIdNotFound() throws Exception {
    UUID entityTypeId = UUID.randomUUID();
    String columnName = "column_name";
    RequestBuilder requestBuilder = MockMvcRequestBuilders
      .get("/entity-types/{id}/columns/{columnName}/values", entityTypeId, columnName)
      .accept(MediaType.APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, "tenant_01");
    when(entityTypeService.getFieldValues(entityTypeId, columnName, null))
      .thenThrow(new EntityTypeNotFoundException(entityTypeId));
    mockMvc.perform(requestBuilder).andExpect(status().isNotFound());
  }

  private static EntityType getEntityType(EntityTypeColumn col) {
    UUID id = UUID.randomUUID();
    return new EntityType()
      .id(id.toString())
      .name("derived_table_01")
      .labelAlias("derived_table_alias_01")
      .columns(List.of(col));
  }

  private static EntityTypeColumn getEntityTypeColumn() {
    return new EntityTypeColumn()
      .name("derived_column_name_01")
      .dataType(new StringType().dataType("stringType"))
      .labelAlias("derived_column_alias_01")
      .visibleByDefault(false);
  }
}
