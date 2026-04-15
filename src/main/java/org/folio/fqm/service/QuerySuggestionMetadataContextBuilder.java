package org.folio.fqm.service;

import org.folio.fqm.model.QuerySuggestionDataType;
import org.folio.fqm.model.QuerySuggestionEntityTypeContext;
import org.folio.fqm.model.QuerySuggestionField;
import org.folio.fqm.model.QuerySuggestionFieldValue;
import org.folio.fqm.model.QuerySuggestionMetadataContext;
import org.folio.querytool.domain.dto.ArrayType;
import org.folio.querytool.domain.dto.EntityDataType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.Field;
import org.folio.querytool.domain.dto.JsonbArrayType;
import org.folio.querytool.domain.dto.NestedObjectProperty;
import org.folio.querytool.domain.dto.ObjectType;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class QuerySuggestionMetadataContextBuilder {

  private final EntityTypeFlatteningService entityTypeFlatteningService;
  private final FolioExecutionContext folioExecutionContext;

  public QuerySuggestionMetadataContextBuilder(
    EntityTypeFlatteningService entityTypeFlatteningService,
    FolioExecutionContext folioExecutionContext
  ) {
    this.entityTypeFlatteningService = entityTypeFlatteningService;
    this.folioExecutionContext = folioExecutionContext;
  }

  public QuerySuggestionMetadataContext buildContext(List<UUID> entityTypeIds) {
    if (entityTypeIds == null || entityTypeIds.isEmpty()) {
      return new QuerySuggestionMetadataContext(List.of());
    }

    String tenantId = folioExecutionContext.getTenantId();
    List<QuerySuggestionEntityTypeContext> entityTypes = entityTypeIds.stream()
      .distinct()
      .map(entityTypeId -> entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, tenantId, true))
      .map(this::mapEntityType)
      .toList();

    return new QuerySuggestionMetadataContext(entityTypes);
  }

  private QuerySuggestionEntityTypeContext mapEntityType(EntityType entityType) {
    List<QuerySuggestionField> fields = entityType.getColumns() == null
      ? List.of()
      : entityType.getColumns().stream().map(this::mapField).toList();

    return new QuerySuggestionEntityTypeContext(
      UUID.fromString(entityType.getId()),
      entityType.getName(),
      entityType.getLabelAlias(),
      fields
    );
  }

  private QuerySuggestionField mapField(Field field) {
    return new QuerySuggestionField(
      field.getName(),
      field.getLabelAlias(),
      field instanceof NestedObjectProperty nestedObjectProperty ? nestedObjectProperty.getLabelAliasFullyQualified() : null,
      field instanceof EntityTypeColumn entityTypeColumn ? entityTypeColumn.getSourceAlias() : null,
      Boolean.TRUE.equals(field.getQueryable()),
      Boolean.TRUE.equals(field.getQueryOnly()),
      Boolean.TRUE.equals(field.getHidden()),
      field instanceof EntityTypeColumn entityTypeColumn && Boolean.TRUE.equals(entityTypeColumn.getIsIdColumn()),
      mapDataType(field.getDataType()),
      mapValues(field.getValues())
    );
  }

  private QuerySuggestionDataType mapDataType(EntityDataType dataType) {
    if (dataType == null) {
      return null;
    }

    if (dataType instanceof ObjectType objectType) {
      List<QuerySuggestionField> properties = objectType.getProperties() == null
        ? List.of()
        : objectType.getProperties().stream().map(this::mapField).toList();
      return new QuerySuggestionDataType(dataType.getDataType(), null, properties);
    }

    if (dataType instanceof ArrayType arrayType) {
      return new QuerySuggestionDataType(
        dataType.getDataType(),
        mapDataType(arrayType.getItemDataType()),
        List.of()
      );
    }

    if (dataType instanceof JsonbArrayType jsonbArrayType) {
      return new QuerySuggestionDataType(
        dataType.getDataType(),
        mapDataType(jsonbArrayType.getItemDataType()),
        List.of()
      );
    }

    return new QuerySuggestionDataType(dataType.getDataType(), null, List.of());
  }

  private List<QuerySuggestionFieldValue> mapValues(List<ValueWithLabel> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }

    return values.stream()
      .map(value -> new QuerySuggestionFieldValue(value.getValue(), value.getLabel()))
      .toList();
  }
}
