package org.folio.fqm.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.querytool.domain.dto.BooleanType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertValuesStep2;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Stream;

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

  @Qualifier("readerJooqContext")
  private final DSLContext readerJooqContext;
  private final DSLContext jooqContext;
  private final ObjectMapper objectMapper;

  public Optional<EntityType> getEntityTypeDefinition(UUID entityTypeId, String tenantId) {
    return getEntityTypeDefinitions(Collections.singleton(entityTypeId), tenantId).findFirst();
  }

  public Stream<EntityType> getEntityTypeDefinitions(Collection<UUID> entityTypeIds, String tenantId) {
    String tableName = tenantId == null ? TABLE_NAME : tenantId + "_mod_fqm_manager." + TABLE_NAME;
    log.info("Getting definitions name for entity type ID: {}", entityTypeIds);

    Field<String> definitionField = field(DEFINITION_FIELD_NAME, String.class);
    Condition entityTypeIdCondition = isEmpty(entityTypeIds) ? trueCondition() : field("id").in(entityTypeIds);
    return readerJooqContext
      .select(definitionField)
      .from(table(tableName))
      .where(entityTypeIdCondition)
      .fetch(definitionField)
      .stream()
      .map(this::unmarshallEntityType)
      .map(entityType -> {
        String customFieldsEntityTypeId = entityType.getCustomFieldEntityTypeId();
        if (customFieldsEntityTypeId != null) {
          entityType.getColumns().addAll(fetchColumnNamesForCustomFields(UUID.fromString(customFieldsEntityTypeId), entityType));
        }
        return entityType;
      });
  }

  public void replaceEntityTypeDefinitions(List<EntityType> entityTypes) {
    log.info("Replacing entity type definitions with new set of {} entities", entityTypes.size());

    jooqContext.transaction(transaction -> {
      transaction.dsl()
        .deleteFrom(table(TABLE_NAME))
        .where(
          field(ID_FIELD_NAME, UUID.class)
            .in(entityTypes.stream().map(et -> UUID.fromString(et.getId())).toList())
        )
        .execute();

      InsertValuesStep2<Record, UUID, JSONB> insert = transaction.dsl()
        .insertInto(table(TABLE_NAME))
        .columns(field(ID_FIELD_NAME, UUID.class), field(DEFINITION_FIELD_NAME, JSONB.class));

      for (EntityType entityType : entityTypes) {
        insert.values(UUID.fromString(entityType.getId()), JSONB.jsonb(objectMapper.writeValueAsString(entityType)));
      }

      insert.execute();
    });
  }

  private List<EntityTypeColumn> fetchColumnNamesForCustomFields(UUID entityTypeId, EntityType entityType) {
    log.info("Getting columns for entity type ID: {}", entityTypeId);
    EntityType entityTypeDefinition = entityTypeId.toString().equals(entityType.getId()) ? entityType :
      getEntityTypeDefinition(entityTypeId, null).orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId));
    String sourceViewName = entityTypeDefinition.getSourceView();
    String sourceViewExtractor = entityTypeDefinition.getSourceViewExtractor();

    return readerJooqContext
      .select(field(REQUIRED_FIELD_NAME), field(REF_ID))
      .from(sourceViewName)
      // This where condition will be removed when handling other CustomFieldTypes.
      // A generic method can be created with if and else statements.
      // Currently, if implemented, "null" will be needed in the else part.
      .where(field(TYPE_FIELD).eq(CUSTOM_FIELD_TYPE))
      .fetch()
      .stream()
      .map(row -> {
        String name = row.get(REQUIRED_FIELD_NAME, String.class);
        String refId = row.get(REF_ID, String.class);
        Objects.requireNonNull(name, "The name is marked as non-nullable in the database");
        return handleSingleCheckBox(name, refId, sourceViewExtractor);
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
      .queryable(true)
      .isCustomField(true);
  }

  @SneakyThrows
  private EntityType unmarshallEntityType(String str) {
    return objectMapper.readValue(str, EntityType.class);
  }

  public record RawEntityTypeSummary(UUID id, String name, List<String> requiredPermissions) {}
}
