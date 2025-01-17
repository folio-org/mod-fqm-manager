package org.folio.fqm.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.querytool.domain.dto.BooleanType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.StringType;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.folio.spring.FolioExecutionContext;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertValuesStep2;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Record4;
import org.jooq.Result;
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
  public static final String STRING_EXTRACTOR = "%s ->> '%s'";
  public static final String CUSTOM_FIELD_NAME = "jsonb ->> 'name'";
  public static final String CUSTOM_FIELD_REF_ID = "jsonb ->> 'refId'";
  public static final String CUSTOM_FIELD_TYPE = "jsonb ->> 'type'";
  public static final String CUSTOM_FIELD_FILTER_VALUE_GETTER = "jsonb -> 'selectField' -> 'options' ->> 'values'";
  public static final String CUSTOM_FIELD_TYPE_SINGLE_CHECKBOX = "SINGLE_CHECKBOX";
  public static final String CUSTOM_FIELD_TYPE_SINGLE_SELECT_DROPDOWN = "SINGLE_SELECT_DROPDOWN";
  public static final String CUSTOM_FIELD_TYPE_RADIO_BUTTON = "RADIO_BUTTON";
  public static final String CUSTOM_FIELD_TYPE_TEXTBOX_SHORT = "TEXTBOX_SHORT";
  public static final String CUSTOM_FIELD_TYPE_TEXTBOX_LONG = "TEXTBOX_LONG";
  public static final String CUSTOM_FIELD_VALUE_GETTER = """
      (
        SELECT f.entry ->> 'value'
        FROM jsonb_array_elements(
               (SELECT jsonb -> 'selectField' -> 'options' -> 'values'\s
                FROM %s_mod_fqm_manager.%s\s
                WHERE jsonb ->> 'refId' = '%s')
             ) AS f(entry)
        WHERE f.entry ->> 'id' = %s ->> '%s'
      )
    """;

  public static final List<ValueWithLabel> CUSTOM_FIELD_BOOLEAN_VALUES = List.of(
    new ValueWithLabel().label("True").value("true"),
    new ValueWithLabel().label("False").value("false")
  );
  private static final List<String> SUPPORTED_CUSTOM_FIELD_TYPES = List.of(
    CUSTOM_FIELD_TYPE_SINGLE_CHECKBOX,
    CUSTOM_FIELD_TYPE_SINGLE_SELECT_DROPDOWN,
    CUSTOM_FIELD_TYPE_RADIO_BUTTON,
    CUSTOM_FIELD_TYPE_TEXTBOX_SHORT,
    CUSTOM_FIELD_TYPE_TEXTBOX_LONG
  );

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
                              @Value("${mod-fqm-manager.entity-type-cache-timeout-seconds:300}") long cacheDurationSeconds) {
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

  private List<EntityTypeColumn> fetchColumnNamesForCustomFields(String entityTypeId,
                                                                 EntityType entityType,
                                                                 Map<String, EntityType> rawEntityTypes) {
    log.info("Getting custom field columns for entity type ID: {}", entityTypeId);
    EntityType entityTypeDefinition = entityTypeId.equals(entityType.getId()) ? entityType :
      Optional.ofNullable(rawEntityTypes.get(entityTypeId))
        .orElseThrow(() -> new EntityTypeNotFoundException(UUID.fromString(entityTypeId)));
    String sourceViewName = entityTypeDefinition.getSourceView();
    String sourceViewExtractor = entityTypeDefinition.getSourceViewExtractor();

    Result<Record4<Object, Object, Object, Object>> results = readerJooqContext
      .select(field(CUSTOM_FIELD_NAME), field(CUSTOM_FIELD_REF_ID), field(CUSTOM_FIELD_TYPE), field(CUSTOM_FIELD_FILTER_VALUE_GETTER))
      .from(sourceViewName)
      .where(field(CUSTOM_FIELD_TYPE).in(SUPPORTED_CUSTOM_FIELD_TYPES))
      .fetch();

    return results.stream()
      .map(row -> {
        String name = "";
        try {
          name = row.get(CUSTOM_FIELD_NAME, String.class);
          String refId = row.get(CUSTOM_FIELD_REF_ID, String.class);
          String type = row.get(CUSTOM_FIELD_TYPE, String.class);
          String customFieldValueJson = row.get(CUSTOM_FIELD_FILTER_VALUE_GETTER, String.class);

          if (CUSTOM_FIELD_TYPE_SINGLE_SELECT_DROPDOWN.equals(type) || CUSTOM_FIELD_TYPE_RADIO_BUTTON.equals(type)) {
            List<ValueWithLabel> columnValues = parseCustomFieldValues(customFieldValueJson);
            return handleSingleSelectCustomField(name, refId, sourceViewName, sourceViewExtractor, columnValues);
          } else if (CUSTOM_FIELD_TYPE_SINGLE_CHECKBOX.equals(type)) {
            return handleBooleanCustomField(name, refId, sourceViewExtractor);
          } else if (CUSTOM_FIELD_TYPE_TEXTBOX_SHORT.equals(type) || CUSTOM_FIELD_TYPE_TEXTBOX_LONG.equals(type)) {
            return handleTextboxCustomField(name, refId, sourceViewExtractor);
          }
          return null;
        } catch (Exception e) {
          log.error("Error processing custom field {} for entity type ID: {}", name, entityTypeId, e);
          return null;
        }
      })
      .filter(Objects::nonNull)
      .toList();
  }


  private List<ValueWithLabel> parseCustomFieldValues(String customFieldValueJson) throws JsonProcessingException {
    List<Map<String, String>> optionList = objectMapper.readValue(customFieldValueJson, new TypeReference<>() {
    });
    return optionList
      .stream()
      .map(option -> new ValueWithLabel()
        .value(option.get("id"))
        .label(option.get("value"))
      )
      .toList();
  }

  private EntityTypeColumn handleBooleanCustomField(String name,
                                                    String refId,
                                                    String sourceViewExtractor) {
    return new EntityTypeColumn()
      .name(name)
      .dataType(new BooleanType().dataType("booleanType"))
      .values(CUSTOM_FIELD_BOOLEAN_VALUES)
      .visibleByDefault(false)
      .valueGetter(String.format(STRING_EXTRACTOR, sourceViewExtractor, refId))
      .labelAlias(name)
      .queryable(true)
      .isCustomField(true);
  }

  private EntityTypeColumn handleSingleSelectCustomField(String name,
                                                         String refId,
                                                         String sourceViewName,
                                                         String sourceViewExtractor,
                                                         List<ValueWithLabel> columnValues) {
    String filterValueGetter = String.format(STRING_EXTRACTOR, sourceViewExtractor, refId);
    String valueGetter = String.format(
      CUSTOM_FIELD_VALUE_GETTER,
      executionContext.getTenantId(),
      sourceViewName,
      refId,
      sourceViewExtractor,
      refId
    );
    return new EntityTypeColumn()
      .name(name)
      .dataType(new StringType().dataType("stringType"))
      .values(columnValues)
      .visibleByDefault(false)
      .valueGetter(valueGetter)
      .filterValueGetter(filterValueGetter)
      .labelAlias(name)
      .queryable(true)
      .isCustomField(true);
  }

  private EntityTypeColumn handleTextboxCustomField(String name,
                                                    String refId,
                                                    String sourceViewExtractor) {
    return new EntityTypeColumn()
      .name(name)
      .dataType(new StringType().dataType("stringType"))
      .visibleByDefault(false)
      .valueGetter(String.format(STRING_EXTRACTOR, sourceViewExtractor, refId))
      .labelAlias(name)
      .queryable(true)
      .isCustomField(true);
  }

  @SneakyThrows
  private EntityType unmarshallEntityType(String str) {
    return objectMapper.readValue(str, EntityType.class);
  }
}
