package org.folio.fqm.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.fqm.utils.flattening.SourceUtils;
import org.folio.querytool.domain.dto.BooleanType;
import org.folio.querytool.domain.dto.CustomFieldMetadata;
import org.folio.querytool.domain.dto.CustomFieldType;
import org.folio.querytool.domain.dto.DateType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.JsonbArrayType;
import org.folio.querytool.domain.dto.StringType;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.folio.spring.FolioExecutionContext;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertValuesStep2;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Record5;
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
  public static final String JSONB_ARRAY_EXTRACTOR = "%s -> '%s'";
  public static final String CUSTOM_FIELD_PREPENDER = "_custom_field_";
  public static final String CUSTOM_FIELD_NAME = "jsonb ->> 'name'";
  public static final String CUSTOM_FIELD_REF_ID = "jsonb ->> 'refId'";
  public static final String CUSTOM_FIELD_TYPE = "jsonb ->> 'type'";
  public static final String CUSTOM_FIELD_FILTER_VALUE_GETTER = "jsonb -> 'selectField' -> 'options' ->> 'values'";
  public static final String CUSTOM_FIELD_TYPE_SINGLE_CHECKBOX = "SINGLE_CHECKBOX";
  public static final String CUSTOM_FIELD_TYPE_SINGLE_SELECT_DROPDOWN = "SINGLE_SELECT_DROPDOWN";
  public static final String CUSTOM_FIELD_TYPE_MULTI_SELECT_DROPDOWN = "MULTI_SELECT_DROPDOWN";
  public static final String CUSTOM_FIELD_TYPE_RADIO_BUTTON = "RADIO_BUTTON";
  public static final String CUSTOM_FIELD_TYPE_TEXTBOX_SHORT = "TEXTBOX_SHORT";
  public static final String CUSTOM_FIELD_TYPE_TEXTBOX_LONG = "TEXTBOX_LONG";
  public static final String CUSTOM_FIELD_TYPE_DATE = "DATE_PICKER";
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
  public static final String CUSTOM_FIELD_JSONB_ARRAY_VALUE_GETTER = """
    (
      SELECT jsonb_agg(f.entry ->> 'value')
      FROM jsonb_array_elements(
        (
            SELECT jsonb -> 'selectField' -> 'options' -> 'values'
            FROM %s_mod_fqm_manager.%s
            WHERE jsonb ->> 'refId' = '%s'
        )
      ) AS f(entry)
      WHERE
        (f.entry ->> 'id') IN (SELECT jsonb_array_elements_text(%s -> '%s'))
    )
    """;

  public static final List<ValueWithLabel> CUSTOM_FIELD_BOOLEAN_VALUES = List.of(
    new ValueWithLabel().label("True").value("true"),
    new ValueWithLabel().label("False").value("false")
  );
  public static final List<String> SUPPORTED_CUSTOM_FIELD_TYPES = List.of(
    CUSTOM_FIELD_TYPE_SINGLE_CHECKBOX,
    CUSTOM_FIELD_TYPE_SINGLE_SELECT_DROPDOWN,
    CUSTOM_FIELD_TYPE_MULTI_SELECT_DROPDOWN,
    CUSTOM_FIELD_TYPE_RADIO_BUTTON,
    CUSTOM_FIELD_TYPE_TEXTBOX_SHORT,
    CUSTOM_FIELD_TYPE_TEXTBOX_LONG,
    CUSTOM_FIELD_TYPE_DATE
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
    Map<UUID, EntityType> entityTypes = entityTypeCache.get(tenantId, tenantIdKey -> {
        String tableName = "".equals(tenantIdKey) ? TABLE_NAME : tenantIdKey + "_mod_fqm_manager." + TABLE_NAME;
        Field<String> definitionField = field(DEFINITION_FIELD_NAME, String.class);
        Map<String, EntityType> rawEntityTypes = readerJooqContext
          .select(definitionField)
          .from(table(tableName))
          .fetch(definitionField)
          .stream()
          .map(str -> {
            try {
              EntityType entityType = objectMapper.readValue(str, EntityType.class);
              throwIfEntityTypeInvalid(entityType);
              return entityType;
            } catch (Exception e) {
              log.error("Error processing raw entity type definition: {}", str, e);
              return null;
            }
          })
          .filter(Objects::nonNull)
          .collect(Collectors.toMap(EntityType::getId, Function.identity()));

        return rawEntityTypes.values().stream()
          .map(entityType -> {
            List<EntityTypeColumn> newColumns = entityType.getColumns().stream()
              .flatMap(column -> column.getDataType().getDataType().equals("customFieldType")
                ? processCustomFieldColumn(entityType, column).stream()
                : Stream.of(column)
              )
              .toList();
            return entityType.columns(newColumns);
          })
          .collect(Collectors.toMap(entityType -> UUID.fromString(entityType.getId()), Function.identity()));
      }
    );

    if (isEmpty(entityTypeIds)) {
      return entityTypes.values().stream();
    }
    return entityTypeIds.stream().filter(entityTypes::containsKey).map(entityTypes::get);
  }

  /**
   * Basic validation to ensure the entity type is structurally sound enough to function.
   *
   * @implSpec Full validation should happen in the service layer, this is just a quick check to avoid issues that could arise from
   * the entity type being malformed in a way that would cause the system to crash or behave unexpectedly.
   */
  static void throwIfEntityTypeInvalid(EntityType entityType) {
    try {
      //Attempt to parse UUID to ensure it's valid
      UUID.fromString(entityType.getId());
    } catch (Exception e) {
      throw new InvalidEntityTypeDefinitionException("Invalid entity type ID: " + entityType.getId(), entityType, e);
    }
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

  private List<EntityTypeColumn> processCustomFieldColumn(EntityType entityType, EntityTypeColumn column) {
    log.debug("Extracting custom fields for column: {} of entity type {}", column.getName(), entityType.getName());
    List<EntityTypeColumn> customFieldColumns = fetchColumnNamesForCustomFields(entityType.getId(), column);
    return getColumnsWithUniqueAliases(customFieldColumns);
  }

  private List<EntityTypeColumn> fetchColumnNamesForCustomFields(String entityTypeId,
                                                                 EntityTypeColumn entityTypeColumn) {
    CustomFieldMetadata customFieldMetadata = ((CustomFieldType) entityTypeColumn.getDataType()).getCustomFieldMetadata();
    String configurationView = customFieldMetadata.getConfigurationView();
    String dataExtractionPath = customFieldMetadata.getDataExtractionPath();

    Result<Record5<Object, Object, Object, Object, Object>> results;
    try {
      results = readerJooqContext
        .select(field("id"), field(CUSTOM_FIELD_NAME), field(CUSTOM_FIELD_REF_ID), field(CUSTOM_FIELD_TYPE), field(CUSTOM_FIELD_FILTER_VALUE_GETTER))
        .from(configurationView)
        .where(field(CUSTOM_FIELD_TYPE).in(SUPPORTED_CUSTOM_FIELD_TYPES))
        .fetch();
    } catch (Exception e) {
      log.error("Error fetching custom fields for entity type ID: {}. This entity type's custom field metadata may not be configured correctly.", entityTypeId, e);
      return Collections.emptyList();
    }

    return results.stream()
      .map(row -> {
        String name = "";
        try {
          String id = row.get("id", String.class);
          name = row.get(CUSTOM_FIELD_NAME, String.class);
          String refId = row.get(CUSTOM_FIELD_REF_ID, String.class);
          String type = row.get(CUSTOM_FIELD_TYPE, String.class);
          String customFieldValueJson = row.get(CUSTOM_FIELD_FILTER_VALUE_GETTER, String.class);

          return switch (type) {
            case CUSTOM_FIELD_TYPE_SINGLE_SELECT_DROPDOWN, CUSTOM_FIELD_TYPE_RADIO_BUTTON -> {
              List<ValueWithLabel> columnValues = parseCustomFieldValues(customFieldValueJson);
              yield handleSingleSelectCustomField(entityTypeColumn, id, name, refId, configurationView, dataExtractionPath, columnValues);
            }
            case CUSTOM_FIELD_TYPE_SINGLE_CHECKBOX ->
              handleBooleanCustomField(entityTypeColumn, id, name, refId, dataExtractionPath);
            case CUSTOM_FIELD_TYPE_TEXTBOX_SHORT, CUSTOM_FIELD_TYPE_TEXTBOX_LONG ->
              handleTextboxCustomField(entityTypeColumn, id, name, refId, dataExtractionPath);
            case CUSTOM_FIELD_TYPE_MULTI_SELECT_DROPDOWN -> {
              List<ValueWithLabel> columnValues = parseCustomFieldValues(customFieldValueJson);
              yield handleMultiSelectCustomField(entityTypeColumn, id, name, refId, configurationView, dataExtractionPath, columnValues);
            }
            case CUSTOM_FIELD_TYPE_DATE ->
              handleDateTypeCustomField(entityTypeColumn, id, name, refId, dataExtractionPath);
            // Should never be reached due to prior filtering
            default -> {
              log.error("Custom field {} of entity type {} uses an unsupported datatype: {}. Ignoring this custom field", name, entityTypeId, type);
              yield null;
            }
          };
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

  private EntityTypeColumn handleBooleanCustomField(EntityTypeColumn customFieldColumn,
                                                    String id,
                                                    String name,
                                                    String refId,
                                                    String sourceViewExtractor) {
    String valueGetter = String.format(STRING_EXTRACTOR, sourceViewExtractor, refId);
    String filterValueGetter = "lower(%s)";
    return new EntityTypeColumn()
      .name(CUSTOM_FIELD_PREPENDER + id)
      .dataType(new BooleanType().dataType("booleanType"))
      .values(CUSTOM_FIELD_BOOLEAN_VALUES)
      .visibleByDefault(Boolean.TRUE.equals(customFieldColumn.getVisibleByDefault()))
      .valueGetter(valueGetter)
      .filterValueGetter(String.format(filterValueGetter, valueGetter))
      .valueFunction(String.format(filterValueGetter, ":value"))
      .labelAlias(name)
      .queryable(Boolean.TRUE.equals(customFieldColumn.getQueryable()))
      .essential(Boolean.TRUE.equals(customFieldColumn.getEssential()))
      .isCustomField(true);
  }

  private EntityTypeColumn handleSingleSelectCustomField(EntityTypeColumn customFieldColumn,
                                                         String id,
                                                         String name,
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
      .name(CUSTOM_FIELD_PREPENDER + id)
      .dataType(new StringType().dataType("stringType"))
      .values(columnValues)
      .visibleByDefault(Boolean.TRUE.equals(customFieldColumn.getVisibleByDefault()))
      .valueGetter(valueGetter)
      .filterValueGetter(filterValueGetter)
      .labelAlias(name)
      .queryable(Boolean.TRUE.equals(customFieldColumn.getQueryable()))
      .essential(Boolean.TRUE.equals(customFieldColumn.getEssential()))
      .isCustomField(true);
  }

  private EntityTypeColumn handleMultiSelectCustomField(EntityTypeColumn customFieldColumn,
                                                        String id,
                                                        String name,
                                                        String refId,
                                                        String sourceViewName,
                                                        String sourceViewExtractor,
                                                        List<ValueWithLabel> columnValues) {
    String filterValueGetter = String.format(JSONB_ARRAY_EXTRACTOR, sourceViewExtractor, refId);
    String valueGetter = String.format(
      CUSTOM_FIELD_JSONB_ARRAY_VALUE_GETTER,
      executionContext.getTenantId(),
      sourceViewName,
      refId,
      sourceViewExtractor,
      refId
    );
    return new EntityTypeColumn()
      .name(CUSTOM_FIELD_PREPENDER + id)
      .dataType(new JsonbArrayType().dataType("jsonbArrayType").itemDataType(new StringType().dataType("stringType")))
      .values(columnValues)
      .visibleByDefault(Boolean.TRUE.equals(customFieldColumn.getVisibleByDefault()))
      .valueGetter(valueGetter)
      .filterValueGetter(filterValueGetter)
      .labelAlias(name)
      .queryable(Boolean.TRUE.equals(customFieldColumn.getQueryable()))
      .essential(Boolean.TRUE.equals(customFieldColumn.getEssential()))
      .isCustomField(true);
  }

  private EntityTypeColumn handleTextboxCustomField(EntityTypeColumn customFieldColumn,
                                                    String id,
                                                    String name,
                                                    String refId,
                                                    String sourceViewExtractor) {
    return new EntityTypeColumn()
      .name(CUSTOM_FIELD_PREPENDER + id)
      .dataType(new StringType().dataType("stringType"))
      .visibleByDefault(Boolean.TRUE.equals(customFieldColumn.getVisibleByDefault()))
      .valueGetter(String.format(STRING_EXTRACTOR, sourceViewExtractor, refId))
      .labelAlias(name)
      .queryable(Boolean.TRUE.equals(customFieldColumn.getQueryable()))
      .essential(Boolean.TRUE.equals(customFieldColumn.getEssential()))
      .isCustomField(true);
  }

  private EntityTypeColumn handleDateTypeCustomField(EntityTypeColumn customFieldColumn,
                                                     String id,
                                                     String name,
                                                     String refId,
                                                     String sourceViewExtractor) {
    return new EntityTypeColumn()
      .name(CUSTOM_FIELD_PREPENDER + id)
      .dataType(new DateType().dataType("dateType"))
      .visibleByDefault(Boolean.TRUE.equals(customFieldColumn.getVisibleByDefault()))
      .valueGetter(String.format(STRING_EXTRACTOR, sourceViewExtractor, refId))
      .labelAlias(name)
      .queryable(Boolean.TRUE.equals(customFieldColumn.getQueryable()))
      .essential(Boolean.TRUE.equals(customFieldColumn.getEssential()))
      .isCustomField(true);
  }

  private List<EntityTypeColumn> getColumnsWithUniqueAliases(List<EntityTypeColumn> originalList) {
    List<EntityTypeColumn> updatedList = new ArrayList<>();
    Map<String, Integer> aliasCounts = new HashMap<>();
    for (EntityTypeColumn column : originalList) {
      String baseAlias = column.getLabelAlias();
      int count = aliasCounts.compute(baseAlias, (k, v) -> v == null ? 1 : v + 1);
      String uniqueAlias = (count == 1) ? baseAlias : baseAlias + " (" + count + ")";
      EntityTypeColumn updatedColumn = SourceUtils.copyColumn(column);
      updatedColumn.labelAlias(uniqueAlias);
      updatedList.add(updatedColumn);
    }
    return updatedList;
  }

  public CustomEntityType getCustomEntityType(UUID entityTypeId) {
    var definition = field(DEFINITION_FIELD_NAME, String.class);
    String rawCustomEntityType = jooqContext
      .select(definition)
      .from(table(TABLE_NAME))
      .where(field(ID_FIELD_NAME, UUID.class).eq(entityTypeId))
      .fetchOne(definition, String.class);
    if (rawCustomEntityType == null) {
      return null;
    }
    try {
      return objectMapper.readValue(rawCustomEntityType, CustomEntityType.class);
    } catch (JsonProcessingException e) {
      throw new InvalidEntityTypeDefinitionException(e.getMessage(), entityTypeId);
    }
  }

  public void createCustomEntityType(CustomEntityType customEntityType) {
    try {
      jooqContext
        .insertInto(table(TABLE_NAME))
        .columns(field(ID_FIELD_NAME, UUID.class), field(DEFINITION_FIELD_NAME, JSONB.class))
        .values(UUID.fromString(customEntityType.getId()), JSONB.jsonb(objectMapper.writeValueAsString(customEntityType)))
        .execute();
      entityTypeCache.invalidate(executionContext.getTenantId());
    } catch (JsonProcessingException e) {
      throw new InvalidEntityTypeDefinitionException(e.getMessage(), customEntityType);
    }
  }

  public void updateEntityType(EntityType entityType) {
    try {
      jooqContext
        .update(table(TABLE_NAME))
        .set(field(DEFINITION_FIELD_NAME, JSONB.class), JSONB.jsonb(objectMapper.writeValueAsString(entityType)))
        .where(field(ID_FIELD_NAME, UUID.class).eq(UUID.fromString(entityType.getId())))
        .execute();
      entityTypeCache.invalidate(executionContext.getTenantId());
    } catch (JsonProcessingException e) {
      throw new InvalidEntityTypeDefinitionException(e.getMessage(), entityType);
    }
  }

  // Clear the ET cache for testing
  void clearCache() {
    entityTypeCache.invalidateAll();
  }
}
