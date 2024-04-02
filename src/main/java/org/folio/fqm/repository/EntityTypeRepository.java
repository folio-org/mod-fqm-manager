package org.folio.fqm.repository;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.querytool.domain.dto.BooleanType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.jooq.DSLContext;
import org.jooq.Condition;
import org.jooq.Field;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.folio.querytool.domain.dto.EntityType;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.or;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.trueCondition;

@Repository
@RequiredArgsConstructor
@Log4j2
public class EntityTypeRepository {
  public static final String TABLE_NAME = "entity_type_definition";
  
  public static final String ID_FIELD_NAME = "id";
  public static final String DEFINITION_FIELD_NAME = "definition";

  public static final String REQUIRED_FIELD_NAME = "jsonb ->> 'name'";
  public static final String REF_ID = "jsonb ->> 'refId'";
  public static final String TYPE_FIELD = "jsonb ->> 'type'";

  public static final String CUSTOM_FIELD_TYPE = "SINGLE_CHECKBOX";

  @Qualifier("readerJooqContext") private final DSLContext jooqContext;
  private final ObjectMapper objectMapper;

  public Optional<EntityType> getEntityTypeDefinition(UUID entityTypeId) {
    log.info("Getting definition name for entity type ID: {}", entityTypeId);

    Field<String> definitionField = field(DEFINITION_FIELD_NAME, String.class);

    return jooqContext
      .select(definitionField)
      .from(table(TABLE_NAME))
      .where(field(ID_FIELD_NAME).eq(entityTypeId))
      .fetchOptional(definitionField)
      .map(this::unmarshallEntityType)
      .map(entityType -> {
        String customFieldsEntityTypeId = entityType.getCustomFieldEntityTypeId();
        if (customFieldsEntityTypeId != null) {
          entityType.getColumns().addAll(fetchColumnNamesForCustomFields(UUID.fromString(customFieldsEntityTypeId)));
        }
        return entityType;
      });
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

  private List<EntityTypeColumn> fetchColumnNamesForCustomFields(UUID entityTypeId) {
    log.info("Getting columns for entity type ID: {}", entityTypeId);
    EntityType entityTypeDefinition = getEntityTypeDefinition(entityTypeId)
      .orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId));
    String sourceViewName = entityTypeDefinition.getSourceView();
    String sourceViewExtractor = entityTypeDefinition.getSourceViewExtractor();

    return jooqContext
      .select(field(REQUIRED_FIELD_NAME), field(REF_ID))
      .from(sourceViewName)
       //This where condition will be removed when handling other CustomFieldTypes.
       //A generic method can be created with if and else statements.
       //Currently, if implemented, "null" will be needed in the else part.
      .where(field(TYPE_FIELD).eq(CUSTOM_FIELD_TYPE))
      .fetch()
      .stream()
      .map(row -> {
        Object value = row.get(REQUIRED_FIELD_NAME);
        Object extractedRefId = row.get(REF_ID);
        assert value != null : "The value is marked as non-nullable in the database";
        return handleSingleCheckBox(value.toString(), extractedRefId.toString(), sourceViewExtractor);
      })
      .toList();
  }

  private EntityTypeColumn handleSingleCheckBox(String value, String refId, String sourceViewExtractor) {
    ValueWithLabel trueValue = new ValueWithLabel().label("True").value("true");
    ValueWithLabel falseValue = new ValueWithLabel().label("False").value("false");

    return new EntityTypeColumn()
      .name(value)
      .dataType(new BooleanType().dataType("booleanType"))
      .values(List.of(trueValue, falseValue))
      .visibleByDefault(false)
      .valueGetter(sourceViewExtractor + " ->> '" + refId + "'")
      .labelAlias(value)
      .isCustomField(true);
  }

  @SneakyThrows
  private EntityType unmarshallEntityType(String str) {
    return objectMapper.readValue(str, EntityType.class);
  }

  public record RawEntityTypeSummary(UUID id, String name) {
  }
}
