package org.folio.fqm.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.querytool.domain.dto.*;
import org.folio.spring.FolioExecutionContext;
import org.jooq.Field;
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
  public static final String SELECT_FIELD = "jsonb -> 'selectField'";
  public static final String CUSTOM_FIELD_TYPE = "SINGLE_CHECKBOX";
  public static final String CUSTOM_FIELD_TYPE_SINGLE_SELECT_DROPDOWN = "SINGLE_SELECT_DROPDOWN";
  public static final String CUSTOM_FIELD_TYPE_RADIO_BUTTON = "RADIO_BUTTON";

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

  private List<EntityTypeColumn> fetchColumnNamesForCustomFields(String entityTypeId, EntityType entityType, Map<String, EntityType> rawEntityTypes) {
    log.info("Getting columns for entity type ID: {}", entityTypeId);

    EntityType entityTypeDefinition = entityTypeId.equals(entityType.getId()) ? entityType :
      Optional.ofNullable(rawEntityTypes.get(entityTypeId))
        .orElseThrow(() -> new EntityTypeNotFoundException(UUID.fromString(entityTypeId)));

    String sourceViewName = entityTypeDefinition.getSourceView();
    String sourceViewExtractor = entityTypeDefinition.getSourceViewExtractor();

    // Fetch query results (no need to use .intoList())
    Result<Record4<Object, Object, Object, Object>> results = readerJooqContext
      .select(field(REQUIRED_FIELD_NAME), field(REF_ID), field(TYPE_FIELD), field(SELECT_FIELD)) // SELECT_FIELD for dropdown and radio button values
      .from(sourceViewName)
      .where(field(TYPE_FIELD).in(CUSTOM_FIELD_TYPE, CUSTOM_FIELD_TYPE_SINGLE_SELECT_DROPDOWN, CUSTOM_FIELD_TYPE_RADIO_BUTTON)) // Handle checkbox, dropdown, and radio button
      .fetch();

    if (results.isEmpty()) {
      log.warn("No custom fields found for entity type ID: {}", entityTypeId);
      return Collections.emptyList();  // Return empty list if no results found
    }

    return results.stream()
      .map(row -> {
        String name = row.get(REQUIRED_FIELD_NAME, String.class);
        String refId = row.get(REF_ID, String.class);
        String type = row.get(TYPE_FIELD, String.class);
        String selectFieldJson = row.get(SELECT_FIELD, String.class); // Dropdown/Radio options as JSON
        Objects.requireNonNull(name, "The name is marked as non-nullable in the database");

        if (CUSTOM_FIELD_TYPE_SINGLE_SELECT_DROPDOWN.equals(type)) {
          List<ValueWithLabel> dropdownValues = parseDropdownValues(selectFieldJson);
          return handleSingleSelectDropdown(name, refId, sourceViewExtractor, dropdownValues);
        } else if (CUSTOM_FIELD_TYPE_RADIO_BUTTON.equals(type)) {
          List<ValueWithLabel> radioButtonValues = parseRadioButtonValues(selectFieldJson);
          return handleRadioButton(name, refId, sourceViewExtractor, radioButtonValues);
        } else if (CUSTOM_FIELD_TYPE.equals(type)) {
          return handleSingleCheckBox(name, refId, sourceViewExtractor);
        }
        return null;
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }


  private List<ValueWithLabel> parseDropdownValues(String selectFieldJson) {
    if (selectFieldJson == null || selectFieldJson.isEmpty()) {
      return List.of();
    }
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(selectFieldJson);
      JsonNode valuesNode = root.path("options").path("values");

      List<ValueWithLabel> dropdownValues = new ArrayList<>();
      for (JsonNode valueNode : valuesNode) {
        String value = valueNode.get("value").asText();
        dropdownValues.add(new ValueWithLabel().label(value).value(value));
      }
      return dropdownValues;
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to parse selectField JSON: " + selectFieldJson, e);
    }
  }

  private EntityTypeColumn handleSingleSelectDropdown(String value, String refId, String sourceViewExtractor, List<ValueWithLabel> dropdownValues) {
    return new EntityTypeColumn()
      .name(value)
      .dataType(new StringType().dataType("stringType"))
      .values(dropdownValues)
      .visibleByDefault(true)
      .valueGetter(sourceViewExtractor + " ->> '" + refId + "'")
      .labelAlias(value)
      .queryable(true)
      .isCustomField(true);
  }

  private List<ValueWithLabel> parseRadioButtonValues(String selectFieldJson) {
    if (selectFieldJson == null || selectFieldJson.isEmpty()) {
      return List.of();
    }
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(selectFieldJson);
      JsonNode valuesNode = root.path("options").path("values");

      List<ValueWithLabel> radioButtonValues = new ArrayList<>();
      for (JsonNode valueNode : valuesNode) {
        String value = valueNode.get("value").asText();
        radioButtonValues.add(new ValueWithLabel().label(value).value(value));
      }
      return radioButtonValues;
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to parse selectField JSON: " + selectFieldJson, e);
    }
  }

  private EntityTypeColumn handleRadioButton(String value, String refId, String sourceViewExtractor, List<ValueWithLabel> radioButtonValues) {
    return new EntityTypeColumn()
      .name(value)
      .dataType(new StringType().dataType("stringType"))
      .values(radioButtonValues)
      .visibleByDefault(true)
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
