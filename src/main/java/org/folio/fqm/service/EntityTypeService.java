package org.folio.fqm.service;

import lombok.RequiredArgsConstructor;

import org.folio.fqm.domain.dto.EntityTypeSummary;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.ColumnValues;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.folio.fqm.repository.EntityTypeRepository.ID_FIELD_NAME;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EntityTypeService {
  private static final String COLUMN_VALUE_SEARCH_FQL = "{\"%s\": {\"$regex\": \"%s\"}}";
  private static final int COLUMN_VALUE_DEFAULT_PAGE_SIZE = 1000;
  private final EntityTypeRepository entityTypeRepository;
  private final LocalizationService localizationService;
  private final QueryProcessorService queryService;

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
      .map(localizationService::localizeEntityType);
  }

  /**
   * Return top 1000 values of an entity type column, matching the given search text
   *
   * @param entityTypeId ID of the entity type
   * @param columnName   Name of the column for which values have to be returned
   * @param searchText   Nullable search text. If a search text is provided, the returned values will include only those
   *                     that contain the specified searchText.
   */
  @Transactional(readOnly = true)
  public ColumnValues getColumnValues(UUID entityTypeId, String columnName, @Nullable String searchText) {
    String fql = String.format(COLUMN_VALUE_SEARCH_FQL, columnName, searchText == null ? "" : searchText);
    List<Map<String, Object>> results = queryService.processQuery(
      entityTypeId,
      fql,
      List.of(ID_FIELD_NAME, columnName),
      null,
      COLUMN_VALUE_DEFAULT_PAGE_SIZE);
    List<ValueWithLabel> valueWithLabels = results
      .stream()
      .map(result -> toValueWithLabel(result, columnName))
      .sorted(Comparator.comparing(ValueWithLabel::getLabel, String.CASE_INSENSITIVE_ORDER))
      .toList();
    return new ColumnValues().content(valueWithLabels);
  }

  public String getDerivedTableName(UUID entityTypeId) {
    return entityTypeRepository.getDerivedTableName(entityTypeId)
      .orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId));
  }

  private static ValueWithLabel toValueWithLabel(Map<String, Object> allValues, String columnName) {
    var valueWithLabel = new ValueWithLabel()
      .label(getColumnValue(allValues, columnName));
    return allValues.containsKey(ID_FIELD_NAME) ?
      valueWithLabel.value(getColumnValue(allValues, ID_FIELD_NAME)) :
      valueWithLabel.value(valueWithLabel.getLabel()); // value = label for entity types that do not have "id" column
  }

  private static String getColumnValue(Map<String, Object> allValues, String columnName) {
    return allValues.get(columnName).toString();
  }
}
