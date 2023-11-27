package org.folio.fqm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.model.Fql;
import org.folio.fql.model.FqlCondition;
import org.folio.fql.model.FieldCondition;
import org.folio.fql.model.LogicalCondition;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeDefaultSort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;

/**
 * Class responsible for identifying the most optimal derived table for running an FQL query
 */
@Log4j2
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class DerivedTableIdentificationService {

  private final EntityTypeRepository entityTypeRepository;

  /**
   * Returns the most optimal derived table for running the given FQL query.
   */
  public String getDerivedTable(EntityType mainEntity, Fql fql, boolean shouldSortResults) {
    log.debug("Identifying derived table for running query {}. Entity Type: {}. Sort results? {}",
      fql, mainEntity.getId(), shouldSortResults);
    List<UUID> subEntityTypeIds = isEmpty(mainEntity.getSubEntityTypeIds()) ? List.of() : mainEntity.getSubEntityTypeIds();
    Set<String> requiredColumns = getColumnsForQuerying(mainEntity, fql, shouldSortResults);

    log.debug("Required columns for executing query: {}.", requiredColumns);

    String candidateEntityTypeId = subEntityTypeIds.stream()
      .map(entityTypeRepository::getEntityTypeDefinition)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .filter(subEntityType -> this.hasAllRequiredColumns(subEntityType, requiredColumns))
      .findFirst()
      .orElse(mainEntity)
      .getId();
    String derivedTableName = entityTypeRepository.getDerivedTableName(UUID.fromString(candidateEntityTypeId))
      .orElseThrow(() -> new EntityTypeNotFoundException(UUID.fromString(mainEntity.getId())));
    log.info("Identified the derived table for running query {}. Requested entity type: {}. Sort? {} " +
        "Candidate entity Type: {}. Derived table: {}", fql, mainEntity.getId(), shouldSortResults, candidateEntityTypeId,
      derivedTableName);
    return derivedTableName;
  }

  private boolean hasAllRequiredColumns(EntityType entityType, Set<String> requiredColumns) {
    Set<String> columnsInEntityType = entityType.getColumns().stream()
      .map(EntityTypeColumn::getName)
      .collect(toSet());
    return columnsInEntityType.containsAll(requiredColumns);
  }

  private Set<String> getColumnsForQuerying(EntityType entityType, Fql fql, boolean shouldSortResults) {
    Set<String> columnsForQuerying = new HashSet<>(getColumnsFromFql(fql.fqlCondition()));
    if (shouldSortResults) {
      columnsForQuerying.addAll(getColumnsFromSortDefinition(entityType));
    }
    return columnsForQuerying;
  }

  private Set<String> getColumnsFromFql(FqlCondition<?> fqlCondition) {
    if (fqlCondition instanceof FieldCondition<?> fieldCondition) {
      return Set.of(fieldCondition.fieldName());
    }
    if (fqlCondition instanceof LogicalCondition logicalCondition) {
      return logicalCondition.value()
        .stream()
        .map(this::getColumnsFromFql) // recursively find the columns in this logical condition.
        .flatMap(Collection::stream)
        .collect(toSet());
    }
    // should not be here
    log.error("Unexpected fqlCondition {} in request", fqlCondition);
    return Set.of();
  }

  private Set<String> getColumnsFromSortDefinition(EntityType entityType) {
    List<EntityTypeDefaultSort> sorts = isEmpty(entityType.getDefaultSort()) ? List.of() : entityType.getDefaultSort();
    return sorts.stream().map(EntityTypeDefaultSort::getColumnName).collect(toSet());
  }
}
