package org.folio.fqm.service;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import org.folio.fql.model.field.FqlField;
import org.folio.fql.service.FqlValidationService;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.domain.dto.EntityTypeSummary;
import org.folio.fqm.exception.FieldNotFoundException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.ColumnValues;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.Field;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;
import static org.folio.fqm.repository.EntityTypeRepository.ID_FIELD_NAME;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EntityTypeService {

  private static final int COLUMN_VALUE_DEFAULT_PAGE_SIZE = 1000;
  private static final List<String> EXCLUDED_CURRENCY_CODES = List.of(
    "XUA", "AYM", "AFA", "ADP", "ATS", "AZM", "BYB", "BYR", "BEF", "BOV", "BGL", "CLF", "COU", "CUC", "CYP", "NLG", "EEK", "XBA", "XBB",
    "XBC", "XBD", "FIM", "FRF", "XFO", "XFU", "GHC", "DEM", "XAU", "GRD", "GWP", "IEP", "ITL", "LVL", "LTL", "LUF", "MGF", "MTL", "MRO", "MXV",
    "MZM", "XPD", "PHP", "XPT", "PTE", "ROL", "RUR", "CSD", "SLE", "SLL", "XAG", "SKK", "SIT", "ESP", "XDR", "XSU", "SDD", "SRG", "STD", "XTS",
    "TPE", "TRL", "TMM", "USN", "USS", "XXX", "UYI", "VEB", "VEF", "VED", "CHE", "CHW", "YUM", "ZWN", "ZMK", "ZWD", "ZWR");

  private final EntityTypeRepository entityTypeRepository;
  private final EntityTypeFlatteningService entityTypeFlatteningService;
  private final LocalizationService localizationService;
  private final QueryProcessorService queryService;
  private final SimpleHttpClient fieldValueClient;
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
          .label(localizationService.getEntityTypeLabel(entityType.getName()));
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
   * @param entityTypeId the ID to search for
   * @param includeHidden Indicates whether the hidden column should be displayed.
   *                      If set to true, the hidden column will be included in the output
   * @return the entity type definition if found, empty otherwise
   */
  public EntityType getEntityTypeDefinition(UUID entityTypeId, boolean includeHidden) {
    EntityType entityType = entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null);
    entityType.columns(entityType.getColumns().stream()
      .filter(column -> includeHidden || !Boolean.TRUE.equals(column.getHidden())) // Filter based on includeHidden flag
      .sorted(nullsLast(comparing(Field::getLabelAlias, String.CASE_INSENSITIVE_ORDER)))
      .toList());
    return entityType;
  }

  /**
   * Return top 1000 values of an entity type field, matching the given search text
   *
   * @param entityTypeId ID of the entity type
   * @param fieldName    Name of the field for which values have to be returned
   * @param searchText   Nullable search text. If a search text is provided, the returned values will include only those
   *                     that contain the specified searchText.
   */
  @Transactional(readOnly = true)
  public ColumnValues getFieldValues(UUID entityTypeId, String fieldName, @Nullable String searchText) {
    searchText = searchText == null ? "" : searchText;
    EntityType entityType = entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null);

    Field field = FqlValidationService
      .findFieldDefinition(new FqlField(fieldName), entityType)
      .orElseThrow(() -> new FieldNotFoundException(entityType.getName(), fieldName));

    if (field.getName().equals("tenant_id")) {
      // Hackish way to get tenant values for cross-tenant instance queries. Can be removed after completion of MODFQMMGR-335.
      List<String> tenants = crossTenantQueryService.getTenantsToQuery(UUID.fromString("6b08439b-4f8e-4468-8046-ea620f5cfb74"));
      List<ValueWithLabel> tenantValues = tenants
        .stream()
        .map(tenant -> new ValueWithLabel().value(tenant).label(tenant))
        .toList();
      return new ColumnValues().content(tenantValues);
    }

    if (field.getName().equals("source_tenant_id")) {
      // Hackish way to get tenant values for cross-tenant instance queries. Can be removed after completion of MODFQMMGR-335.
      List<String> tenants = crossTenantQueryService.getTenantsToQuery(UUID.fromString("6b08439b-4f8e-4468-8046-ea620f5cfb74"));
      if (tenants.size() > 1) {
        List<ValueWithLabel> tenantValues = tenants
          .stream()
          .map(tenant -> new ValueWithLabel().value(tenant).label(tenant))
          .toList();
        return new ColumnValues().content(tenantValues);
      } else {
        // tenant is not central tenant, but we need to provide central tenant id as an available value
        String centralTenantId = crossTenantQueryService.getCentralTenantId();
        List<ValueWithLabel> tenantList = new ArrayList<>();
        if (centralTenantId != null && !centralTenantId.equals(tenants.get(0))) {
          tenantList.add(new ValueWithLabel().value(centralTenantId).label(centralTenantId));
        }
        tenantList.add(new ValueWithLabel().value(tenants.get(0)).label(tenants.get(0)));
        return new ColumnValues().content(tenantList);
      }
    }

    if (field.getValues() != null) {
      return getFieldValuesFromEntityTypeDefinition(field, searchText);
    }

    if (field.getValueSourceApi() != null) {
      return getFieldValuesFromApi(field, searchText);
    }

    if (field.getName().equals("pol_currency")) {
      return getCurrencyValues();
    }

    return getFieldValuesFromEntityType(entityTypeId, fieldName, searchText);
  }

  private ColumnValues getFieldValuesFromEntityTypeDefinition(Field field, String searchText) {
    List<ValueWithLabel> filteredValues = field
      .getValues()
      .stream()
      .filter(valueWithLabel -> valueWithLabel.getLabel().contains(searchText))
      .distinct()
      .sorted(Comparator.comparing(ValueWithLabel::getLabel, String.CASE_INSENSITIVE_ORDER))
      .toList();
    return new ColumnValues().content(filteredValues);
  }

  private ColumnValues getFieldValuesFromApi(Field field, String searchText) {
    String rawJson = fieldValueClient.get(field.getValueSourceApi().getPath(), Map.of("limit", String.valueOf(COLUMN_VALUE_DEFAULT_PAGE_SIZE)));
    DocumentContext parsedJson = JsonPath.parse(rawJson);
    List<String> values = parsedJson.read(field.getValueSourceApi().getValueJsonPath());
    List<String> labels = parsedJson.read(field.getValueSourceApi().getLabelJsonPath());

    List<ValueWithLabel> results = new ArrayList<>(values.size());
    for (int i = 0; i < values.size(); i++) {
      String value = values.get(i);
      String label = labels.get(i);
      if (label.contains(searchText)) {
        results.add(new ValueWithLabel().value(value).label(label));
      }
    }
    return new ColumnValues().content(results);
  }

  private ColumnValues getFieldValuesFromEntityType(UUID entityTypeId, String fieldName, String searchText) {
    String fql = "{\"%s\": {\"$regex\": \"%s\"}}".formatted(fieldName, searchText);
    List<Map<String, Object>> results = queryService.processQuery(
      entityTypeId,
      fql,
      List.of(ID_FIELD_NAME, fieldName),
      null,
      COLUMN_VALUE_DEFAULT_PAGE_SIZE
    );
    List<ValueWithLabel> valueWithLabels = results
      .stream()
      .map(result -> toValueWithLabel(result, fieldName))
      .sorted(Comparator.comparing(ValueWithLabel::getLabel, String.CASE_INSENSITIVE_ORDER))
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
        .sorted(comparing(ValueWithLabel::getLabel))
        .toList());
    return new ColumnValues().content(currencies);
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
