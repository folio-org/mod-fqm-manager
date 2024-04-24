package org.folio.fqm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kotlin.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.exception.EntityTypeNotFoundException;
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
  private final EntityTypeRepository entityTypeRepository;
  private final ObjectMapper objectMapper;

  // TODO: clean up
  public Optional<EntityType> getFlattenedEntityType(UUID entityTypeId, boolean doFinalRenames) {
    EntityType originalEntityType = entityTypeRepository
      .getEntityTypeDefinition(entityTypeId)
      .orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId));
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
      .sourceView(originalEntityType.getSourceView()) // Possibly unneeded
      .sourceViewExtractor(originalEntityType.getSourceViewExtractor()); // Possibly unneeded

    List<EntityTypeColumn> finalColumns = new ArrayList<>();
    for (EntityTypeSource source : originalEntityType.getSources()) {
      if (source.getType().equals("db")) {
        Pair<EntityTypeSource, List<EntityTypeColumn>> updatePair = handleSourceAndUpdateEntityType(originalEntityType, source, null, false); // TODO: think about this, may not be able to hardcode false here
        flattenedEntityType.addSourcesItem(updatePair.component1());
        finalColumns.addAll(updatePair.component2());
      } else {
        UUID sourceEntityTypeId = UUID.fromString(source.getId());
        EntityType flattenedSourceDefinition = getFlattenedEntityType(sourceEntityTypeId, false)
          .orElseThrow(() -> new EntityTypeNotFoundException(sourceEntityTypeId));

        // If an entity type source contains multiple db sources, then we need to keep the original alias in order to
        // distinguish the different targets. Frequently, it will likely only have one db source. In this case we
        // can use the outer alias only, in order to keep field names more concise
        boolean keepOriginalAlias = countDbSources(flattenedSourceDefinition) > 1;

        for (EntityTypeSource subSource : flattenedSourceDefinition.getSources()) {
          Pair<EntityTypeSource, List<EntityTypeColumn>> updatePair = handleSourceAndUpdateEntityType(flattenedSourceDefinition, subSource, source, keepOriginalAlias);
          flattenedEntityType.addSourcesItem(updatePair.component1());
          finalColumns.addAll(updatePair.component2());
        }
      }
    }

    flattenedEntityType.columns(finalColumns);
    if (doFinalRenames) {
      List<EntityTypeColumn> convertedColumns = finalColumnConversion(flattenedEntityType);
      flattenedEntityType.columns(convertedColumns);
    }
    return Optional.of(flattenedEntityType);
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
      log.error("ERROR: number of joins without sources must be exactly 1, but was {}", sourceWithoutJoinCount);
      return ""; // TODO: handle this better
    }

    // Order sources so that JOIN clause makes sense
    List<EntityTypeSource> orderedSources = getOrderedSources(flattenedEntityType);

    for (EntityTypeSource source : orderedSources) {
      EntityTypeSourceJoin join = source.getJoin();
      String alias = "\"" + source.getAlias() + "\"";
      String target = source.getTarget();
      if (join != null) {
        String joinClause = " " + join.getType() + " " + target + " " + alias + " ON " + join.getCondition(); // NEW
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
      if (!visited.contains(source.getAlias())) {
        dfs(source, sourceMap, visited, orderedList);
      }
    }
    return orderedList;
  }

  private static void dfs(EntityTypeSource source, Map<String, EntityTypeSource> sourceMap, Set<String> visited, List<EntityTypeSource> orderedList) {
    visited.add(source.getAlias());
    if (source.getJoin() != null) {
      EntityTypeSource joinToSource = sourceMap.get(source.getJoin().getJoinTo());
      if (!visited.contains(joinToSource.getAlias())) {
        dfs(joinToSource, sourceMap, visited, orderedList);
      }
    }
    orderedList.add(source);
  }

  private Pair<EntityTypeSource, List<EntityTypeColumn>> handleSourceAndUpdateEntityType(EntityType originalEntityType, EntityTypeSource nestedSource, EntityTypeSource outerSource, boolean keepOriginalAlias) {
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
    if (newSource.getType().equals("db")) {
      StringBuilder newAlias = outerSource != null ? new StringBuilder(outerSource.getAlias()) : new StringBuilder();
      if (keepOriginalAlias) {
        newAlias.append("_").append(nestedAlias);
      }
      log.info("Updating source/columns for db source for original entity type " + originalEntityType.getName());
      for (EntityTypeColumn oldColumn : originalEntityType.getColumns()) {
        EntityTypeColumn column = copyColumn(oldColumn);
        if (column.getSourceAlias().equals(nestedAlias)) {
          if (outerSource != null) { // temporary, need a better way to do this
            // Only treat column as idColumn if outer source specifies to do so
            column.isIdColumn(Boolean.TRUE.equals(outerSource.getUseIdColumns()) && Boolean.TRUE.equals(column.getIsIdColumn()));
            if (!Boolean.TRUE.equals(newSource.getFlattened())) {
              column.sourceAlias(newAlias.toString());
            }
          }
          updatedColumns.add(column);
        }
      }

      if (newAlias.toString().equals("complex_entity_type_source3")){
        System.out.println("HERE");
      }
      if (outerSource != null) { // TODO: may not need "nestedSource.getJoin() == null"
//      if (outerSource != null) {
        if (!Boolean.TRUE.equals(newSource.getFlattened())) {
          newSource.alias(newAlias.toString());
          newSource.flattened(true);
          if (nestedSource.getJoin() == null) {
            newSource.join(outerSource.getJoin());
          } else {
            // maybe we can handle here? probably not
            newSource.getJoin().joinTo("AJf");
          }
        }
      }
    } else {
      log.error("SHOULD NOT BE HERE");
    }
    return new Pair<>(newSource, updatedColumns);
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
      column.name(column.getSourceAlias() + "_" + column.getName());
      finalColumns.add(column);
    }
    return finalColumns;
  }

  private EntityTypeColumn copyColumn(EntityTypeColumn column) {
    try {
      String json = objectMapper.writeValueAsString(column);
      return objectMapper.readValue(json, EntityTypeColumn.class);
    } catch (Exception e) {
      return new EntityTypeColumn(); // TODO: do something better here
    }
  }

  private long countDbSources(EntityType entityType) {
    return entityType
      .getSources()
      .stream()
      .filter(source -> !Boolean.TRUE.equals(source.getFlattened()) && source.getType().equals("db"))
      .count();
  }
}
