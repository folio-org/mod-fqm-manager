package org.folio.fqm.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.querytool.domain.dto.BooleanType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.folio.spring.FolioExecutionContext;
import org.jooq.Record;
import org.jooq.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
@Log4j2
public class EntityTypeRepository {

  public static final String TABLE_NAME = "entity_type_definition";

  public static final String ID_FIELD_NAME = "id";
  public static final String DEFINITION_FIELD_NAME = "definition";

  public static final String REQUIRED_FIELD_NAME = "jsonb ->> 'name'";
  public static final String REF_ID = "jsonb ->> 'refId'";
  public static final String TYPE_FIELD = "jsonb ->> 'type'";

  public static final String CUSTOM_FIELD_TYPE = "SINGLE_CHECKBOX";

  private final DSLContext readerJooqContext;
  private final DSLContext jooqContext;
  private final ObjectMapper objectMapper;
  private final FolioExecutionContext executionContext;

  private final Cache<String, Map<UUID, EntityType>> entityTypeCache;

  @Autowired
  public EntityTypeRepository(@Qualifier("readerJooqContext") DSLContext readerJooqContext,
                              DSLContext jooqContext,
                              ObjectMapper objectMapper,
                              FolioExecutionContext executionContext,
                              @Value("${mod-fqm-manager.entity-type-cache-timeout-seconds:3600}") long cacheDurationSeconds) {
    this.readerJooqContext = readerJooqContext;
    this.jooqContext = jooqContext;
    this.objectMapper = objectMapper;
    this.executionContext = executionContext;
    this.entityTypeCache = Caffeine.newBuilder().expireAfterWrite(cacheDurationSeconds, TimeUnit.SECONDS).build();
  }

  public Optional<EntityType> getEntityTypeDefinition(UUID entityTypeId, String tenantId) {
    return getEntityTypeDefinitions(Collections.singleton(entityTypeId), tenantId).findFirst();
  }

  public Stream<EntityType> getEntityTypeDefinitions(Collection<UUID> entityTypeIds, String tenantId) {
    log.info("Getting definitions name for entity type ID: {}", entityTypeIds);

    Map<UUID, EntityType> entityTypes = entityTypeCache.get(tenantId != null ? tenantId : executionContext.getTenantId(), tenantIdKey -> {
        String tableName = "".equals(tenantIdKey) ? TABLE_NAME : tenantIdKey + "_mod_fqm_manager." + TABLE_NAME;
        Field<String> definitionField = field(DEFINITION_FIELD_NAME, String.class);
      Map<String, EntityType> rawEntityTypes = readerJooqContext
        .select(definitionField)
        .from(table(tableName))
        .fetch(definitionField)
        .stream()
        .map(this::unmarshallEntityType)
        .collect(Collectors.toMap(EntityType::getId, Function.identity()));

      return rawEntityTypes.values().stream()
        .map(entityType -> {
          String customFieldsEntityTypeId = entityType.getCustomFieldEntityTypeId();
          if (customFieldsEntityTypeId != null) {
            entityType.getColumns().addAll(fetchColumnNamesForCustomFields(customFieldsEntityTypeId, entityType, rawEntityTypes));
          }
          return entityType;
        })
        .collect(Collectors.toMap(entityType -> UUID.fromString(entityType.getId()), Function.identity()));
      }
    );

    if (isEmpty(entityTypeIds)) {
      return entityTypes.values().stream();
    }
    return entityTypeIds.stream().filter(entityTypes::containsKey).map(entityTypes::get);
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

  private List<EntityTypeColumn> fetchColumnNamesForCustomFields(String entityTypeId, EntityType entityType, Map<String, EntityType> rawEntityTypes) {
    log.info("Getting columns for entity type ID: {}", entityTypeId);
    EntityType entityTypeDefinition = entityTypeId.equals(entityType.getId()) ? entityType :
      Optional.ofNullable(rawEntityTypes.get(entityTypeId)).orElseThrow(() -> new EntityTypeNotFoundException(UUID.fromString(entityTypeId)));
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
}
