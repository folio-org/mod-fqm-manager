package org.folio.fqm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceJoin;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Log4j2
public class EntityTypeFlatteningService {
  private final String DB_TYPE = "db";
  private final EntityTypeRepository entityTypeRepository;
  private final ObjectMapper objectMapper;
  private final LocalizationService localizationService;

  // TODO: clean up
  public EntityType getFlattenedEntityType(UUID entityTypeId, boolean doFinalRenames) {
    EntityType originalEntityType = entityTypeRepository
      .getEntityTypeDefinition(entityTypeId)
      .orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId));
    // TODO: Remove this if-block after all entity types have been converted from the "fromClause" model to the "sources" model
    if (originalEntityType.getFromClause() != null && originalEntityType.getSources() == null) {
      return originalEntityType;
    }
    EntityType flattenedEntityType = new EntityType()
      .id(originalEntityType.getId())
      .name(originalEntityType.getName())
      ._private(originalEntityType.getPrivate())
      .defaultSort(originalEntityType.getDefaultSort())
      .columns(originalEntityType.getColumns()) // TODO: probably remove
      .idView(originalEntityType.getIdView())
      .customFieldEntityTypeId(originalEntityType.getCustomFieldEntityTypeId())
      .labelAlias(originalEntityType.getLabelAlias())
      .root(originalEntityType.getRoot())
      .groupByFields(originalEntityType.getGroupByFields())
      .sourceView(originalEntityType.getSourceView()) // Possibly unneeded
      .sourceViewExtractor(originalEntityType.getSourceViewExtractor()); // Possibly unneeded

    List<EntityTypeColumn> finalColumns = new ArrayList<>();
    Set<String> finalPermissions = new HashSet<>(originalEntityType.getRequiredPermissions());
    for (EntityTypeSource source : originalEntityType.getSources()) {
      if (source.getType().equals(DB_TYPE)) {
        Pair<EntityTypeSource, List<EntityTypeColumn>> updatePair = getConvertedSourceAndColumns(originalEntityType, source, null, false); // TODO: think about this, may not be able to hardcode false here
        flattenedEntityType.addSourcesItem(updatePair.getLeft());
        finalColumns.addAll(updatePair.getRight());
      } else {
        UUID sourceEntityTypeId = UUID.fromString(source.getId());
        EntityType flattenedSourceDefinition = getFlattenedEntityType(sourceEntityTypeId, false);
        finalPermissions.addAll(flattenedSourceDefinition.getRequiredPermissions());

        // If an entity type source contains multiple db sources, then we need to keep the original alias in order to
        // distinguish the different targets. Frequently, it will likely only have one db source. In this case we
        // can use the outer alias only, in order to keep field names more concise
        boolean keepOriginalAlias = countDbSources(flattenedSourceDefinition) > 1;

        for (EntityTypeSource subSource : flattenedSourceDefinition.getSources()) {
          String oldAlias = subSource.getAlias();
          Pair<EntityTypeSource, List<EntityTypeColumn>> updatePair = getConvertedSourceAndColumns(flattenedSourceDefinition, subSource, source, keepOriginalAlias);
          String newAlias = updatePair.getLeft().getAlias();
          if (!oldAlias.equals(newAlias)) {
            updateOtherSources(oldAlias, newAlias, flattenedSourceDefinition.getSources());
          }
          flattenedEntityType.addSourcesItem(updatePair.getLeft());
          finalColumns.addAll(updatePair.getRight());
        }
      }
    }

    flattenedEntityType.columns(finalColumns);
    flattenedEntityType.requiredPermissions(new ArrayList<>(finalPermissions));
    if (doFinalRenames) {
      List<EntityTypeColumn> convertedColumns = finalColumnConversion(flattenedEntityType);
      flattenedEntityType.columns(convertedColumns);
    }
    return localizationService.localizeEntityType(flattenedEntityType);
  }

  public String getJoinClause(EntityType flattenedEntityType) {
    StringBuilder finalJoinClause = new StringBuilder();
    List<EntityTypeSource> sources = flattenedEntityType.getSources();

    // Check that exactly 1 source does not have a JOIN clause
    long sourceWithoutJoinCount = sources
      .stream()
      .filter(source -> source.getJoin() == null)
      .count();
    if (sourceWithoutJoinCount != 1) {
      log.error("ERROR: number of sources without joins must be exactly 1, but was {}", sourceWithoutJoinCount);
      throw new InvalidEntityTypeDefinitionException("Flattened entity type should have 1 source without joins, but has " + sourceWithoutJoinCount, flattenedEntityType);
    }

    // Order sources so that JOIN clause makes sense
    List<EntityTypeSource> orderedSources = getOrderedSources(flattenedEntityType);

    for (EntityTypeSource source : orderedSources) {
      EntityTypeSourceJoin join = source.getJoin();
      String alias = "\"" + source.getAlias() + "\"";
      String target = source.getTarget();
      if (join != null) {
        String joinClause = " " + join.getType() + " " + target + " " + alias; // NEW
        if (join.getCondition() != null) {
          joinClause += " ON " + join.getCondition();
        }
        joinClause = joinClause.replace(":this", alias);
        joinClause = joinClause.replace(":that", "\"" + join.getJoinTo() + "\"");
        log.info("Join clause: " + joinClause);
        finalJoinClause.append(joinClause);
      } else {
        finalJoinClause.append(target).append(" ").append(alias); // NEW
      }
    }

    String finalJoinClauseString = finalJoinClause.toString();
    // Replace each target in the join clause with an appropriate alias
    for (EntityTypeSource source : sources) {
      String toReplace = ":" + source.getAlias();
      String alias = "\"" + source.getAlias() + "\"";
      finalJoinClauseString = finalJoinClauseString.replace(toReplace, alias); // NEW
    }
    log.info("Final join clause string: " + finalJoinClauseString);
    return finalJoinClauseString;
  }

  private List<EntityTypeSource> getOrderedSources(EntityType entityType) {
    Map<String, EntityTypeSource> sourceMap = new HashMap<>();
    for (EntityTypeSource source : entityType.getSources()) {
      sourceMap.put(source.getAlias(), source);
    }

    List<EntityTypeSource> orderedList = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    for (EntityTypeSource source : entityType.getSources()) {
      getSourcesRecursively(source, sourceMap, visited, orderedList);
    }

    return orderedList;
  }

  private static void getSourcesRecursively(EntityTypeSource source, Map<String, EntityTypeSource> sourceMap, Set<String> visited, List<EntityTypeSource> orderedList) {
    // Depth-first/post-order traversal
    if (!visited.add(source.getAlias())) {
      return;
    }
    if (source.getJoin() != null) {
      EntityTypeSource joinToSource = sourceMap.get(source.getJoin().getJoinTo());
      if (!visited.contains(joinToSource.getAlias())) {
        getSourcesRecursively(joinToSource, sourceMap, visited, orderedList);
      }
    }
    orderedList.add(source);
  }

  private void updateOtherSources(String oldSourceName, String newSourceName, List<EntityTypeSource> otherSources) {
    String oldAlias = oldSourceName + ".";
    for (EntityTypeSource source : otherSources) {
      if (source.getJoin() != null && oldSourceName.equals(source.getJoin().getJoinTo())) {
        source.getJoin().joinTo(newSourceName);
      }
      if (source.getTarget().contains(oldAlias)) {
        source.target(source.getTarget().replace(oldAlias, "\"" + newSourceName + "\"."));
      }

    }
  }

  private Pair<EntityTypeSource, List<EntityTypeColumn>> getConvertedSourceAndColumns(EntityType originalEntityType, EntityTypeSource nestedSource, EntityTypeSource outerSource, boolean keepOriginalAlias) {
    List<EntityTypeColumn> updatedColumns = new ArrayList<>();
    // Make a copy instead of returning original object
    EntityTypeSource newSource = new EntityTypeSource()
      .type(nestedSource.getType())
      .id(nestedSource.getId())
      .flattened(nestedSource.getFlattened())
      .alias(nestedSource.getAlias())
      .target(nestedSource.getTarget())
      .join(nestedSource.getJoin())
      .useIdColumns(outerSource == null || Boolean.TRUE.equals(outerSource.getUseIdColumns()));
    String nestedAlias = newSource.getAlias();

    StringBuilder newAlias = outerSource != null ? new StringBuilder(outerSource.getAlias()) : new StringBuilder();
    if (keepOriginalAlias) {
      newAlias.append(".").append(nestedAlias);
    }

    for (EntityTypeColumn oldColumn : originalEntityType.getColumns()) {
      EntityTypeColumn column = copyColumn(oldColumn, originalEntityType);
      if (column.getSourceAlias().equals(nestedAlias)) {
        if (outerSource != null) { // temporary, need a better way to do this
          column.name(outerSource.getAlias() + "." + column.getName());
          // Only treat column as idColumn if outer source specifies to do so
          column.isIdColumn(Boolean.TRUE.equals(outerSource.getUseIdColumns()) && Boolean.TRUE.equals(column.getIsIdColumn()));
          if (!Boolean.TRUE.equals(newSource.getFlattened())) {
            column.sourceAlias(newAlias.toString());
          }
        }
        updatedColumns.add(column);
      }
    }

    if (outerSource != null && !Boolean.TRUE.equals(newSource.getFlattened())) {
      newSource.alias(newAlias.toString());
      if (nestedSource.getJoin() == null && outerSource.getJoin() != null) {
        newSource.join(outerSource.getJoin());
        newSource.flattened(true);
      }
    }
    return Pair.of(newSource, updatedColumns);
  }

  private List<EntityTypeColumn> finalColumnConversion(EntityType flattenedEntityType) {
    List<EntityTypeColumn> finalColumns = new ArrayList<>();
    String toReplace = ":sourceAlias";
    for (EntityTypeColumn column : flattenedEntityType.getColumns()) {
      String sourceAlias = "\"" + column.getSourceAlias() + "\"";
      String valueGetter = column.getValueGetter();
      String filterValueGetter = column.getFilterValueGetter();
      valueGetter = valueGetter.replace(toReplace, sourceAlias);
      if (filterValueGetter != null) {
        filterValueGetter = filterValueGetter.replace(toReplace, sourceAlias);
      }
      column.valueGetter(valueGetter);
      column.filterValueGetter(filterValueGetter);
      finalColumns.add(column);
    }
    return finalColumns;
  }

  private EntityTypeColumn copyColumn(EntityTypeColumn column, EntityType entityType) {
    try {
      String json = objectMapper.writeValueAsString(column);
      return objectMapper.readValue(json, EntityTypeColumn.class);
    } catch (Exception e) {
      throw new InvalidEntityTypeDefinitionException("Encountered an error while copying entity type column \"" + column.getName() + "\"", e, entityType);
    }
  }

  private long countDbSources(EntityType entityType) {
    return entityType
      .getSources()
      .stream()
      .filter(source -> !Boolean.TRUE.equals(source.getFlattened()) && DB_TYPE.equals(source.getType()))
      .count();
  }
}
