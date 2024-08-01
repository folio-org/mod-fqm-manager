package org.folio.fqm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.ArrayType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceJoin;
import org.folio.querytool.domain.dto.Field;
import org.folio.querytool.domain.dto.NestedObjectProperty;
import org.folio.querytool.domain.dto.ObjectType;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@Log4j2
public class EntityTypeFlatteningService {
  private final EntityTypeRepository entityTypeRepository;
  private final ObjectMapper objectMapper;
  private final LocalizationService localizationService;
  private final SimpleHttpClient ecsClient;

  public EntityType getFlattenedEntityType(UUID entityTypeId) {
    return getFlattenedEntityType(entityTypeId, null);
  }

  private EntityType getFlattenedEntityType(UUID entityTypeId, EntityTypeSource sourceFromParent) {
    EntityType originalEntityType = entityTypeRepository
      .getEntityTypeDefinition(entityTypeId)
      .orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId));
    EntityType flattenedEntityType = new EntityType()
      .id(originalEntityType.getId())
      .name(originalEntityType.getName())
      ._private(originalEntityType.getPrivate())
      .defaultSort(originalEntityType.getDefaultSort())
      .idView(originalEntityType.getIdView())
      .customFieldEntityTypeId(originalEntityType.getCustomFieldEntityTypeId())
      .labelAlias(originalEntityType.getLabelAlias())
      .root(originalEntityType.getRoot())
      .groupByFields(originalEntityType.getGroupByFields())
      .sourceView(originalEntityType.getSourceView())
      .sourceViewExtractor(originalEntityType.getSourceViewExtractor());

    Map<String, String> renamedAliases = new LinkedHashMap<>(); // <oldName, newName>
    String aliasPrefix = sourceFromParent == null ? "" : sourceFromParent.getAlias() + ".";
    for (EntityTypeSource source : originalEntityType.getSources()) {
      // Update alias
      String oldAlias = source.getAlias();
      String newAlias = aliasPrefix + oldAlias;
      renamedAliases.put(oldAlias, newAlias);
    }

    Set<String> finalPermissions = new HashSet<>(originalEntityType.getRequiredPermissions());
    Stream.Builder<Stream<EntityTypeColumn>> columns = Stream.builder();

    for (EntityTypeSource source : originalEntityType.getSources()) {
      if (source.getType().equals("db")) {
        EntityTypeSource newSource = copySource(sourceFromParent, source, renamedAliases, true);
        flattenedEntityType.addSourcesItem(newSource);
      }
      else {
        UUID sourceEntityTypeId = UUID.fromString(source.getId());
        // Recursively flatten the source and add it to the flattened entity type
        EntityType flattenedSourceDefinition = getFlattenedEntityType(sourceEntityTypeId, source);
        finalPermissions.addAll(flattenedSourceDefinition.getRequiredPermissions());
        // Add a prefix to each column's name and idColumnName, then add em to the flattened entity type
        columns.add(
          flattenedSourceDefinition.getColumns()
            .stream()
            .map(col -> col
              .name(aliasPrefix + source.getAlias() + '.' + col.getName())
              .idColumnName(col.getIdColumnName() == null ? null : aliasPrefix + source.getAlias() + '.' + col.getIdColumnName())
            )
        );
        // Copy each sub-source into the flattened entity type
        copySubSources(source, flattenedSourceDefinition, renamedAliases, aliasPrefix)
          .forEach(subSource -> {
            flattenedEntityType.addSourcesItem(subSource);
            renamedAliases.put(aliasPrefix + subSource.getAlias(), subSource.getAlias());
          });
      }
    }

    Stream<EntityTypeColumn> childSourceColumns = columns.build().flatMap(Function.identity());
    Stream<EntityTypeColumn> allColumns = Stream.concat(copyColumns(sourceFromParent, originalEntityType, renamedAliases), childSourceColumns);

    flattenedEntityType.columns(getFilteredColumns(allColumns).toList());
    flattenedEntityType.requiredPermissions(new ArrayList<>(finalPermissions));
    return localizationService.localizeEntityType(flattenedEntityType);
  }

  private static Stream<EntityTypeSource> copySubSources(EntityTypeSource source, EntityType flattenedSourceDefinition, Map<String, String> renamedAliases, String aliasPrefix) {
    return flattenedSourceDefinition.getSources()
      .stream()
      .map(subSource -> {
        // For this, we don't want to rename aliases, since they have already been renamed (in the recursive call to getFlattenedEntityType())
        EntityTypeSource newSource = copySource(source, subSource, renamedAliases, false);
        // Also, we need to set up the join for sources that don't already have it (there should be exactly 1 in each source)
        if (source.getJoin() != null && subSource.getJoin() == null) {
          EntityTypeSourceJoin newJoin = new EntityTypeSourceJoin()
            .type(source.getJoin().getType())
            .condition(source.getJoin().getCondition())
            .joinTo(aliasPrefix + source.getJoin().getJoinTo()); // joinTo in subSource was done in the recursive call, but without the prefix, so we need to add it here
          newSource.join(newJoin);
        }
        return newSource;
      });
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

  /**
   * This method injects the source alias into the column's value getter and filter value getter.
   * It also recursively injects the source alias into nested object types and array types.
   *
   * @param <T>            The type of the column, which must extend the Field interface.
   * @param column         The column to inject the source alias into.
   * @param renamedAliases The map of old aliases to new aliases.
   * @return The column with the injected source alias.
   */
private static <T extends Field> T injectSourceAlias(T column, Map<String, String> renamedAliases, String sourceAlias) {
  // Reverse the aliases, since the map was created in prefix order and we want to use the most recently added aliases first
  // If we don't do this, then we might replace with "abc" before "abc.def" when handling an alias reference like ":abc.def"
  Stream<String> aliases = StreamSupport.stream(Spliterators.spliteratorUnknownSize(new LinkedList<>(renamedAliases.keySet()).descendingIterator(), Spliterator.ORDERED), false);
  Stream.concat(
      Stream.of("sourceAlias"), // Simple hack to maintain backward compatibility by shimming the source alias in
      aliases
    )
    .forEach(alias -> {
      String oldAliasReference = ':' + alias;
      String newAliasReference = '"' + renamedAliases.get(sourceAlias != null ? sourceAlias : alias) + '"';
      column.valueGetter(column.getValueGetter().replaceAll(oldAliasReference, newAliasReference));
      if (column.getFilterValueGetter() != null) {
        column.filterValueGetter(column.getFilterValueGetter().replaceAll(oldAliasReference, newAliasReference));
      }
      if (column.getValueFunction() != null) {
        column.valueFunction(column.getValueFunction().replaceAll(oldAliasReference, newAliasReference));
      }
      if (column.getDataType() instanceof ObjectType objectType) {
        injectSourceAliasForObjectType(objectType, renamedAliases, sourceAlias);
      }
      if (column.getDataType() instanceof ArrayType arrayType) {
        injectSourceAliasForArrayType(arrayType, renamedAliases, sourceAlias);
      }
    });
  return column;
}

  private static void injectSourceAliasForObjectType(ObjectType objectType, Map<String, String> renamedAliases, String sourceAlias) {
    List<NestedObjectProperty> convertedProperties = objectType.getProperties()
      .stream()
      .map(nestedField -> injectSourceAlias(nestedField, renamedAliases, sourceAlias))
      .toList();
    objectType.properties(convertedProperties);
  }

  private static void injectSourceAliasForArrayType(ArrayType arrayType, Map<String, String> renamedAliases, String sourceAlias) {
    if (arrayType.getItemDataType() instanceof ArrayType nestedArrayType) {
      injectSourceAliasForArrayType(nestedArrayType, renamedAliases, sourceAlias);
    }
    else if (arrayType.getItemDataType() instanceof ObjectType objectType) {
      injectSourceAliasForObjectType(objectType, renamedAliases, sourceAlias);
    }
  }

  private Stream<EntityTypeColumn> copyColumns(EntityTypeSource sourceFromParent, EntityType originalEntityType, Map<String, String> renamedAliases) {
    return originalEntityType.getColumns()
      .stream()
      .map(column -> {
        EntityTypeColumn newColumn = copyColumn(column, originalEntityType);
        // Only treat newColumn as idColumn if outer source specifies to do so
        newColumn.isIdColumn(newColumn.getIsIdColumn() == null ? null : Boolean.TRUE.equals(newColumn.getIsIdColumn()) && (sourceFromParent == null || Boolean.TRUE.equals(sourceFromParent.getUseIdColumns())));
        injectSourceAlias(newColumn, renamedAliases, newColumn.getSourceAlias());
        newColumn.setSourceAlias(null);
        return newColumn;
      });
  }

  private EntityTypeColumn copyColumn(EntityTypeColumn column, EntityType entityType) {
    try {
      String json = objectMapper.writeValueAsString(column);
      return objectMapper.readValue(json, EntityTypeColumn.class);
    } catch (Exception e) {
      throw new InvalidEntityTypeDefinitionException("Encountered an error while copying entity type column \"" + column.getName() + "\"", e, entityType);
    }
  }

  private static EntityTypeSource copySource(EntityTypeSource sourceFromParent, EntityTypeSource source, Map<String, String> renamedAliases, boolean renameAliases) {
    return new EntityTypeSource()
      .type(source.getType())
      .id(source.getId())
      .flattened(source.getFlattened())
      .alias(renameAliases ? renamedAliases.get(source.getAlias()) : source.getAlias())
      .target(source.getTarget())
      .join(source.getJoin() == null ? null : new EntityTypeSourceJoin()
        .type(source.getJoin().getType())
        .condition(source.getJoin().getCondition())
        .joinTo(renameAliases ? renamedAliases.get(source.getJoin().getJoinTo()) : source.getJoin().getJoinTo())
      )
      .useIdColumns(sourceFromParent == null || Boolean.TRUE.equals(source.getUseIdColumns()));
  }

  private Stream<EntityTypeColumn> getFilteredColumns(Stream<EntityTypeColumn> unfilteredColumns) {
    boolean ecsEnabled = ecsEnabled();
    return unfilteredColumns
      .filter(column -> ecsEnabled || !Boolean.TRUE.equals(column.getEcsOnly()))
      .map(column -> column.getValues() == null ? column : column.values(column.getValues().stream().distinct().toList()));
  }

  private boolean ecsEnabled() {
    try {
      String rawJson = ecsClient.get("consortia-configuration", Map.of("limit", String.valueOf(100)));
      DocumentContext parsedJson = JsonPath.parse(rawJson);
      // The value isn't needed here, this just provides an easy way to tell if ECS is enabled
      parsedJson.read("centralTenantId");
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
