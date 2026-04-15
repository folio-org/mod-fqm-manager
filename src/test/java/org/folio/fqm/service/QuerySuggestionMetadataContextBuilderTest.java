package org.folio.fqm.service;

import org.folio.fqm.model.QuerySuggestionDataType;
import org.folio.fqm.model.QuerySuggestionEntityTypeContext;
import org.folio.fqm.model.QuerySuggestionField;
import org.folio.fqm.model.QuerySuggestionMetadataContext;
import org.folio.querytool.domain.dto.ArrayType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.NestedObjectProperty;
import org.folio.querytool.domain.dto.ObjectType;
import org.folio.querytool.domain.dto.StringType;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuerySuggestionMetadataContextBuilderTest {

  @Test
  void shouldBuildReducedMetadataContextFromFlattenedEntityTypes() {
    UUID ordersId = UUID.randomUUID();
    UUID invoicesId = UUID.randomUUID();

    EntityType orders = new EntityType()
      .id(ordersId.toString())
      .name("orders")
      .labelAlias("Orders")
      .columns(List.of(
        new EntityTypeColumn()
          .name("status")
          .labelAlias("Status")
          .queryable(true)
          .values(List.of(
            new ValueWithLabel().value("Open").label("Open"),
            new ValueWithLabel().value("Closed").label("Closed")
          ))
          .dataType(new StringType().dataType("stringType")),
        new EntityTypeColumn()
          .name("vendor")
          .labelAlias("Vendor")
          .dataType(new ObjectType().dataType("objectType").properties(List.of(
            new NestedObjectProperty()
              .name("name")
              .labelAlias("Name")
              .labelAliasFullyQualified("Vendor name")
              .queryable(true)
              .dataType(new StringType().dataType("stringType"))
          ))),
        new EntityTypeColumn()
          .name("tags")
          .labelAlias("Tags")
          .dataType(new ArrayType().dataType("arrayType").itemDataType(new StringType().dataType("stringType")))
      ));

    EntityType invoices = new EntityType()
      .id(invoicesId.toString())
      .name("invoices")
      .labelAlias("Invoices")
      .columns(List.of(
        new EntityTypeColumn()
          .name("invoiceNumber")
          .labelAlias("Invoice number")
          .isIdColumn(true)
          .sourceAlias("invoice")
          .dataType(new StringType().dataType("stringType"))
      ));

    QuerySuggestionMetadataContextBuilder builder = new QuerySuggestionMetadataContextBuilder(
      new StubEntityTypeFlatteningService(Map.of(ordersId, orders, invoicesId, invoices)),
      new TestFolioExecutionContext("tenant_01")
    );

    QuerySuggestionMetadataContext result = builder.buildContext(List.of(ordersId, invoicesId));

    assertEquals(2, result.entityTypes().size());

    QuerySuggestionEntityTypeContext ordersContext = result.entityTypes().get(0);
    assertEquals(ordersId, ordersContext.id());
    assertEquals("Orders", ordersContext.label());
    assertEquals(3, ordersContext.fields().size());

    QuerySuggestionField statusField = ordersContext.fields().get(0);
    assertEquals("status", statusField.name());
    assertEquals("Status", statusField.label());
    assertEquals("stringType", statusField.dataType().type());
    assertEquals(2, statusField.values().size());
    assertTrue(statusField.queryable());

    QuerySuggestionField vendorField = ordersContext.fields().get(1);
    assertEquals("objectType", vendorField.dataType().type());
    assertEquals(1, vendorField.dataType().properties().size());
    assertEquals("Vendor name", vendorField.dataType().properties().get(0).fullyQualifiedLabel());

    QuerySuggestionField tagsField = ordersContext.fields().get(2);
    QuerySuggestionDataType tagsType = tagsField.dataType();
    assertEquals("arrayType", tagsType.type());
    assertNotNull(tagsType.itemType());
    assertEquals("stringType", tagsType.itemType().type());

    QuerySuggestionEntityTypeContext invoicesContext = result.entityTypes().get(1);
    assertEquals(invoicesId, invoicesContext.id());
    assertEquals("invoice", invoicesContext.fields().get(0).sourceAlias());
    assertTrue(invoicesContext.fields().get(0).idField());
  }

  @Test
  void shouldReturnEmptyContextWhenNoEntityTypesProvided() {
    QuerySuggestionMetadataContextBuilder builder = new QuerySuggestionMetadataContextBuilder(
      new StubEntityTypeFlatteningService(Map.of()),
      new TestFolioExecutionContext("tenant_01")
    );

    QuerySuggestionMetadataContext result = builder.buildContext(List.of());

    assertTrue(result.entityTypes().isEmpty());
  }

  private static class StubEntityTypeFlatteningService extends EntityTypeFlatteningService {
    private final Map<UUID, EntityType> entityTypesById;
    private final Map<UUID, String> tenantIdsSeen = new HashMap<>();

    StubEntityTypeFlatteningService(Map<UUID, EntityType> entityTypesById) {
      super(null, null, null, null, null);
      this.entityTypesById = entityTypesById;
    }

    @Override
    public EntityType getFlattenedEntityType(UUID entityTypeId, String tenantId, boolean preserveAllColumns) {
      tenantIdsSeen.put(entityTypeId, tenantId);
      EntityType entityType = entityTypesById.get(entityTypeId);
      if (entityType == null) {
        throw new IllegalArgumentException("Missing stub entity type: " + entityTypeId);
      }
      return entityType;
    }
  }

  private record TestFolioExecutionContext(String tenantId) implements FolioExecutionContext {
    @Override
    public String getTenantId() {
      return tenantId;
    }
  }
}
