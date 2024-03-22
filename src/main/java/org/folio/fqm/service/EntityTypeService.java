package org.folio.fqm.service;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import org.folio.fql.model.field.FqlField;
import org.folio.fql.service.FqlValidationService;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.domain.dto.EntityTypeSummary;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.exception.FieldNotFoundException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.ColumnValues;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.Field;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Comparator.comparing;
import static org.folio.fqm.repository.EntityTypeRepository.ID_FIELD_NAME;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EntityTypeService {

  private static final int COLUMN_VALUE_DEFAULT_PAGE_SIZE = 1000;
  private final EntityTypeRepository entityTypeRepository;
  private final LocalizationService localizationService;
  private final QueryProcessorService queryService;
  private final SimpleHttpClient fieldValueClient;

  /**
   * Returns the list of all entity types.
   *
   * @param entityTypeIds If provided, only the entity types having the provided Ids will be included in the results
   */
  @Transactional(readOnly = true)
  public List<EntityTypeSummary> getEntityTypeSummary(Set<UUID> entityTypeIds) {
    return entityTypeRepository
      .getEntityTypeSummary(entityTypeIds)
      .stream()
      .map(rawEntityTypeSummary ->
        new EntityTypeSummary()
          .id(rawEntityTypeSummary.id())
          .label(localizationService.getEntityTypeLabel(rawEntityTypeSummary.name()))
      )
      .sorted(Comparator.comparing(EntityTypeSummary::getLabel, String.CASE_INSENSITIVE_ORDER))
      .toList();
  }

  /**
   * Returns the definition of a given entity type.
   *
   * @param entityTypeId the ID to search for
   * @return the entity type definition if found, empty otherwise
   */
  public Optional<EntityType> getEntityTypeDefinition(UUID entityTypeId) {
    return entityTypeRepository
      .getEntityTypeDefinition(entityTypeId)
      .map(entityType -> {
        sortColumnsInEntityType(entityType);
        return entityType;
      })
      .map(localizationService::localizeEntityType);
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

    EntityType entityType = entityTypeRepository
      .getEntityTypeDefinition(entityTypeId)
      .orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId));

    Field field = FqlValidationService
      .findFieldDefinition(new FqlField(fieldName), entityType)
      .orElseThrow(() -> new FieldNotFoundException(entityType.getName(), fieldName));

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
        .map(currency -> new ValueWithLabel()
          .value(currency.getCurrencyCode())
          .label(String.format("%s (%s)", currency.getDisplayName(), currency.getCurrencyCode())))
        .sorted(comparing(ValueWithLabel::getLabel))
        .toList());
    return new ColumnValues().content(currencies);
  }

  public String getDerivedTableName(UUID entityTypeId) {
    return entityTypeRepository
      .getDerivedTableName(entityTypeId)
      .orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId));
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
  private void sortColumnsInEntityType(EntityType entityType) {
    List<EntityTypeColumn> sortedColumns = entityType.getColumns().stream()
      .sorted(Comparator.comparing(EntityTypeColumn::getName))
      .collect(Collectors.toList());
    entityType.setColumns(sortedColumns);
  }
}
