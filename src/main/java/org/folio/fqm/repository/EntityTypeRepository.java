package org.folio.fqm.repository;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import org.jooq.DSLContext;
import org.jooq.Condition;
import org.jooq.Field;

import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.folio.fqm.domain.dto.EntityTypeSummary;
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
  public static final String ID_FIELD_NAME = "id";
  public static final String TABLE_NAME = "entity_type_definition";

  private final DSLContext jooqContext;
  private final ObjectMapper objectMapper;

  public Optional<String> getDerivedTableName(String tenantId, UUID entityTypeId) {
    log.info("Getting derived table name for tenant {}, entity type ID: {}", tenantId, entityTypeId);

    Field<String> derivedTableNameField = field("derived_table_name", String.class);

    return jooqContext
      .select(derivedTableNameField)
      .from(table(TABLE_NAME))
      .where(field(ID_FIELD_NAME).eq(entityTypeId))
      .fetchOptional(derivedTableNameField)
      .map(tableName -> getFqmSchemaName(tenantId) + "." + tableName);
  }

  public Optional<EntityType> getEntityTypeDefinition(String tenantId, UUID entityTypeId) {
    log.info("Getting definition name for tenant {}, entity type ID: {}", tenantId, entityTypeId);

    Field<String> definitionField = field("definition", String.class);

    return jooqContext
      .select(definitionField)
      .from(table(TABLE_NAME))
      .where(field(ID_FIELD_NAME).eq(entityTypeId))
      .fetchOptional(definitionField)
      .map(this::unmarshallEntityType);
  }

  public List<EntityTypeSummary> getEntityTypeSummary(Set<UUID> entityTypeIds) {
    log.info("Fetching entityTypeSummary for ids: {}", entityTypeIds);
    Field<UUID> idField = field(ID_FIELD_NAME, UUID.class);
    Field<String> labelAliasField = field("definition ->> 'labelAlias'", String.class);
    Field<Boolean> privateEntityField = field("(definition ->> 'private')::boolean", Boolean.class);

    Condition publicEntityCondition = or(field(privateEntityField).isFalse(), field(privateEntityField).isNull());
    Condition entityTypeIdCondition = isEmpty(entityTypeIds) ? trueCondition() : field("id").in(entityTypeIds);
    return jooqContext.select(idField, labelAliasField)
      .from(table(TABLE_NAME))
      .where(entityTypeIdCondition.and(publicEntityCondition))
      .fetch()
      .map(
        row -> new EntityTypeSummary()
          .id(row.get(idField))
          .label(row.get(labelAliasField))
      );
  }

  String getFqmSchemaName(String tenantId) {
    return tenantId + "_mod_fqm_manager";
  }

  @SneakyThrows
  private EntityType unmarshallEntityType(String str) {
    return objectMapper.readValue(str, EntityType.class);
  }
}
