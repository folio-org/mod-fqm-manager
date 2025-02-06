package org.folio.fqm.utils.flattening;

import static org.folio.fqm.utils.EntityTypeUtils.splitFieldIntoAliasAndField;

import java.util.ArrayList;
import java.util.Collection;
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
import org.folio.querytool.domain.dto.JoinEqualityCastUUID;
import org.folio.querytool.domain.dto.JoinEqualitySimple;
import org.hibernate.query.sqm.EntityTypeException;

@Log4j2
@UtilityClass
public class FromClauseUtils {

  /**
   * Build the FROM/JOIN clause for a given flattened entity type.
   *
   * @param flattenedEntityType The entity type to build the clause for, MUST be flattened
   * @param tenantId The tenant ID to use for the table prefix
   */
  public static String getFromClause(EntityType flattenedEntityType, String tenantId) {
    // Check that exactly 1 source does not have a JOIN clause
    List<EntityTypeSource> sourcesWithoutJoin = flattenedEntityType
      .getSources()
      .stream()
      .filter(source -> !isJoined(source))
      .toList();

    if (sourcesWithoutJoin.size() != 1) {
      log.error("ERROR: number of sources without joins must be exactly 1, but we found {}", sourcesWithoutJoin);
      throw new InvalidEntityTypeDefinitionException(
        "Flattened entity type should have exactly 1 source without joins, but has " + sourcesWithoutJoin.size(),
        flattenedEntityType
      );
    }

    // fill all DB sources with JOIN clauses based on their parent ETs
    List<EntityTypeSource> resolvedSources = resolveJoins(flattenedEntityType);
    // Order sources so that JOIN clause makes sense
    List<EntityTypeSource> orderedSources = orderSources(resolvedSources);

    String finalJoinClause = buildFromClauseFromOrderedSources(orderedSources, tenantId);
    log.info("Final from clause string: {}", finalJoinClause);
    return finalJoinClause;
  }

  private static String buildFromClauseFromOrderedSources(
    Collection<EntityTypeSource> orderedAndResolvedSources,
    String tenantId
  ) {
    String tablePrefix = tenantId != null ? tenantId + "_mod_fqm_manager." : "";

    StringBuilder finalJoinClause = new StringBuilder();

    for (EntityTypeSource source : orderedAndResolvedSources) {
      if (source instanceof EntityTypeSourceEntityType) {
        continue;
      }
      EntityTypeSourceDatabase sourceDb = (EntityTypeSourceDatabase) source;
      EntityTypeSourceDatabaseJoin join = sourceDb.getJoin();
      String alias = "\"" + source.getAlias() + "\"";
      String target = sourceDb.getTarget();
      if (join != null) {
        String joinClause = " " + join.getType() + " " + tablePrefix + target + " " + alias;
        if (join.getCondition() != null) {
          joinClause += " ON " + join.getCondition();
        }
        finalJoinClause.append(joinClause);
      } else {
        finalJoinClause.append(tablePrefix).append(target).append(" ").append(alias);
      }
    }

    return finalJoinClause.toString();
  }

  public static boolean isJoined(EntityTypeSource source) {
    if (source.getJoinedViaEntityType() != null) {
      return true;
    }
    return (
      (source instanceof EntityTypeSourceDatabase sourceDb && sourceDb.getJoin() != null) ||
      (source instanceof EntityTypeSourceEntityType sourceEt && sourceEt.getSourceField() != null)
    );
  }

  public static List<EntityTypeSource> resolveJoins(EntityType flattenedEntityType) {
    Map<String, EntityTypeSourceEntityType> entityTypeSourceMap = flattenedEntityType
      .getSources()
      .stream()
      .filter(EntityTypeSourceEntityType.class::isInstance)
      .map(EntityTypeSourceEntityType.class::cast)
      .collect(Collectors.toMap(EntityTypeSource::getAlias, Function.identity()));

    return flattenedEntityType
      .getSources()
      .stream()
      .map((EntityTypeSource source) -> {
        if (source instanceof EntityTypeSourceDatabase sourceDb) {
          return resolveJoin(sourceDb, entityTypeSourceMap, flattenedEntityType);
        } else {
          return source;
        }
      })
      .toList();
  }

  /**
   * Resolves the JOIN for a database source based on it's join (if applicable), or it's parent entity type's join.
   */
  private static EntityTypeSource resolveJoin(
    EntityTypeSourceDatabase source,
    Map<String, EntityTypeSourceEntityType> sourceMap,
    EntityType flattenedEntityType
  ) {
    // joined via plain SQL, not an entity type
    if (source.getJoin() != null) {
      String joinClause = source
        .getJoin()
        .getCondition()
        .replace(":this", "\"" + source.getAlias() + "\"")
        .replace(":that", "\"" + source.getJoin().getJoinTo() + "\"");

      return source.join(source.getJoin().condition(joinClause));
    }

    if (source.getJoinedViaEntityType() == null) {
      // we have no parent (simple containing only DBs)
      return source;
    }

    EntityTypeSourceEntityType parentSource = sourceMap.get(source.getJoinedViaEntityType());
    while (parentSource.getJoinedViaEntityType() != null && parentSource.getSourceField() == null) {
      parentSource = sourceMap.get(parentSource.getJoinedViaEntityType());
    }

    if (parentSource.getSourceField() == null) {
      // parent source is not joined, so we do not need to join either
      return source;
    }

    return source.join(computeJoin(flattenedEntityType, parentSource));
  }

  public static EntityTypeSourceDatabaseJoin computeJoin(
    EntityType flattenedEntityType,
    EntityTypeSourceEntityType source
  ) {
    EntityTypeColumn sourceColumn = EntityTypeUtils
      .findColumnByName(flattenedEntityType, source.getSourceField())
      .orElseThrow();
    EntityTypeColumn targetColumn = EntityTypeUtils
      .findColumnByName(flattenedEntityType, source.getTargetField())
      .orElseThrow();

    Optional<Join> sourceToTargetJoin = EntityTypeUtils.findJoinBetween(sourceColumn, targetColumn);
    Optional<Join> targetToSourceJoin = EntityTypeUtils.findJoinBetween(targetColumn, sourceColumn);

    if (sourceToTargetJoin.isEmpty() && targetToSourceJoin.isEmpty()) {
      throw log.throwing(
        new InvalidEntityTypeDefinitionException(
          "No join found between %s and %s".formatted(source.getSourceField(), source.getTargetField()),
          flattenedEntityType
        )
      );
    } else if (sourceToTargetJoin.isPresent() && targetToSourceJoin.isPresent()) {
      throw log.throwing(
        new InvalidEntityTypeDefinitionException(
          "Ambiguous join found between %s and %s; joins should only be on one side!".formatted(
              source.getSourceField(),
              source.getTargetField()
            ),
          flattenedEntityType
        )
      );
    } else {
      return sourceToTargetJoin
        .map(join -> computeJoin(sourceColumn, targetColumn, join, false))
        .or(() -> targetToSourceJoin.map(join -> computeJoin(targetColumn, sourceColumn, join, true)))
        .orElseThrow();
    }
  }

  /** Join column A to column B. Column A MUST have the joinsTo definition referring to B; no validation is performed here */
  public static EntityTypeSourceDatabaseJoin computeJoin(
    EntityTypeColumn a,
    EntityTypeColumn b,
    Join join,
    boolean flipJoinDirection
  ) {
    String direction = flipJoinDirection
      ? switch (join.getDirection()) {
        case LEFT -> "RIGHT";
        case RIGHT -> "LEFT";
        default -> join.getDirection().toString();
      }
      : join.getDirection().toString();

    return new EntityTypeSourceDatabaseJoin()
      .condition(getJoinTemplate(join).replace(":this", a.getValueGetter()).replace(":that", b.getValueGetter()))
      .type(direction.toUpperCase() + " JOIN");
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
   * Orders sources to ensure we get sensible JOIN clauses.
   * We do not care about the order of entity type sources vs database sources themselves, only the relationships
   * they define.
   *
   * This method will return a list with the following properties:
   * <ul>
   *  <li>If an entity type source has a sourceField, it's source will appear BEFORE it in the list</li>
   *  <li>If an source has a joinedViaEntityType, it's parent will appear BEFORE it in the list</li>
   *  <li>If a database source has a join, it's joinTo source will appear BEFORE it in the list</li>
   * </ul>
   */
  public static List<EntityTypeSource> orderSources(List<EntityTypeSource> sources) {
    Map<String, EntityTypeSource> sourceMap = sources
      .stream()
      .collect(Collectors.toMap(EntityTypeSource::getAlias, Function.identity()));

    List<EntityTypeSource> orderedList = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    sources.stream().forEach(source -> orderSourcesRecursively(source, sourceMap, visited, orderedList, sources));

    return orderedList;
  }

  private static void orderSourcesRecursively(
    EntityTypeSource source,
    Map<String, EntityTypeSource> sourceMap,
    Set<String> visited,
    List<EntityTypeSource> orderedList,
    List<EntityTypeSource> allSources
  ) {
    // Depth-first/post-order traversal
    if (!visited.add(source.getAlias())) {
      return;
    }
    // join our join-to source before ourselves
    if (source instanceof EntityTypeSourceEntityType sourceEt && sourceEt.getSourceField() != null) {
      orderSourcesRecursively(
        sourceMap.get(splitFieldIntoAliasAndField(sourceEt.getSourceField()).getLeft()),
        sourceMap,
        visited,
        orderedList,
        allSources
      );
    }

    // join our parent entity type(s) before ourselves
    if (source.getJoinedViaEntityType() != null) {
      EntityTypeSource joinedViaSource = sourceMap.get(source.getJoinedViaEntityType());
      orderSourcesRecursively(joinedViaSource, sourceMap, visited, orderedList, allSources);
    }

    // join our join-to source before ourselves
    // we only want true DB joins here; entity-type derived joins don't have a joinTo
    if (
      source instanceof EntityTypeSourceDatabase sourceDb &&
      sourceDb.getJoin() != null &&
      sourceDb.getJoin().getJoinTo() != null
    ) {
      EntityTypeSource joinToSource = sourceMap.get(sourceDb.getJoin().getJoinTo());
      orderSourcesRecursively(joinToSource, sourceMap, visited, orderedList, allSources);
    }
    orderedList.add(source);
  }
}
