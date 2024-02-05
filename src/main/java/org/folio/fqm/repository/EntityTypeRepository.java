package org.folio.fqm.repository;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.folio.querytool.domain.dto.BooleanType;
import org.jooq.DSLContext;
import org.jooq.Condition;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.jooq.impl.DSL.*;

@Repository
@RequiredArgsConstructor
@Log4j2
public class EntityTypeRepository {
  public static final String ID_FIELD_NAME = "id";
  public static final String TABLE_NAME = "entity_type_definition";
  public static final  String requiredFieldName = "jsonb ->> 'name'";
  public static final String refId =  "jsonb ->> 'refId'";
  public static final String whereClauseName = "jsonb ->> 'type'";
  public static final String customFieldType = "SINGLE_CHECKBOX";
  private final DSLContext jooqContext;
  private final ObjectMapper objectMapper;


  public Optional<String> getDerivedTableName(UUID entityTypeId) {
    log.info("Getting derived table name for entity type ID: {}", entityTypeId);

    Field<String> derivedTableNameField = field("derived_table_name", String.class);

    return jooqContext
      .select(derivedTableNameField)
      .from(table(TABLE_NAME))
      .where(field(ID_FIELD_NAME).eq(entityTypeId))
      .fetchOptional(derivedTableNameField);
  }

  public List<EntityTypeColumn> fetchNamesForSingleCheckbox(UUID entityTypeId) {
    log.info("Getting derived table name for entity type ID: {}", entityTypeId);
    String sourceViewName = getEntityTypeDefinition(entityTypeId)
      .map(EntityType::getSourceView)
      .orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId));
    return jooqContext
      .select(field(requiredFieldName), field(refId))
      .from(sourceViewName)
      .where(field(whereClauseName).eq(customFieldType))
      .fetch()
      .stream()
      .map(record -> {
        Object value = record.get(0);
        Object refId1 = record.get(1);
        assert value != null;
        return createEntityTypeColumn(value.toString(), refId1.toString());
      })
      .collect(Collectors.toList());
  }


  public Optional<EntityType> getEntityTypeDefinition(UUID entityTypeId) {
    log.info("Getting definition name for entity type ID: {}", entityTypeId);

    Field<String> definitionField = field("definition", String.class);

    Optional<EntityType> entityTypeOptional = jooqContext
      .select(definitionField)
      .from(table(TABLE_NAME))
      .where(field(ID_FIELD_NAME).eq(entityTypeId))
      .fetchOptional(definitionField)
      .map(this::unmarshallEntityType);

    EntityType entityType = entityTypeOptional.orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId));

    String customFieldsEntityTypeId = entityType.getCustomFieldEntityTypeId();
    if (customFieldsEntityTypeId != null) {
      entityType.getColumns().addAll(fetchNamesForSingleCheckbox(UUID.fromString(customFieldsEntityTypeId)));
    }
    return Optional.of(entityType);
  }


  public List<RawEntityTypeSummary> getEntityTypeSummary(Set<UUID> entityTypeIds) {
    log.info("Fetching entityTypeSummary for ids: {}", entityTypeIds);
    Field<UUID> idField = field(ID_FIELD_NAME, UUID.class);
    Field<String> nameField = field("definition ->> 'name'", String.class);
    Field<Boolean> privateEntityField = field("(definition ->> 'private')::boolean", Boolean.class);

    Condition publicEntityCondition = or(field(privateEntityField).isFalse(), field(privateEntityField).isNull());
    Condition entityTypeIdCondition = isEmpty(entityTypeIds) ? trueCondition() : field("id").in(entityTypeIds);
    return jooqContext.select(idField, nameField)
      .from(table(TABLE_NAME))
      .where(entityTypeIdCondition.and(publicEntityCondition))
      .fetch()
      .map(
        row -> new RawEntityTypeSummary(row.get(idField), row.get(nameField))
      );
  }

  private EntityTypeColumn createEntityTypeColumn(String value, String refId) {
    ValueWithLabel trueValue = new ValueWithLabel().label("True").value("true");
    ValueWithLabel falseValue = new ValueWithLabel().label("False").value("false");

    EntityTypeColumn entityTypeColumn = new EntityTypeColumn();
    entityTypeColumn.name(value);
    entityTypeColumn.dataType(new BooleanType());
    entityTypeColumn.values(List.of(trueValue, falseValue));
    entityTypeColumn.visibleByDefault(false);
    entityTypeColumn.valueGetter("src_users_users.jsonb -> 'customFields' ->> '" + refId + "'");
    entityTypeColumn.labelAlias(value);
    return entityTypeColumn;
  }

  @SneakyThrows
  private EntityType unmarshallEntityType(String str) {
    return objectMapper.readValue(str, EntityType.class);
  }

  public record RawEntityTypeSummary(UUID id, String name) {
  }
}
