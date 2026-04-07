package org.folio.fqm.utils.flattening;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabaseJoin;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.querytool.domain.dto.Join;
import org.folio.querytool.domain.dto.JoinCustom;
import org.folio.querytool.domain.dto.JoinDirection;
import org.folio.querytool.domain.dto.JoinEqualityCastUUID;
import org.folio.querytool.domain.dto.JoinEqualitySimple;
import org.hibernate.query.sqm.EntityTypeException;

@Log4j2
@UtilityClass
public class FromClauseUtils {

  /**
   * Build the FROM/JOIN clause for a given flattened entity type.
   *
   * @param flattenedEntityType The entity type to build the clause for, MUST be flattened and SHOULD have been done so
   *                            with `preserveAllColumns=true`. Not setting `preserveAllColumns=true` when flattening may
   *                            cause columns needed for joining to be filtered out, resulting in a failure here.
   * @param tenantId The tenant ID to use for the table prefix
   */
  public static String getFromClause(EntityType flattenedEntityType, String tenantId) {
    // Check that exactly 1 source is not joined via a JOIN clause or an entity type join
    List<EntityTypeSource> sourcesWithoutJoin = flattenedEntityType
      .getSources()
      .stream()
      .filter(source -> !SourceUtils.isJoined(source))
      .toList();

    if (sourcesWithoutJoin.size() != 1) {
      log.error("ERROR: number of sources without joins must be exactly 1, but we found {}", sourcesWithoutJoin);
      throw new InvalidEntityTypeDefinitionException(
        "Flattened entity type should have exactly 1 source without joins, but has " + sourcesWithoutJoin.size(),
        flattenedEntityType
      );
    }

    flattenedEntityType.setSources(
      flattenedEntityType
        .getSources()
        .stream()
        .map(EntityTypeSource::toBuilder)
        .map(EntityTypeSource.EntityTypeSourceBuilder::build)
        // it complains if this cast is not here due to superclass builder implementation
        .map(EntityTypeSource.class::cast)
        .toList()
    );

    List<EntityTypeSourceDatabase> joinedSources = resolveJoins(
      flattenedEntityType,
      findNecessaryJoins(flattenedEntityType)
    );

    String finalJoinClause = buildFromClauseFromOrderedSources(joinedSources, tenantId);
    log.info("Final from clause string: {}", finalJoinClause);
    return finalJoinClause;
  }

  private static String buildFromClauseFromOrderedSources(
    Collection<EntityTypeSourceDatabase> orderedAndResolvedSources,
    String tenantId
  ) {
    String tablePrefix = tenantId != null ? tenantId + "_mod_fqm_manager." : "";

    List<String> baseTables = new ArrayList<>();
    List<String> joins = new ArrayList<>();

    for (EntityTypeSourceDatabase source : orderedAndResolvedSources) {
      EntityTypeSourceDatabaseJoin join = source.getJoin();
      String alias = "\"" + source.getAlias() + "\"";
      String target = source.getTarget();
      if (join != null) {
        String joinClause = join.getType() + " " + tablePrefix + target + " " + alias;
        if (join.getCondition() != null) {
          joinClause += " ON " + join.getCondition();
        }
        joins.add(joinClause);
      } else {
        baseTables.add(tablePrefix + target + " " + alias);
      }
    }

    return (
      baseTables.stream().collect(Collectors.joining(", ")) + " " + joins.stream().collect(Collectors.joining(" "))
    );
  }

  /**
   * Find all {@link NecessaryJoin NecessaryJoin}s for the given flattened entity type. This will return a list
   * of records, one for each join that must be resolved between entity types.
   */
  public static List<NecessaryJoin> findNecessaryJoins(EntityType flattenedEntityType) {
    Map<String, EntityTypeSourceEntityType> entityTypeSourceMap = flattenedEntityType
      .getSources()
      .stream()
      .filter(EntityTypeSourceEntityType.class::isInstance)
      .map(EntityTypeSourceEntityType.class::cast)
      .collect(Collectors.toMap(EntityTypeSource::getAlias, Function.identity()));

    return flattenedEntityType
      .getSources()
      .stream()
      .filter(EntityTypeSourceDatabase.class::isInstance)
      .map(EntityTypeSourceDatabase.class::cast)
      .filter(s -> s.getJoin() == null)
      .filter(s -> s.getJoinedViaEntityType() != null)
      .map(source -> findNecessaryJoin(source, entityTypeSourceMap, flattenedEntityType))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toCollection(ArrayList::new));
  }

  /**
   * Find the {@link NecessaryJoin NecessaryJoin} required to join the provided database source.
   * This finds the source's parent where the `sourceField` and `targetField` are provided, finds
   * the referenced fields, their sources, and wraps it all up into a {@link NecessaryJoin} for later use.
   *
   * @param source The source to find the necessary join for
   * @param sourceMap A map of source aliases to their entity type sources
   * @param flattenedEntityType The flattened entity type being processed
   */
  private static Optional<NecessaryJoin> findNecessaryJoin(
    EntityTypeSourceDatabase source,
    Map<String, EntityTypeSourceEntityType> sourceMap,
    EntityType flattenedEntityType
  ) {
    EntityTypeSourceEntityType parentSource = SourceUtils.findJoiningEntityType(source, sourceMap);

    if (parentSource.getSourceField() == null) {
      // parent source is not joined, so we do not need to join either
      return Optional.empty();
    }

    EntityTypeColumn columnA = EntityTypeUtils.findColumnByName(flattenedEntityType, parentSource.getSourceField());
    EntityTypeColumn columnB = EntityTypeUtils.findColumnByName(
      flattenedEntityType,
      parentSource.getAlias() + "." + parentSource.getTargetField()
    );
    EntityTypeSourceDatabase sourceA = (EntityTypeSourceDatabase) EntityTypeUtils.findSourceByAlias(
      flattenedEntityType,
      columnA.getSourceAlias(),
      columnA.getName()
    );
    EntityTypeSourceDatabase sourceB = (EntityTypeSourceDatabase) EntityTypeUtils.findSourceByAlias(
      flattenedEntityType,
      columnB.getSourceAlias(),
      columnB.getName()
    );

    return Optional.of(new NecessaryJoin(columnA, sourceA, columnB, sourceB, parentSource.getOverrideJoinDirection()));
  }

  /** Represents a join between two columns (and their sources) that must be resolved and end up in the resulting query */
  protected record NecessaryJoin(
    EntityTypeColumn columnA,
    EntityTypeSourceDatabase sourceA,
    EntityTypeColumn columnB,
    EntityTypeSourceDatabase sourceB,
    JoinDirection overrideJoinDirection
  ) {
    public NecessaryJoin flip() {
      return new NecessaryJoin(columnB, sourceB, columnA, sourceA, SourceUtils.flipDirection(overrideJoinDirection));
    }

    public String toString() {
      return "%s[%s] <-> %s[%s]".formatted(
          columnA().getName(),
          sourceA().getAlias(),
          columnB().getName(),
          sourceB().getAlias()
        );
    }
  }

  /**
   * Resolves the entity type joins for the given flattened entity type. This is done via the following process:
   *
   * <ol>
   * <li>Find database sources that already have SQL joins declared; use these as the basis for our join
   *     dependency graph (see {@link #resolveDatabaseJoins})</li>
   * <li>Algorithmically resolve the graph of desired joins to solve the network into dependency lists</li>
   * <li>Add SQL conditions to the database sources</li>
   * <li>Order the sources based on the created graph (see {@link #orderSources})</li>
   * <li>Return the ordered sources ready to be concatenated into SQL</li>
   * </ol>
   *
   * @param flattenedEntityType the entity type to process
   * @param necessaryJoins      the list of joins to include
   * @return a list of ordered database sources with joins resolved
   */
  public static List<EntityTypeSourceDatabase> resolveJoins(
    EntityType flattenedEntityType,
    List<NecessaryJoin> necessaryJoins
  ) {
    // stores the dependencies for joining in the format `KEY must come _after_ VALUES`
    Map<String, Set<String>> dependencies = new HashMap<>();

    resolveDatabaseJoins(flattenedEntityType, dependencies);

    Set<String> sourcesAvailableForEntityJoins = flattenedEntityType
      .getSources()
      .stream()
      .filter(EntityTypeSourceDatabase.class::isInstance)
      .map(EntityTypeSourceDatabase.class::cast)
      .filter(sourceDb -> sourceDb.getJoin() == null)
      .map(s -> s.getAlias())
      .collect(Collectors.toSet());

    while (!necessaryJoins.isEmpty()) {
      // if one side of a join is already used, we must resolve the other side before potentially deadlocking ourselves.
      // for example, A joins to B and source A is already joined to something else, so we can't use it.
      // Therefore, we must join B ON (some condition with A)
      Optional<NecessaryJoin> joinToAlreadyJoined = necessaryJoins
        .stream()
        .filter(join ->
          !sourcesAvailableForEntityJoins.contains(join.sourceA().getAlias()) ||
          !sourcesAvailableForEntityJoins.contains(join.sourceB().getAlias())
        )
        .findFirst()
        .map(join -> {
          if (sourcesAvailableForEntityJoins.contains(join.sourceA().getAlias())) {
            return join.flip();
          } else {
            return join;
          }
        });

      // if above isn't a thing, just grab the next one
      NecessaryJoin join = joinToAlreadyJoined.orElse(necessaryJoins.getFirst());

      EntityTypeSourceDatabaseJoin computedJoin = computeJoin(
        flattenedEntityType,
        join.columnA(),
        join.columnB(),
        join.overrideJoinDirection()
      );
      join.sourceB().setJoin(computedJoin);

      // A needs to get added to the query before B
      dependencies.computeIfAbsent(join.sourceB().getAlias(), k -> new HashSet<>()).add(join.sourceA().getAlias());

      sourcesAvailableForEntityJoins.remove(join.sourceB().getAlias());
      necessaryJoins.remove(join);
      necessaryJoins.remove(join.flip()); // in case we flipped earlier, re-flip for removal
    }

    // Order DB sources so that JOIN clause makes sense
    // we don't care about ET sources for the actual from clause, so we filter for just DB ones here
    return orderSources(
      flattenedEntityType
        .getSources()
        .stream()
        .filter(EntityTypeSourceDatabase.class::isInstance)
        .map(EntityTypeSourceDatabase.class::cast)
        .toList(),
      dependencies
    );
  }

  /** Handle SQL join conditions for database sources */
  public static void resolveDatabaseJoins(EntityType flattenedEntityType, Map<String, Set<String>> dependencies) {
    flattenedEntityType
      .getSources()
      .stream()
      .filter(EntityTypeSourceDatabase.class::isInstance)
      .map(EntityTypeSourceDatabase.class::cast)
      .filter(source -> source.getJoin() != null)
      .forEach(source -> {
        source.setJoin(
          source
            .getJoin()
            .condition(
              Optional
                .ofNullable(source.getJoin().getCondition())
                .map(s -> s.replace(":this", "\"" + source.getAlias() + "\""))
                .map(s -> s.replace(":that", "\"" + source.getJoin().getJoinTo() + "\""))
                .orElse(null)
            )
        );

        dependencies.computeIfAbsent(source.getAlias(), k -> new HashSet<>()).add(source.getJoin().getJoinTo());
      });
  }

  /** Compute the SQL for a join between two columns */
  public static EntityTypeSourceDatabaseJoin computeJoin(
    EntityType flattenedEntityType,
    EntityTypeColumn sourceColumn,
    EntityTypeColumn targetColumn,
    JoinDirection overrideJoinDirection
  ) {
    Optional<Join> sourceToTargetJoin = EntityTypeUtils.findJoinBetween(sourceColumn, targetColumn);
    Optional<Join> targetToSourceJoin = EntityTypeUtils.findJoinBetween(targetColumn, sourceColumn);

    if (sourceToTargetJoin.isEmpty() && targetToSourceJoin.isEmpty()) {
      throw log.throwing(
        new InvalidEntityTypeDefinitionException(
          "No join found between %s and %s".formatted(sourceColumn.getName(), targetColumn.getName()),
          flattenedEntityType
        )
      );
    } else if (sourceToTargetJoin.isPresent() && targetToSourceJoin.isPresent()) {
      throw log.throwing(
        new InvalidEntityTypeDefinitionException(
          "Ambiguous join found between %s and %s; joins should only be on one side!".formatted(
              sourceColumn.getName(),
              targetColumn.getName()
            ),
          flattenedEntityType
        )
      );
    } else {
      return sourceToTargetJoin
        .map(join -> computeJoin(sourceColumn, targetColumn, join, overrideJoinDirection, false))
        .or(() ->
          targetToSourceJoin.map(join -> computeJoin(targetColumn, sourceColumn, join, overrideJoinDirection, true))
        )
        .orElseThrow();
    }
  }

  /** Join column A to column B. Column A MUST have the joinsTo definition referring to B; no validation is performed here */
  public static EntityTypeSourceDatabaseJoin computeJoin(
    EntityTypeColumn a,
    EntityTypeColumn b,
    Join join,
    JoinDirection directionOverride,
    boolean flipJoinDirection
  ) {
    JoinDirection direction = Optional
      .ofNullable(directionOverride)
      .or(() -> Optional.ofNullable(join.getDirection()))
      .orElse(JoinDirection.INNER);

    String directionStr = flipJoinDirection && directionOverride == null
      ? switch (direction) {
        case LEFT -> "RIGHT";
        case RIGHT -> "LEFT";
        default -> direction.toString();
      }
      : direction.toString();

    return new EntityTypeSourceDatabaseJoin()
      .condition(getJoinTemplate(join).replace(":this", a.getValueGetter()).replace(":that", b.getValueGetter()))
      .type(directionStr.toUpperCase() + " JOIN");
  }

  private static String getJoinTemplate(Join join) {
    if (join instanceof JoinCustom custom) {
      return custom.getSql();
    } else if (join instanceof JoinEqualitySimple) {
      return ":this = :that";
    } else if (join instanceof JoinEqualityCastUUID) {
      return "(:this)::uuid = (:that)::uuid";
    } else {
      throw log.throwing(new EntityTypeException("Unsupported join type", join.getClass().getSimpleName()));
    }
  }

  /**
   * Orders sources based on a graph traversal given a dependency list of format KEY after VALUE(S).
   */
  public static List<EntityTypeSourceDatabase> orderSources(
    List<EntityTypeSourceDatabase> sources,
    Map<String, Set<String>> sourceDependencies
  ) {
    Map<String, EntityTypeSourceDatabase> sourceMap = sources
      .stream()
      .collect(Collectors.toMap(EntityTypeSourceDatabase::getAlias, Function.identity()));

    List<String> result = new ArrayList<>();
    Set<String> toInsert = sources
      .stream()
      .map(EntityTypeSourceDatabase::getAlias)
      .collect(Collectors.toCollection(HashSet::new));

    while (!toInsert.isEmpty()) {
      String next = toInsert.iterator().next();
      orderSourceVisit(result, toInsert, sourceDependencies, next);
    }

    return result.stream().map(sourceMap::get).toList();
  }

  private static void orderSourceVisit(
    List<String> result,
    Set<String> toInsert,
    Map<String, Set<String>> sourceDependencies,
    String source
  ) {
    if (!toInsert.contains(source)) {
      return;
    }

    toInsert.remove(source);

    sourceDependencies
      .getOrDefault(source, Set.of())
      .forEach(dep -> orderSourceVisit(result, toInsert, sourceDependencies, dep));

    result.add(source);
  }
}
