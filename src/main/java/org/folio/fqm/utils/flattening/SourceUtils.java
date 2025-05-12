package org.folio.fqm.utils.flattening;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.querytool.domain.dto.ArrayType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabaseJoin;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.querytool.domain.dto.Field;
import org.folio.querytool.domain.dto.NestedObjectProperty;
import org.folio.querytool.domain.dto.ObjectType;

@Log4j2
@UtilityClass
public class SourceUtils {

  public static String injectSourceAliasIntoViewExtractor(
    String sourceViewExtractor,
    Map<String, String> renamedAliases
  ) {
    List<String> aliases = getAliasReplacementOrder(renamedAliases).toList();

    for (String alias : aliases) {
      String oldAliasReference = ':' + alias;
      String newAliasReference = '"' + renamedAliases.get(alias) + '"';
      sourceViewExtractor = sourceViewExtractor.replace(oldAliasReference, newAliasReference);
    }

    return sourceViewExtractor;
  }

  /**
   * This method injects the source alias into the column's value getter and filter value getter.
   * It also recursively injects the source alias into nested object types and array types.
   *
   * Only one of renamedAliases or sourceAlias should be provided. If both are provided, renamedAliases will be ignored.
   *
   * @param <T>            The type of the column, which must extend the Field interface.
   * @param column         The column to inject the source alias into.
   * @param renamedAliases The map of old aliases to new aliases.
   * @param sourceAlias    [deprecated] The explicitly provided sourceAlias property on the column, for legacy support
   * @param finalPass      If this is the final pass of injection (the top-most entity type). If this is false,
   *                         we will use psuedo-aliases (:[intermediate-alias]) to keep track of new names as we progress up the tree.
   * @return The column with the injected source aliases.
   */
  public static <T extends Field> T injectSourceAlias(
    T column,
    Map<String, String> renamedAliases,
    String sourceAlias,
    boolean finalPass
  ) {
    if (sourceAlias != null) {
      return injectSourceAlias(column, Map.of("sourceAlias", renamedAliases.get(sourceAlias)), finalPass);
    } else {
      return injectSourceAlias(column, renamedAliases, finalPass);
    }
  }

  private static <T extends Field> T injectSourceAlias(
    T column,
    Map<String, String> renamedAliases,
    boolean finalPass
  ) {
    getAliasReplacementOrder(renamedAliases)
      .forEach(alias -> {
        // only replaces things on the first pass
        String oldAliasReference = ':' + alias;
        // we use this to ensure we don't replace prefixes of aliases without the rest of the alias
        String intermediateAliasReference = ":[%s]".formatted(alias);
        // we only want to remove the :alias format once we're on the final pass (no more parent sources above this one)
        String newAliasReference = (finalPass ? "\"%s\"" : ":[%s]").formatted(renamedAliases.get(alias));

        column.valueGetter(
          column
            .getValueGetter()
            .replace(oldAliasReference, newAliasReference)
            .replace(intermediateAliasReference, newAliasReference)
        );

        if (column.getFilterValueGetter() != null) {
          column.filterValueGetter(
            column
              .getFilterValueGetter()
              .replace(oldAliasReference, newAliasReference)
              .replace(intermediateAliasReference, newAliasReference)
          );
        }
        if (column.getValueFunction() != null) {
          column.valueFunction(
            column
              .getValueFunction()
              .replace(oldAliasReference, newAliasReference)
              .replace(intermediateAliasReference, newAliasReference)
          );
        }
        if (column.getDataType() instanceof ObjectType objectType) {
          injectSourceAliasForObjectType(objectType, renamedAliases, finalPass);
        }
        if (column.getDataType() instanceof ArrayType arrayType) {
          injectSourceAliasForArrayType(arrayType, renamedAliases, finalPass);
        }
      });

    return column;
  }

  public static void injectSourceAliasForObjectType(
    ObjectType objectType,
    Map<String, String> renamedAliases,
    boolean finalPass
  ) {
    List<NestedObjectProperty> convertedProperties = objectType
      .getProperties()
      .stream()
      .map(nestedField -> injectSourceAlias(copyNestedProperty(nestedField), renamedAliases, finalPass))
      .toList();
    objectType.properties(convertedProperties);
  }

  public static void injectSourceAliasForArrayType(
    ArrayType arrayType,
    Map<String, String> renamedAliases,
    boolean finalPass
  ) {
    if (arrayType.getItemDataType() instanceof ArrayType nestedArrayType) {
      injectSourceAliasForArrayType(nestedArrayType, renamedAliases, finalPass);
    } else if (arrayType.getItemDataType() instanceof ObjectType objectType) {
      injectSourceAliasForObjectType(objectType, renamedAliases, finalPass);
    }
  }

  private static Stream<String> getAliasReplacementOrder(Map<String, String> renamedAliases) {
    // Sort longer aliases before others, since the map was created in prefix order and we want to use the most recently added aliases first
    // If we don't do this, then we might replace with "abc" before "abc.def" when handling an alias reference like ":abc.def".

    // We can't simply rely on reversing the `keySet` as there are no guarantees on the order we'll start with.
    return renamedAliases.keySet().stream().sorted((a, b) -> Integer.compare(b.length(), a.length()));
  }

  public static Stream<EntityTypeColumn> copyColumns(
    EntityTypeSourceEntityType sourceFromParent,
    Stream<EntityTypeColumn> columns,
    Map<String, String> renamedAliases
  ) {
    return columns
      .map(column -> {
        EntityTypeColumn newColumn = copyColumn(column);
        // Only treat newColumn as idColumn if outer source specifies to do so
        newColumn.isIdColumn(
          Optional
            .ofNullable(newColumn.getIsIdColumn())
            .map(isIdColumn ->
              Boolean.TRUE.equals(isIdColumn) &&
              (sourceFromParent == null || Boolean.TRUE.equals(sourceFromParent.getUseIdColumns()))
            )
            .orElse(null)
        );
        injectSourceAlias(newColumn, renamedAliases, newColumn.getSourceAlias(), sourceFromParent == null);
        newColumn.setSourceAlias(null);
        return newColumn;
      });
  }

  public static EntityTypeColumn copyColumn(EntityTypeColumn column) {
    return column.toBuilder().build();
  }

  private static NestedObjectProperty copyNestedProperty(NestedObjectProperty property) {
    return property.toBuilder().build();
  }

  public static EntityTypeSource copySource(
    EntityTypeSourceEntityType sourceFromParent,
    EntityTypeSource source,
    Map<String, String> renamedAliases
  ) {
    if (source instanceof EntityTypeSourceDatabase sourceDb) {
      return copySource(sourceFromParent, sourceDb, renamedAliases);
    } else if (source instanceof EntityTypeSourceEntityType sourceEt) {
      return copySource(sourceFromParent, sourceEt, renamedAliases);
    } else {
      throw log.throwing(new IllegalStateException("Unknown source type: " + source.getClass()));
    }
  }

  private static EntityTypeSourceDatabase copySource(
    EntityTypeSourceEntityType sourceFromParent,
    EntityTypeSourceDatabase source,
    Map<String, String> renamedAliases
  ) {
    return new EntityTypeSourceDatabase()
      .type(source.getType())
      .alias(renamedAliases.get(source.getAlias()))
      .target(source.getTarget())
      .joinedViaEntityType(getParentAlias(sourceFromParent, source, renamedAliases))
      .join(
        Optional
          .ofNullable(source.getJoin())
          .map(join ->
            new EntityTypeSourceDatabaseJoin()
              .type(join.getType())
              .condition(join.getCondition())
              .joinTo(renamedAliases.get(join.getJoinTo()))
          )
          .orElse(null)
      );
  }

  private static EntityTypeSourceEntityType copySource(
    EntityTypeSourceEntityType sourceFromParent,
    EntityTypeSourceEntityType source,
    Map<String, String> renamedAliases
  ) {
    return new EntityTypeSourceEntityType()
      .type(source.getType())
      .alias(renamedAliases.get(source.getAlias()))
      .overrideJoinDirection(source.getOverrideJoinDirection())
      .sourceField(
        Optional
          .ofNullable(source.getSourceField())
          .map(EntityTypeUtils::splitFieldIntoAliasAndField)
          .map(p -> renamedAliases.getOrDefault(p.getLeft(), p.getLeft()) + "." + p.getRight())
          .orElse(null)
      )
      .targetId(source.getTargetId())
      .targetField(source.getTargetField())
      .joinedViaEntityType(getParentAlias(sourceFromParent, source, renamedAliases))
      .useIdColumns(source.getUseIdColumns())
      .essentialOnly(source.getEssentialOnly());
  }

  private static String getParentAlias(
    EntityTypeSourceEntityType sourceFromParent,
    EntityTypeSource source,
    Map<String, String> renamedAliases
  ) {
    return Optional
      // use mapped current, if there is one already
      .ofNullable(source.getJoinedViaEntityType())
      .map(renamedAliases::get)
      // or, if no current joinedVia, use the parent's
      .or(() -> Optional.ofNullable(sourceFromParent).map(EntityTypeSource::getAlias))
      .orElse(null);
  }
}
