package org.folio.fqm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.folio.fqm.domain.dto.EntityTypeSummary;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.testutil.TestDataFixture;
import org.folio.querytool.domain.dto.*;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityTypeServiceTest {

  @Mock
  private EntityTypeRepository repo;

  @Mock
  private QueryProcessorService queryProcessorService;

  @Mock
  private FolioExecutionContext folioExecutionContext;

  @InjectMocks
  private EntityTypeService entityTypeService;

  @Test
  void shouldGetEntityTypeSummaryForValidIds() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Set<UUID> ids = Set.of(id1, id2);
    List<EntityTypeSummary> expectedSummary = List.of(
      new EntityTypeSummary().id(id1).label("label_01"),
      new EntityTypeSummary().id(id2).label("label_02")
    );
    when(repo.getEntityTypeSummary(ids)).thenReturn(expectedSummary);
    List<EntityTypeSummary> actualSummary = entityTypeService.getEntityTypeSummary(
      ids
    );

    assertEquals(
      expectedSummary,
      actualSummary,
      "Expected Summary should equal Actual Summary"
    );
  }

  @Test
  void shouldGetValueWithLabel() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    String tenantId = "tenant_01";
    List<String> fields = List.of("id", valueColumnName);

    ColumnValues expectedColumnValueLabel = new ColumnValues()
      .content(
        List.of(
          new ValueWithLabel().value("value_01").label("label_01"),
          new ValueWithLabel().value("value_02").label("label_02")
        )
      );
    String expectedFql =
      "{\"" + valueColumnName + "\": {\"$regex\": " + "\"\"}}";

    when(folioExecutionContext.getTenantId()).thenReturn(tenantId);
    when(
      queryProcessorService.processQuery(
        tenantId,
        entityTypeId,
        expectedFql,
        fields,
        null,
        1000
      )
    )
      .thenReturn(
        List.of(
          Map.of("id", "value_01", valueColumnName, "label_01"),
          Map.of("id", "value_02", valueColumnName, "label_02")
        )
      );

    ColumnValues actualColumnValueLabel = entityTypeService.getColumnValues(
      entityTypeId,
      valueColumnName,
      ""
    );
    assertEquals(expectedColumnValueLabel, actualColumnValueLabel);
  }

  @Test
  void shouldReturnValueAsLabelIfIdColumnDoNotExist() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    String tenantId = "tenant_01";
    List<String> fields = List.of("id", valueColumnName);

    ColumnValues expectedColumnValueLabel = new ColumnValues()
      .content(
        List.of(
          new ValueWithLabel().value("value_01").label("value_01"),
          new ValueWithLabel().value("value_02").label("value_02")
        )
      );
    String expectedFql =
      "{\"" + valueColumnName + "\": {\"$regex\": " + "\"\"}}";

    when(folioExecutionContext.getTenantId()).thenReturn(tenantId);
    when(
      queryProcessorService.processQuery(
        tenantId,
        entityTypeId,
        expectedFql,
        fields,
        null,
        1000
      )
    )
      .thenReturn(
        List.of(
          Map.of(valueColumnName, "value_01"),
          Map.of(valueColumnName, "value_02")
        )
      );

    ColumnValues actualColumnValueLabel = entityTypeService.getColumnValues(
      entityTypeId,
      valueColumnName,
      ""
    );
    assertEquals(expectedColumnValueLabel, actualColumnValueLabel);
  }

  @Test
  void shouldFilterBySearchText() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    String tenantId = "tenant_01";
    List<String> fields = List.of("id", valueColumnName);
    String searchText = "search text";
    String expectedFql =
      "{\"" +
      valueColumnName +
      "\": {\"$regex\": " +
      "\"" +
      searchText +
      "\"}}";

    when(folioExecutionContext.getTenantId()).thenReturn(tenantId);
    entityTypeService.getColumnValues(
      entityTypeId,
      valueColumnName,
      searchText
    );
    verify(queryProcessorService)
      .processQuery(tenantId, entityTypeId, expectedFql, fields, null, 1000);
  }

  @Test
  void shouldHandleNullSearchText() {
    UUID entityTypeId = UUID.randomUUID();
    String valueColumnName = "column_name";
    String tenantId = "tenant_01";
    List<String> fields = List.of("id", valueColumnName);
    String expectedFql =
      "{\"" + valueColumnName + "\": {\"$regex\": " + "\"\"}}";

    when(folioExecutionContext.getTenantId()).thenReturn(tenantId);
    entityTypeService.getColumnValues(entityTypeId, valueColumnName, null);
    verify(queryProcessorService)
      .processQuery(tenantId, entityTypeId, expectedFql, fields, null, 1000);
  }

  @Test
  void shouldReturnEntityTypeDefinition() {
    UUID entityTypeId = UUID.randomUUID();
    String tenantId = "tenant_01";
    EntityType expectedEntityType = TestDataFixture.getEntityDefinition();

    when(entityTypeService.getEntityTypeDefinition(tenantId, entityTypeId))
      .thenReturn(Optional.of(expectedEntityType));
    EntityType actualDefinition = entityTypeService
      .getEntityTypeDefinition(tenantId, entityTypeId)
      .get();

    assertEquals(expectedEntityType, actualDefinition);
  }

  @Test
  void shouldReturnDerivedTableName() {
    UUID entityTypeId = UUID.randomUUID();
    String tenantId = "tenant_01";
    String derivedTableName = "derived_table_01";

    when(entityTypeService.getDerivedTableName(tenantId, entityTypeId))
      .thenReturn(derivedTableName);

    String actualDerivedTableName = entityTypeService.getDerivedTableName(
      tenantId,
      entityTypeId
    );
    assertEquals(derivedTableName, actualDerivedTableName);
  }
}
