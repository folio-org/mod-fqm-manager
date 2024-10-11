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
import org.codehaus.plexus.util.StringUtils;
import org.folio.fql.model.field.FqlField;
import org.folio.fql.service.FqlValidationService;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.domain.dto.EntityTypeSummary;
import org.folio.fqm.exception.FieldNotFoundException;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.ColumnValues;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.Field;
import org.folio.querytool.domain.dto.SourceColumn;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;
import static org.folio.fqm.repository.EntityTypeRepository.ID_FIELD_NAME;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
@Log4j2
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
  private final LocalizationService localizationService;
  private final QueryProcessorService queryService;
  private final SimpleHttpClient simpleHttpClient;
  private final PermissionsService permissionsService;
  private final CrossTenantQueryService crossTenantQueryService;

  /**
   * Returns the list of all entity types.
   *
   * @param entityTypeIds If provided, only the entity types having the provided Ids will be included in the results
   */
  @Transactional(readOnly = true)
  public List<EntityTypeSummary> getEntityTypeSummary(Set<UUID> entityTypeIds, boolean includeInaccessible) {
    Set<String> userPermissions = permissionsService.getUserPermissions();
    return entityTypeRepository
      .getEntityTypeDefinitions(entityTypeIds, null)
      .filter(entityType -> !Boolean.TRUE.equals(entityType.getPrivate()))
      .filter(entityType -> includeInaccessible || userPermissions.containsAll(permissionsService.getRequiredPermissions(entityType)))
      .map(entityType -> {
        EntityTypeSummary result = new EntityTypeSummary()
          .id(UUID.fromString(entityType.getId()))
          .label(localizationService.getEntityTypeLabel(entityType.getName()))
          .crossTenantQueriesEnabled(Boolean.TRUE.equals(entityType.getCrossTenantQueriesEnabled()) && crossTenantQueryService.isCentralTenant());
        if (includeInaccessible) {
          return result.missingPermissions(
            permissionsService.getRequiredPermissions(entityType)
              .stream()
              .filter(permission -> !userPermissions.contains(permission))
              .toList()
          );
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
   * @param sortColumns   If true, columns will be alphabetically sorted by their translations
   * @return the entity type definition if found, empty otherwise
   */
  public EntityType getEntityTypeDefinition(UUID entityTypeId, boolean includeHidden, boolean sortColumns) {
    EntityType entityType = entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null);
    boolean crossTenantEnabled = Boolean.TRUE.equals(entityType.getCrossTenantQueriesEnabled())
      && crossTenantQueryService.isCentralTenant();
    List<EntityTypeColumn> columns = entityType
      .getColumns()
      .stream()
      .filter(column -> includeHidden || !Boolean.TRUE.equals(column.getHidden())) // Filter based on includeHidden flag
      .toList();
    if (sortColumns) {
      columns = columns.stream()
        .sorted(nullsLast(comparing(Field::getLabelAlias, String.CASE_INSENSITIVE_ORDER)))
        .toList();
    }
    return entityType
      .columns(columns)
      .crossTenantQueriesEnabled(crossTenantEnabled);
  }

  /**
   * Return top 1000 values of an entity type field, matching the given search text
   *
   * @param fieldName  Name of the field for which values have to be returned
   * @param searchText Nullable search text. If a search text is provided, the returned values will include only those
   *                   that contain the specified searchText.
   */
  @Transactional(readOnly = true)
  public ColumnValues getFieldValues(UUID entityTypeId, String fieldName, @Nullable String searchText) {
    searchText = searchText == null ? "" : searchText;
    EntityType entityType = entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null);

    Field field = FqlValidationService
      .findFieldDefinition(new FqlField(fieldName), entityType)
      .orElseThrow(() -> new FieldNotFoundException(entityType.getName(), fieldName));

    if (field.getValues() != null) {
      return getFieldValuesFromEntityTypeDefinition(field, searchText);
    }

    List<String> tenantsToQuery = crossTenantQueryService.getTenantsToQueryForColumnValues(entityType);
    if (field.getValueSourceApi() != null) {
      return getFieldValuesFromApi(field, searchText, tenantsToQuery);
    }

    if (field.getSource() != null) {
      if (field.getSource().getType() == SourceColumn.TypeEnum.ENTITY_TYPE) {
        EntityType sourceEntityType = entityTypeFlatteningService.getFlattenedEntityType(field.getSource().getEntityTypeId(), null);

        permissionsService.verifyUserHasNecessaryPermissions(sourceEntityType, false);

        return getFieldValuesFromEntityType(sourceEntityType, field.getSource().getColumnName(), searchText);
      } else if (field.getSource().getType() == SourceColumn.TypeEnum.FQM) {
        switch (Objects.requireNonNull(field.getSource().getName(), "Value sources with the FQM type require the source name to be configured")) {
          case "currency" -> {
            return getCurrencyValues();
          }
          case "tenant_id" -> {
            return getTenantIds(entityType);
          }
          case "languages" -> {
            return getLanguages(searchText, tenantsToQuery);
          }
          default -> {
            throw new InvalidEntityTypeDefinitionException("Unhandled source name \"" + field.getSource().getName() + "\" for the FQM value source type in column \"" + fieldName + '"', entityType);
          }
        }
      }
    }

    throw new InvalidEntityTypeDefinitionException("Unable to retrieve column values for " + fieldName, entityType);
  }

  private ColumnValues getTenantIds(EntityType entityType) {
    List<String> tenants = crossTenantQueryService.getTenantsToQueryForColumnValues(entityType);
    List<ValueWithLabel> tenantValues = tenants
      .stream()
      .map(tenant -> new ValueWithLabel().value(tenant).label(tenant))
      .toList();
    return new ColumnValues().content(tenantValues);
  }

  private ColumnValues getFieldValuesFromEntityTypeDefinition(Field field, String searchText) {
    List<ValueWithLabel> filteredValues = field
      .getValues()
      .stream()
      .filter(valueWithLabel -> valueWithLabel.getLabel().contains(searchText))
      .distinct()
      .sorted(comparing(ValueWithLabel::getLabel, String.CASE_INSENSITIVE_ORDER))
      .toList();
    return new ColumnValues().content(filteredValues);
  }

  private ColumnValues getFieldValuesFromApi(Field field, String searchText, List<String> tenantsToQuery) {
    Set<ValueWithLabel> resultSet = new HashSet<>();
    for (String tenantId : tenantsToQuery) {
      try {
        String rawJson = simpleHttpClient.get(field.getValueSourceApi().getPath(), Map.of("limit", String.valueOf(COLUMN_VALUE_DEFAULT_PAGE_SIZE)), tenantId);
        DocumentContext parsedJson = JsonPath.parse(rawJson);
        List<String> values = parsedJson.read(field.getValueSourceApi().getValueJsonPath());
        List<String> labels = parsedJson.read(field.getValueSourceApi().getLabelJsonPath());
        for (int i = 0; i < values.size(); i++) {
          String value = values.get(i);
          String label = labels.get(i);
          if (label.contains(searchText)) {
            resultSet.add(new ValueWithLabel().value(value).label(label));
          }
        }
      } catch (FeignException.Unauthorized e) {
        log.error("Failed to get column values from {} tenant due to exception: {}", tenantId, e.getMessage());
      }
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
      null,
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
    Map<String, String> queryParams = Map.of(
      "facet", "languages",
      "query", "id=*",
      "limit", "1000"
    );

    Set<String> valueSet = new HashSet<>();
    for (String tenant : tenantsToQuery) {
      try {
        String rawJson = simpleHttpClient.get("search/instances/facets", queryParams, tenant);
        DocumentContext parsedJson = JsonPath.parse(rawJson);
        List<String> values = parsedJson.read("$.facets.languages.values.*.id");
        valueSet.addAll(values);
      } catch (FeignException.Unauthorized | FeignException.BadRequest e) {
        log.error("Failed to get languages from {} tenant due to exception {}", tenant, e.getMessage());
      }
    }
    List<String> values = new ArrayList<>(valueSet);

    List<ValueWithLabel> results = new ArrayList<>();
    ObjectMapper mapper =
      JsonMapper
        .builder()
        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
        .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
        .build();

    List<Map<String, String>> languages = List.of();
    try(InputStream input = getClass().getClassLoader().getResourceAsStream(LANGUAGES_FILEPATH)) {
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

    for (String code : values) {
      String label;
      String a2Code = a3ToA2Map.get(code);
      String name = a3ToNameMap.get(code);
      if (StringUtils.isNotEmpty(a2Code)) {
        Locale languageLocale = new Locale(a2Code);
        label = languageLocale.getDisplayLanguage(folioLocale);
      } else if (StringUtils.isNotEmpty(name)) {
        label = name;
      } else if (StringUtils.isNotEmpty(code)){
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
}
