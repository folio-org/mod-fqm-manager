package org.folio.fqm.service;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.codehaus.plexus.util.StringUtils;
import org.folio.fql.model.field.FqlField;
import org.folio.fql.service.FqlValidationService;
import org.folio.fqm.client.CrossTenantHttpClient;
import org.folio.fqm.client.LanguageClient;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.config.MigrationConfiguration;
import org.folio.fqm.domain.dto.EntityTypeSummary;
import org.folio.fqm.exception.EntityTypeInUseException;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.exception.FieldNotFoundException;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.querytool.domain.dto.ArrayType;
import org.folio.querytool.domain.dto.AvailableJoinsResponse;
import org.folio.querytool.domain.dto.ColumnValues;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.EntityDataType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.querytool.domain.dto.Field;
import org.folio.querytool.domain.dto.JoinFieldPair;
import org.folio.querytool.domain.dto.LabeledValue;
import org.folio.querytool.domain.dto.LabeledValueWithDescription;
import org.folio.querytool.domain.dto.NestedObjectProperty;
import org.folio.querytool.domain.dto.ObjectType;
import org.folio.querytool.domain.dto.SourceColumn;
import org.folio.querytool.domain.dto.UpdateUsedByRequest.OperationEnum;
import org.folio.querytool.domain.dto.ValueSourceApi;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.folio.spring.FolioExecutionContext;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import static java.util.Comparator.comparing;
import static org.folio.fqm.repository.EntityTypeRepository.ID_FIELD_NAME;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Currency;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
@Service
@RequiredArgsConstructor
public class EntityTypeService {

  private static final int COLUMN_VALUE_DEFAULT_PAGE_SIZE = 1000;
  private static final String LANGUAGES_FILEPATH = "languages.json5";
  private static final String GET_LOCALE_SETTINGS_PATH = "configurations/entries";
  private static final Map<String, String> GET_LOCALE_SETTINGS_PARAMS = Map.of(
    "query", "(module==ORG and configName==localeSettings)"
  );
  private static final List<String> EXCLUDED_CURRENCY_CODES = List.of(
    "XUA", "AYM", "AFA", "ADP", "ATS", "AZM", "BYB", "BYR", "BEF", "BOV", "BGL", "CLF", "COU", "CUC", "CYP", "NLG", "EEK", "XBA", "XBB",
    "XBC", "XBD", "FIM", "FRF", "XFO", "XFU", "GHC", "DEM", "XAU", "GRD", "GWP", "IEP", "ITL", "LVL", "LTL", "LUF", "MGF", "MTL", "MRO", "MXV",
    "MZM", "XPD", "PHP", "XPT", "PTE", "ROL", "RUR", "CSD", "SLE", "SLL", "XAG", "SKK", "SIT", "ESP", "XDR", "XSU", "SDD", "SRG", "STD", "XTS",
    "TPE", "TRL", "TMM", "USN", "USS", "XXX", "UYI", "VEB", "VEF", "VED", "CHE", "CHW", "YUM", "ZWN", "ZMK", "ZWD", "ZWR");

  private final EntityTypeRepository entityTypeRepository;
  private final EntityTypeFlatteningService entityTypeFlatteningService;
  private final EntityTypeValidationService entityTypeValidationService;
  private final LocalizationService localizationService;
  private final MigrationConfiguration migrationConfiguration;
  private final MigrationService migrationService;
  private final QueryProcessorService queryService;
  private final CrossTenantHttpClient crossTenantHttpClient;
  private final PermissionsService permissionsService;
  private final CrossTenantQueryService crossTenantQueryService;
  private final LanguageClient languageClient;
  private final FolioExecutionContext folioExecutionContext;
  private final ClockService clockService;
  private final SimpleHttpClient simpleHttpClient;

  /**
   * Returns the list of all entity types.
   *
   * @param entityTypeIds If provided, only the entity types having the provided Ids will be included in the results
   */
  public List<EntityTypeSummary> getEntityTypeSummary(Set<UUID> entityTypeIds, boolean includeInaccessible, boolean includeAll) {
    Set<String> userPermissions = permissionsService.getUserPermissions();
    return entityTypeRepository
      .getEntityTypeDefinitions(entityTypeIds, folioExecutionContext.getTenantId())
      .filter(entityType -> !Boolean.TRUE.equals(entityType.getDeleted()))
      .filter(entityType -> includeAll || !Boolean.TRUE.equals(entityType.getPrivate()))
      .filter(entityType -> !Boolean.TRUE.equals(entityType.getAdditionalProperty("isCustom")) || currentUserCanAccessCustomEntityType(entityType.getId()))
      .filter(entityType -> includeInaccessible || userPermissions.containsAll(permissionsService.getRequiredPermissions(entityType)))
      .map(entityType -> {
        EntityTypeSummary result = new EntityTypeSummary()
          .id(UUID.fromString(entityType.getId()))
          .label(localizationService.getEntityTypeLabel(entityType))
          .isCustom(Boolean.TRUE.equals(entityType.getAdditionalProperty("isCustom")))
          .crossTenantQueriesEnabled(Boolean.TRUE.equals(entityType.getCrossTenantQueriesEnabled()) && crossTenantQueryService.isCentralTenant());

        if (includeInaccessible) {
          return result.missingPermissions(
            permissionsService.getRequiredPermissions(entityType)
              .stream()
              .filter(permission -> !userPermissions.contains(permission))
              .toList()
          );
        }

        if (Boolean.TRUE.equals(result.getIsCustom())) {
          result.setCreatedAt(Date.from(Instant.parse((String) entityType.getAdditionalProperty("createdAt"))));
          result.setUpdatedAt(Date.from(Instant.parse((String) entityType.getAdditionalProperty("updatedAt"))));
        }

        return result;
      })
      .sorted(comparing(EntityTypeSummary::getLabel, String.CASE_INSENSITIVE_ORDER))
      .toList();
  }

  /**
   * Returns the definition of a given entity type.
   *
   * @param entityTypeId  the ID to search for
   * @param includeHidden Indicates whether the hidden column should be displayed.
   *                      If set to true, the hidden column will be included in the output
   * @return the entity type definition if found, empty otherwise
   */
  public EntityType getEntityTypeDefinition(UUID entityTypeId, boolean includeHidden) {
    verifyAccessForPossibleCustomEntityType(entityTypeId);
    EntityType entityType = entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, folioExecutionContext.getTenantId(), false);
    boolean crossTenantEnabled = Boolean.TRUE.equals(entityType.getCrossTenantQueriesEnabled())
      && crossTenantQueryService.isCentralTenant();
    List<EntityTypeColumn> filteredColumns = entityType.getColumns().stream()
      .filter(col -> includeHidden || !Boolean.TRUE.equals(col.getHidden()))
      .map(col -> {
        EntityDataType filteredDataType = filterHiddenFields(col.getDataType(), includeHidden);
        return (EntityTypeColumn) col.toBuilder().dataType(filteredDataType).build();
      })
      .toList();
    return entityType
      .columns(filteredColumns)
      .crossTenantQueriesEnabled(crossTenantEnabled);
  }

  /**
   * Return top 1000 values of an entity type field, matching the given search text
   *
   * @param fieldName  Name of the field for which values have to be returned
   * @param searchText Nullable search text. If a search text is provided, the returned values will include only those
   *                   that contain the specified searchText.
   */
  public ColumnValues getFieldValues(UUID entityTypeId, String fieldName, @Nullable String searchText) {
    searchText = searchText == null ? "" : searchText;
    verifyAccessForPossibleCustomEntityType(entityTypeId);
    EntityType entityType = entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, folioExecutionContext.getTenantId(), false);

    Field field = FqlValidationService
      .findFieldDefinition(new FqlField(fieldName), entityType)
      .orElseThrow(() -> new FieldNotFoundException(entityType.getName(), fieldName));

    if (!CollectionUtils.isEmpty(field.getValues())) {
      return getFieldValuesFromEntityTypeDefinition(field, searchText);
    }

    List<String> tenantsToQuery = crossTenantQueryService.getTenantsToQueryForColumnValues(entityType);
    if (field.getValueSourceApi() != null) {
      return getFieldValuesFromApi(field, searchText, tenantsToQuery);
    }

    if (field.getSource() != null) {
      if (field.getSource().getType() == SourceColumn.TypeEnum.ENTITY_TYPE) {
        EntityType sourceEntityType = entityTypeFlatteningService.getFlattenedEntityType(field.getSource().getEntityTypeId(), folioExecutionContext.getTenantId(), false);
        permissionsService.verifyUserHasNecessaryPermissions(sourceEntityType, false);

        Field sourceField = FqlValidationService
          .findFieldDefinition(new FqlField(field.getSource().getColumnName()), sourceEntityType)
          .orElseThrow(() -> new FieldNotFoundException(sourceEntityType.getName(), field.getSource().getColumnName()));
        if (sourceField.getValueSourceApi() != null) {
          return getFieldValuesFromApi(sourceField, searchText, tenantsToQuery);
        }

        return getFieldValuesFromEntityType(sourceEntityType, sourceField.getName(), searchText);
      } else if (field.getSource().getType() == SourceColumn.TypeEnum.FQM) {
        return switch (Objects.requireNonNull(field.getSource().getName(), "Value sources with the FQM type require the source name to be configured")) {
          // entity types using this MUST declare a dependency on view `_mod_finance_storage_exchange_rate_availability_indicator`
          // to ensure that this source is available
          case "currency" -> getCurrencyValues();
          // entity types using this MUST declare a dependency on view `_mod_search_languages_availability_indicator`
          // to ensure that this source is available
          case "languages" -> getLanguages(searchText, tenantsToQuery);
          case "tenant_id" -> getTenantIds(entityType);
          case "tenant_name" -> getTenantNames(entityType);
          // instructs query builder to provide organization finder plugin, so no values need be returned here
          case "organization", "donor_organization" -> ColumnValues.builder().content(List.of()).build();
          default -> throw new InvalidEntityTypeDefinitionException("Unhandled source name \"" + field.getSource().getName() + "\" for the FQM value source type in column \"" + fieldName + '"', entityType);
        };
      }
    }

    throw new InvalidEntityTypeDefinitionException("Unable to retrieve column values for " + fieldName, entityType);
  }

  private EntityDataType filterHiddenFields(EntityDataType dataType, boolean includeHidden) {
    switch (dataType) {
      case ObjectType objectType -> {
        List<NestedObjectProperty> filteredProps = objectType.getProperties().stream()
          .map(prop -> {
            EntityDataType propDataType = prop.getDataType();
            if (propDataType != null) {
              EntityDataType filteredType = filterHiddenFields(propDataType, includeHidden);
              return prop.toBuilder().dataType(filteredType).build();
            }
            return prop;
          })
          .filter(prop -> includeHidden || !Boolean.TRUE.equals(prop.getHidden()))
          .toList();
        return objectType.toBuilder().properties(filteredProps).build();
      }
      case ArrayType arrayType -> {
        EntityDataType filteredItemType = filterHiddenFields(arrayType.getItemDataType(), includeHidden);
        return arrayType.toBuilder().itemDataType(filteredItemType).build();
      }
      default -> {
        return dataType;
      }
    }
  }

  private ColumnValues getTenantIds(EntityType entityType) {
    List<String> tenants = crossTenantQueryService.getTenantsToQueryForColumnValues(entityType);
    List<ValueWithLabel> tenantValues = tenants
      .stream()
      .map(tenant -> new ValueWithLabel().value(tenant).label(tenant))
      .toList();
    return new ColumnValues().content(tenantValues);
  }

  private ColumnValues getTenantNames(EntityType entityType) {
    List<Pair<String, String>> tenantMaps = crossTenantQueryService.getTenantIdNamePairs(
      entityType,
      folioExecutionContext.getUserId()
    );
    List<ValueWithLabel> tenantValues = tenantMaps
      .stream()
      .map(tenant -> new ValueWithLabel().value(tenant.getKey()).label(tenant.getValue()))
      .toList();
    return new ColumnValues().content(tenantValues);
  }

  private ColumnValues getFieldValuesFromEntityTypeDefinition(Field field, String searchText) {
    List<ValueWithLabel> filteredValues = field
      .getValues()
      .stream()
      .filter(valueWithLabel -> valueWithLabel.getLabel().toLowerCase().contains(searchText.toLowerCase()))
      .distinct()
      .sorted(comparing(ValueWithLabel::getLabel, String.CASE_INSENSITIVE_ORDER))
      .toList();
    return new ColumnValues().content(filteredValues);
  }

  private ColumnValues getFieldValuesFromApi(Field field, String searchText, List<String> tenantsToQuery) {
    Set<ValueWithLabel> resultSet = new HashSet<>();
    int failureCount = 0;
    FeignException lastException = null;

    for (String tenantId : tenantsToQuery) {
      try {
        ValueSourceApi valueSourceApi = field.getValueSourceApi();
        Map<String, String> queryParams = Objects.requireNonNullElseGet(
          valueSourceApi.getQueryParams(),
          () -> Map.of("limit", String.valueOf(COLUMN_VALUE_DEFAULT_PAGE_SIZE))
        );
        String rawJson = crossTenantHttpClient.get(valueSourceApi.getPath(), queryParams, tenantId);
        DocumentContext parsedJson = JsonPath.parse(rawJson);
        List<String> values = parsedJson.read(field.getValueSourceApi().getValueJsonPath());
        List<String> labels = parsedJson.read(field.getValueSourceApi().getLabelJsonPath());
        log.info("Obtained {} values from API {} in tenant {} for field {}", values.size(), valueSourceApi.getPath(), tenantId, field.getName());
        for (int i = 0; i < values.size(); i++) {
          String value = values.get(i);
          String label = labels.get(i);
          if (label.toLowerCase().contains(searchText.toLowerCase())) {
            resultSet.add(new ValueWithLabel().value(value).label(label));
          }
        }
      } catch (FeignException.Unauthorized e) {
        log.error("Failed to get column values from {} tenant due to exception:", tenantId, e);
        failureCount++;
        lastException = e;
      }
      catch (FeignException.NotFound e) {
        log.error("Value source API {} not found in tenant {}", field.getValueSourceApi().getPath(), tenantId);
        failureCount++;
        lastException = e;
      }
    }

    if (failureCount > 0 && failureCount == tenantsToQuery.size()) {
      log.error("Failed to get column values from all tenants. Rethrowing last exception.");
      throw lastException;
    }

    List<ValueWithLabel> results = new ArrayList<>(resultSet);
    results.sort(Comparator.comparing(ValueWithLabel::getLabel, String.CASE_INSENSITIVE_ORDER));
    return new ColumnValues().content(results);
  }

  private ColumnValues getFieldValuesFromEntityType(EntityType entityType, String fieldName, String searchText) {
    String fql = "{\"%s\": {\"$regex\": \"%s\"}}".formatted(fieldName, searchText);
    List<Map<String, Object>> results = queryService.processQuery(
      entityType,
      fql,
      List.of(ID_FIELD_NAME, fieldName),
      COLUMN_VALUE_DEFAULT_PAGE_SIZE
    );
    List<ValueWithLabel> valueWithLabels = results
      .stream()
      .map(result -> toValueWithLabel(result, fieldName))
      .sorted(comparing(ValueWithLabel::getLabel, String.CASE_INSENSITIVE_ORDER))
      .toList();
    return new ColumnValues().content(valueWithLabels);
  }

  private static ColumnValues getCurrencyValues() {
    List<ValueWithLabel> currencies =
      new ArrayList<>(Currency
        .getAvailableCurrencies()
        .stream()
        .filter(currency -> !EXCLUDED_CURRENCY_CODES.contains(currency.getCurrencyCode()))
        .map(currency -> new ValueWithLabel()
          .value(currency.getCurrencyCode())
          .label(String.format("%s (%s)", currency.getDisplayName(), currency.getCurrencyCode())))
        .sorted(comparing(ValueWithLabel::getLabel, String.CASE_INSENSITIVE_ORDER))
        .toList());
    return new ColumnValues().content(currencies);
  }

  private ColumnValues getLanguages(String searchText, List<String> tenantsToQuery) {
    Set<String> langSet = new HashSet<>();
    for (String tenantId : tenantsToQuery) {
      try {
        String rawJson = languageClient.get(tenantId);
        DocumentContext parsedJson = JsonPath.parse(rawJson);
        List<String> values = parsedJson.read("$.facets.languages.values.*.id");
        langSet.addAll(values);
      } catch (FeignException.Unauthorized | FeignException.BadRequest e) {
        log.error("Failed to get languages for tenant {} due to exception {}", tenantId, e.getMessage());
      }
    }

    List<ValueWithLabel> results = new ArrayList<>();
    ObjectMapper mapper =
      JsonMapper
        .builder()
        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
        .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
        .build();

    List<Map<String, String>> languages = List.of();
    try (InputStream input = getClass().getClassLoader().getResourceAsStream(LANGUAGES_FILEPATH)) {
      languages = mapper.readValue(input, new TypeReference<>() {
      });
    } catch (IOException e) {
      log.error("Failed to read language file. Language display names may not be properly translated.");
    }

    Locale folioLocale;
    try {
      String localeSettingsResponse = simpleHttpClient.get(GET_LOCALE_SETTINGS_PATH, GET_LOCALE_SETTINGS_PARAMS);
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode localeSettingsNode = objectMapper.readTree(localeSettingsResponse);
      String valueString = localeSettingsNode
        .path("configs")
        .get(0)
        .path("value")
        .asText();
      JsonNode valueNode = objectMapper.readTree(valueString);
      String localeString = valueNode.path("locale").asText();
      folioLocale = new Locale(localeString.substring(0, 2)); // Java locales are in form xx, FOLIO stores locales as xx-YY
    } catch (Exception e) {
      log.debug("No default locale defined. Defaulting to English for language translations.");
      folioLocale = Locale.ENGLISH;
    }

    Map<String, String> a3ToNameMap = new HashMap<>();
    Map<String, String> a3ToA2Map = new HashMap<>();
    for (Map<String, String> language : languages) {
      a3ToA2Map.put(language.get("alpha3"), language.get("alpha2"));
      a3ToNameMap.put(language.get("alpha3"), language.get("name"));
    }

    for (String code : langSet) {
      String label;
      String a2Code = a3ToA2Map.get(code);
      String name = a3ToNameMap.get(code);
      if (StringUtils.isNotEmpty(a2Code)) {
        Locale languageLocale = new Locale(a2Code);
        label = languageLocale.getDisplayLanguage(folioLocale);
      } else if (StringUtils.isNotEmpty(name)) {
        label = name;
      } else if (StringUtils.isNotEmpty(code)) {
        label = code;
      } else {
        continue;
      }
      if (label.toLowerCase().contains(searchText.toLowerCase())) {
        results.add(new ValueWithLabel().value(code).label(label));
      }
    }
    results.sort(Comparator.comparing(ValueWithLabel::getLabel, String.CASE_INSENSITIVE_ORDER));
    return new ColumnValues().content(results);
  }

  private static ValueWithLabel toValueWithLabel(Map<String, Object> allValues, String fieldName) {
    var valueWithLabel = new ValueWithLabel().label(getFieldValue(allValues, fieldName));
    return allValues.containsKey(ID_FIELD_NAME)
      ? valueWithLabel.value(getFieldValue(allValues, ID_FIELD_NAME))
      : valueWithLabel.value(valueWithLabel.getLabel()); // value = label for entity types that do not have "id" column
  }

  private static String getFieldValue(Map<String, Object> allValues, String fieldName) {
    return allValues.get(fieldName).toString();
  }

  CustomEntityType getCustomEntityType(UUID entityTypeId) {
    var customET = entityTypeRepository.getCustomEntityType(entityTypeId);
    if (customET == null || !Boolean.TRUE.equals(customET.getIsCustom())) {
      throw new EntityTypeNotFoundException(entityTypeId, String.format("Entity type %s could not be found or is not a custom entity type.", entityTypeId));
    }

    return customET;
  }

  public CustomEntityType getCustomEntityTypeWithAccessCheck(UUID entityTypeId) {
    CustomEntityType customET = getCustomEntityType(entityTypeId);
    permissionsService.verifyUserCanAccessCustomEntityType(customET);
    return customET;
  }

  public CustomEntityType createCustomEntityType(CustomEntityType customEntityType) {
    var now = clockService.now();
    UUID customEntityTypeId;
    String customEntityTypeIdString = customEntityType.getId();
    // UUID.fromString() will pad 0's onto invalid UUID strings to make valid UUIDs, which can lead to unexpected behavior.
    // This block ensures that the service accepts only valid UUID strings
    try {
      if (customEntityTypeIdString == null || customEntityTypeIdString.isEmpty()) {
        customEntityTypeId = UUID.randomUUID();
      } else {
        customEntityTypeId = UUID.fromString(customEntityTypeIdString);
        if (!customEntityTypeId.toString().equals(customEntityTypeIdString)) {
          throw new IllegalArgumentException("Invalid UUID format");
        }
      }
    } catch (IllegalArgumentException e) {
      throw new InvalidEntityTypeDefinitionException("Invalid string provided for entity type ID", customEntityType);
    }

    if (customEntityType.getOwner() != null && !customEntityType.getOwner().equals(folioExecutionContext.getUserId())) {
      throw new InvalidEntityTypeDefinitionException(
        "owner ID mismatch: the provided owner ID does not match the current user's ID. This field should be omitted " +
          "or match the authenticated user.",
        customEntityType
      );
    }

    CustomEntityType updatedCustomEntityType = customEntityType.toBuilder()
      .id(customEntityTypeId.toString())
      .version(migrationConfiguration.getCurrentVersion())
      .createdAt(now)
      .updatedAt(now)
      .owner(folioExecutionContext.getUserId())
      .build();

    entityTypeValidationService.validateCustomEntityType(customEntityTypeId, updatedCustomEntityType);
    entityTypeRepository.createCustomEntityType(updatedCustomEntityType);
    migrationService.updateCustomEntityMigrationMappings();
    return updatedCustomEntityType;
  }

  public CustomEntityType updateCustomEntityType(UUID entityTypeId, CustomEntityType customEntityType) {
    CustomEntityType oldET = getCustomEntityType(entityTypeId);
    permissionsService.verifyUserCanAccessCustomEntityType(oldET);

    CustomEntityType updatedCustomEntityType = customEntityType.toBuilder()
      .version(migrationConfiguration.getCurrentVersion())
      .createdAt(oldET.getCreatedAt())
      .updatedAt(clockService.now())
      .owner(Objects.requireNonNullElse(customEntityType.getOwner(), oldET.getOwner()))
      .build();

    entityTypeValidationService.validateCustomEntityType(entityTypeId, updatedCustomEntityType);
    entityTypeRepository.updateEntityType(updatedCustomEntityType);
    migrationService.updateCustomEntityMigrationMappings();
    return updatedCustomEntityType;
  }

  boolean currentUserCanAccessCustomEntityType(String entityTypeId) {
    CustomEntityType customET = getCustomEntityType(UUID.fromString(entityTypeId));
    return permissionsService.canUserAccessCustomEntityType(customET);
  }

  void verifyAccessForPossibleCustomEntityType(UUID entityTypeId) {
    entityTypeRepository.getEntityTypeDefinition(entityTypeId, folioExecutionContext.getTenantId())
      .filter(et -> Boolean.TRUE.equals(et.getAdditionalProperty("isCustom")))
      .map(et -> getCustomEntityType(entityTypeId))
      .ifPresent(permissionsService::verifyUserCanAccessCustomEntityType);
  }

  public void deleteCustomEntityType(UUID entityTypeId) {
    var customEntityType = getCustomEntityType(entityTypeId);
    // We don't need full validation, but we definitely need to make sure this is a custom ET...
    if (customEntityType.getIsCustom() == null) {
      throw new EntityTypeNotFoundException(entityTypeId, "Entity type " + entityTypeId + " is not a custom entity type, so it cannot be deleted");
    }
    permissionsService.verifyUserCanAccessCustomEntityType(customEntityType);
    ensureEntityTypeIsNotInUse(customEntityType);
    CustomEntityType deletedCustomEntityType = customEntityType.toBuilder()
      .deleted(true)
      .updatedAt(clockService.now())
      .build();
    entityTypeRepository.updateEntityType(deletedCustomEntityType);
    migrationService.updateCustomEntityMigrationMappings();
  }

  /**
   * Get all entity types that the current user has access to, including both simples and flattened composites.
   */
  Map<UUID, EntityType> getAccessibleEntityTypesById() {
    Set<String> userPermissions = permissionsService.getUserPermissions();
    return entityTypeRepository
      .getEntityTypeDefinitions(Set.of(), folioExecutionContext.getTenantId())
      .filter(entityType -> !Boolean.TRUE.equals(entityType.getDeleted()))
      .filter(entityType -> !Boolean.TRUE.equals(entityType.getAdditionalProperty("isCustom")) || currentUserCanAccessCustomEntityType(entityType.getId()))
      .map(entityType -> entityTypeFlatteningService.getFlattenedEntityType(UUID.fromString(entityType.getId()), folioExecutionContext.getTenantId(), true))
      .filter(entityType -> userPermissions.containsAll(permissionsService.getRequiredPermissions(entityType)))
      .collect(Collectors.toMap(et -> UUID.fromString(et.getId()), et -> et, (a, b) -> a));
  }

  public AvailableJoinsResponse getAvailableJoins(List<EntityTypeSourceEntityType> sources, UUID targetEntityTypeId) {
    // Treat null or empty list as null, to simplify things
    sources = sources == null || sources.isEmpty() ? null : sources;

    Map<UUID, EntityType> accessibleEntityTypesById = getAccessibleEntityTypesById();

    // Special case where the custom ET isn't provided: provide all accessible entity types as possible targets
    if (sources == null) {
      return new AvailableJoinsResponse()
        .availableTargetIds(entityTypesToSortedLabeledValues(accessibleEntityTypesById.values().stream().filter(EntityTypeUtils::isSimple)));
    }

    CustomEntityType tempCustomEntityType = new CustomEntityType()
      .id(UUID.randomUUID().toString())
      .name("temp custom entity type for join discovery")
      .sources(new ArrayList<>(sources)); // Rebuild sources, to get the proper type
    EntityType flattenedCustomEntityType = entityTypeFlatteningService.getFlattenedEntityType(tempCustomEntityType, folioExecutionContext.getTenantId(), true);

    // If no target ET is provided, return the possible options
    if (targetEntityTypeId == null) {
      return new AvailableJoinsResponse()
        .availableTargetIds(discoverTargetEntityTypes(flattenedCustomEntityType, accessibleEntityTypesById));
    }

    // Otherwise, return the possible relationships between the custom ET and the target ET
    EntityType targetEntityType = accessibleEntityTypesById.get(targetEntityTypeId);
    return new AvailableJoinsResponse().availableJoinConditions(discoverJoinConditions(flattenedCustomEntityType, targetEntityType));
  }

  public Optional<EntityType> updateEntityTypeUsedBy(UUID entityTypeId, String usedBy, OperationEnum operation) {
    return entityTypeRepository.getEntityTypeDefinition(entityTypeId, folioExecutionContext.getTenantId())
      .map(entityType -> {
        Set<String> usedBySet = new HashSet<>(
          Optional.ofNullable(entityType.getUsedBy()).orElse(Collections.emptyList())
        );

        if (operation == OperationEnum.ADD) {
          usedBySet.add(usedBy);
        } else if (operation == OperationEnum.REMOVE) {
          usedBySet.remove(usedBy);
        }

        entityType.setUsedBy(new ArrayList<>(usedBySet));
        entityTypeRepository.updateEntityType(entityType);
        return entityType;
      });
  }

  private void ensureEntityTypeIsNotInUse(EntityType entityType) {
    ensureNoEntityTypesUseThisEntityType(entityType);
    ensureNoConsumersUseThisEntityType(entityType);
  }

  private void ensureNoEntityTypesUseThisEntityType(EntityType entityType) {
    List<EntityType> dependentEntityTypes = entityTypeRepository.getEntityTypeDefinitions(Set.of(), folioExecutionContext.getTenantId())
      .filter(et -> !Boolean.TRUE.equals(et.getDeleted()))
      .filter(et -> dependsOnTargetEntityType(et, entityType))
      .toList();
    if (!dependentEntityTypes.isEmpty()) {
      String usedBy = dependentEntityTypes
        .stream()
        .map(et -> String.format("%s (id %s)", et.getName(), et.getId()))
        .collect(Collectors.joining(", "));
      throw new EntityTypeInUseException(
        entityType,
        "Cannot delete custom entity type because it is used as a source by other entity types: " + usedBy
      );
    }
  }

  private boolean dependsOnTargetEntityType(EntityType entityType, EntityType target) {
    return entityType.getSources() != null &&
      entityType.getSources()
        .stream()
        .filter(EntityTypeSourceEntityType.class::isInstance)
        .map(EntityTypeSourceEntityType.class::cast)
        .anyMatch(source ->
          source.getTargetId() != null &&
            source.getTargetId().toString().equals(target.getId())
        );
  }

  private void ensureNoConsumersUseThisEntityType(EntityType entityType) {
    if (entityType.getUsedBy() != null && !entityType.getUsedBy().isEmpty()) {
      String usedBy = String.join(", ", entityType.getUsedBy());
      throw new EntityTypeInUseException(
        entityType,
        "Cannot delete custom entity type because it is used by the following consumers: " + usedBy
      );
    }
  }

  /**
   * Identifies joinable entity types by analyzing both direct joins from fields within the customEntityType
   * and reverse joins from other entity types that can join to this customEntityType.
   */
  private static List<LabeledValueWithDescription> discoverTargetEntityTypes(EntityType customEntityType, Map<UUID, EntityType> accessibleEntityTypesById) {
    List<EntityTypeColumn> customEntityTypeColumns = customEntityType.getColumns().stream()
      .toList();
    Set<EntityType> targetEntityTypes = new HashSet<>();
    for (EntityTypeColumn customColumn : customEntityTypeColumns) {
      for (EntityType potentialTargetEntityType : accessibleEntityTypesById.values()) {
        if (!EntityTypeUtils.isSimple(potentialTargetEntityType)) {
          continue;
        }
        for (EntityTypeColumn targetColumn : potentialTargetEntityType.getColumns()) {
          if (canColumnsJoin(customColumn, targetColumn, potentialTargetEntityType)) {
            targetEntityTypes.add(potentialTargetEntityType);
            break; // Move on to the next potential target entity type
          }
        }
      }
    }

    return entityTypesToSortedLabeledValues(targetEntityTypes.stream());
  }

  /**
   * Identifies joinable fields in the target entity type based on direct or reverse joins with the source entity type.
   */
  static List<JoinFieldPair> discoverJoinConditions(EntityType customEntityType, EntityType targetEntityType) {
    List<JoinFieldPair> joinConditions = new ArrayList<>();
    for (EntityTypeColumn customCol : customEntityType.getColumns()) {
      for (EntityTypeColumn targetCol : targetEntityType.getColumns()) {
        if (canColumnsJoin(customCol, targetCol, targetEntityType)) {
          joinConditions.add(new JoinFieldPair(
            new LabeledValue(customCol.getLabelAlias()).value(customCol.getName()),
            new LabeledValue(targetCol.getLabelAlias()).value(targetCol.getName())
          ));
        }
      }
    }
    return joinConditions.stream()
      .sorted(comparing((JoinFieldPair pair) -> pair.getSourceField().getLabel(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(pair -> pair.getTargetField().getLabel(), String.CASE_INSENSITIVE_ORDER))
      .toList();
  }

  /**
   * Helper method to determine if two columns can be joined together.
   */
  private static boolean canColumnsJoin(EntityTypeColumn customEntityTypeColumn, EntityTypeColumn targetEntityTypeColumn,
                                        EntityType targetEntityType) {
    // Check if target column can join to custom column
    boolean targetCanJoinToCustom =
      targetEntityTypeColumn.getJoinsTo() != null
        && targetEntityTypeColumn.getJoinsTo().stream()
        .anyMatch(join -> join.getTargetId().equals(customEntityTypeColumn.getOriginalEntityTypeId())
          && join.getTargetField() != null
          && (customEntityTypeColumn.getName().equals(join.getTargetField()) || customEntityTypeColumn.getName().endsWith('.' + join.getTargetField())));

    // Check if custom column can join to target column
    boolean customCanJoinToTarget =
      customEntityTypeColumn.getJoinsTo() != null
        && customEntityTypeColumn.getJoinsTo().stream()
        .anyMatch(join -> (join.getTargetId().equals(targetEntityTypeColumn.getOriginalEntityTypeId()) || join.getTargetId().toString().equals(targetEntityType.getId()))
          && join.getTargetField() != null
          && (targetEntityTypeColumn.getName().equals(join.getTargetField()) || targetEntityTypeColumn.getName().endsWith('.' + join.getTargetField())));
    return targetCanJoinToCustom || customCanJoinToTarget;
  }

  /**
   * Converts a stream of entity type columns to sorted labeled values.
   */
  private static List<LabeledValue> columnsToSortedLabeledValues(Stream<EntityTypeColumn> columns) {
    return columns
      .map(col -> new LabeledValue(col.getLabelAlias()).value(col.getName()))
      .sorted(comparing(LabeledValue::getLabel, String.CASE_INSENSITIVE_ORDER))
      .toList();
  }

  /**
   * Converts a collection of entity types to sorted labeled values.
   */
  private static List<LabeledValueWithDescription> entityTypesToSortedLabeledValues(Stream<EntityType> entityTypes) {
    return entityTypes
      .map(entityType ->
        new LabeledValueWithDescription(entityType.getLabelAlias())
          .value(entityType.getId())
          .description(entityType.getDescription())
      )
      .sorted(comparing(LabeledValueWithDescription::getLabel, String.CASE_INSENSITIVE_ORDER))
      .toList();
  }
}
