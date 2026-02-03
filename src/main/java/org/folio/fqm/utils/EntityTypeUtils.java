package org.folio.fqm.utils;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.jooq.impl.DSL.field;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.fqm.domain.Query;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.querytool.domain.dto.ArrayType;
import org.folio.querytool.domain.dto.DateTimeType;
import org.folio.querytool.domain.dto.EntityDataType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeDefaultSort;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.querytool.domain.dto.Field;
import org.folio.querytool.domain.dto.Join;
import org.folio.querytool.domain.dto.NestedObjectProperty;
import org.folio.querytool.domain.dto.ObjectType;
import org.folio.querytool.domain.dto.SourceColumn;
import org.jooq.SortField;
import org.jooq.impl.DSL;

/**
 * Class responsible for retrieving information related to the ID columns of an entity type.
 */
@Log4j2
@UtilityClass
public class EntityTypeUtils {

  public static final org.jooq.Field<String[]> RESULT_ID_FIELD = field("result_id", String[].class);

  private static final String COUNTRIES_SOURCE = "countries";

  /**
   * Returns a list of strings corresponding to the names of the id columns of an entity type.
   *
   * @param entityType Entity type to extract id column information from
   * @return List of id column names for the entity type
   */
  public static List<String> getIdColumnNames(EntityType entityType) {
    return getIdColumns(entityType).stream().map(EntityTypeColumn::getName).toList();
  }

  /**
   * Returns a list of strings corresponding to the valueGetters for the id columns of an entity type.
   *
   * @param entityType Entity type to extract id column information from
   * @return List of value getters for the id columns of the entity type
   */
  public static List<String> getIdColumnValueGetters(EntityType entityType) {
    return getIdColumns(entityType).stream().map(EntityTypeColumn::getValueGetter).toList();
  }

  /**
   * Returns a JOOQ field corresponding to the array of valueGetters for the id columns of an entity type.
   *
   * @param entityType Entity type to extract id column information from
   * @return JOOQ field corresponding to the valueGetters for the id columns of the entity type
   */
  @SuppressWarnings("unchecked")
  public static org.jooq.Field<String[]> getResultIdValueGetter(EntityType entityType) {
    List<org.jooq.Field<Object>> idColumnValueGetters = getIdColumnValueGetters(entityType)
      .stream()
      .map(DSL::field)
      .toList();
    return DSL.cast(DSL.array(idColumnValueGetters.toArray(new org.jooq.Field[0])), String[].class);
  }

  public static List<SortField<Object>> getSortFields(EntityType entityType, boolean sortResults) {
    if (sortResults && !isEmpty(entityType.getDefaultSort())) {
      return entityType.getDefaultSort().stream().map(EntityTypeUtils::toSortField).toList();
    }
    return List.of();
  }

  public static SortField<Object> toSortField(EntityTypeDefaultSort entityTypeDefaultSort) {
    org.jooq.Field<Object> field = field(entityTypeDefaultSort.getColumnName());
    return entityTypeDefaultSort.getDirection() == EntityTypeDefaultSort.DirectionEnum.DESC
      ? field.desc()
      : field.asc();
  }

  public static List<String> getDateTimeFields(EntityType entityType) {
    return entityType
      .getColumns()
      .stream()
      .filter(col -> col.getDataType() instanceof DateTimeType)
      .map(org.folio.querytool.domain.dto.Field::getName)
      .toList();
  }

  /**
   * Searches for a column within an entity type by name, returning it if it exists and throwing otherwise.
   * This method will not search nested object fields, only top-level columns.
   */
  public static EntityTypeColumn findColumnByName(EntityType entityType, String columnName) {
    return entityType
      .getColumns()
      .stream()
      .filter(column -> column.getName().equals(columnName))
      .findFirst()
      .orElseThrow(() ->
        log.throwing(
          new InvalidEntityTypeDefinitionException("Column " + columnName + " could not be found", entityType)
        )
      );
  }

  /**
   * Searches for a source within an entity type by alias, returning it if it exists and throwing otherwise.
   */
  public static EntityTypeSource findSourceByAlias(EntityType entityType, String alias, String ref) {
    return entityType
      .getSources()
      .stream()
      .filter(source -> source.getAlias().equals(alias))
      .findFirst()
      .orElseThrow(() ->
        log.throwing(
          new InvalidEntityTypeDefinitionException(
            "Source " + alias + " (referenced by field " + ref + ") could not be found",
            entityType
          )
        )
      );
  }

  /**
   * Build a map of source alias to targetId for all ET sources in the given entity type.
   */
  public static Map<String, UUID> getEntityTypeSourceAliasMap(EntityType entityType) {
    if (entityType.getSources() == null) {
      return Map.of();
    }
    return entityType
      .getSources()
      .stream()
      .filter(EntityTypeSourceEntityType.class::isInstance)
      .map(EntityTypeSourceEntityType.class::cast)
      .collect(
        Collectors.toMap(
          EntityTypeSourceEntityType::getAlias,
          EntityTypeSourceEntityType::getTargetId,
          (existing, replacement) -> {
            throw new InvalidEntityTypeDefinitionException("Multiple sources cannot share the same alias", entityType);
          }
        )
      );
  }

  /**
   * Searches for a join from source to target and returns it, if it exists.
   * The join MUST be defined in source; a separate call is needed to check for joins defined target to source.
   */
  public static Optional<Join> findJoinBetween(EntityTypeColumn source, EntityTypeColumn target) {
    return source
      .getJoinsTo()
      .stream()
      .filter(j ->
        j.getTargetId().equals(target.getOriginalEntityTypeId()) &&
          j.getTargetField().equals(splitFieldIntoAliasAndField(target.getName()).getRight())
      )
      .findFirst();
  }

  /**
   * Splits a composite field name (foo.bar) into an alias and a field name.
   *
   * @example foo.bar -> Pair.of("foo", "bar")
   * @example bar -> Pair.of("", "bar")
   * @example foo.bar.baz -> Pair.of("foo.bar", "baz")
   */
  public static Pair<String, String> splitFieldIntoAliasAndField(String field) {
    int dotIndex = field.lastIndexOf('.');
    if (dotIndex == -1) {
      return Pair.of("", field);
    }
    return Pair.of(field.substring(0, dotIndex), field.substring(dotIndex + 1));
  }

  public static boolean isSimple(EntityType entityType) {
    return entityType.getSources().stream().allMatch(source -> source.getType().equals("db"));
  }

  private static List<EntityTypeColumn> getIdColumns(EntityType entityType) {
    return entityType
      .getColumns()
      .stream()
      .filter(column -> Boolean.TRUE.equals(column.getIsIdColumn()))
      // Ensure tenant_id column (if present) is the last entry in the id column list.
      // Required for compatibility with bulk-edit.
      .sorted(Comparator.comparing(column -> column.getName().contains("tenant_id") ? 1 : 0))
      .toList();
  }

  /**
   * Runs a consumer on every {@link Field field} in the entity type, resolving nested fields.
   * No guarantees are made about the order in which fields are visited, however, this will include
   * every field, no matter how deeply nested.
   *
   * @param entityType the entity to traverse
   * @param consumer   the consumer to run on each field. The first parameter is the
   *                   {@link Field field} itself and the second parameter is the path to that field
   * @example An entity type with columns `obj` and `arrobj`, where `obj` is an object with two properties
   * and `arrobj` is an array of objects with three properties, the following calls will be made:
   * <pre>
   * - consumer.accept(obj, "") // base column
   * - consumer.accept(child1, "obj->") // first property of obj
   * - consumer.accept(child2, "obj->") // second property of obj
   * - consumer.accept(arrobj, "") // base column
   * - consumer.accept(arrChild1, "arrobj[*]->") // first property
   * - consumer.accept(arrChild2, "arrobj[*]->") // second property
   * - consumer.accept(arrChild3, "arrobj[*]->") // third property
   * </pre>
   */
  public static void runOnEveryField(EntityType entityType, BiConsumer<Field, String> consumer) {
    if (entityType.getColumns() == null) {
      return;
    }
    for (EntityTypeColumn column : entityType.getColumns()) {
      runOnEveryField("", column, consumer);
    }
  }

  private static void runOnEveryField(String parentPath, Field field, BiConsumer<Field, String> consumer) {
    consumer.accept(field, parentPath);
    StringBuilder pathBuilder = new StringBuilder(parentPath).append(field.getName());
    if (field.getDataType() instanceof ObjectType objectType) {
      String path = pathBuilder.append("->").toString();
      for (Field prop : objectType.getProperties()) {
        runOnEveryField(path, prop, consumer);
      }
    } else if (field.getDataType() instanceof ArrayType arrayType) {
      // unpack any number of nested arrays until we get something else
      EntityDataType innerDataType = arrayType.getItemDataType();
      pathBuilder.append("[*]");
      while (innerDataType instanceof ArrayType innerDataTypeA) {
        innerDataType = innerDataTypeA.getItemDataType();
        pathBuilder.append("[*]");
      }
      if (innerDataType instanceof ObjectType objectType) {
        String path = pathBuilder.append("->").toString();
        for (Field prop : objectType.getProperties()) {
          runOnEveryField(path, prop, consumer);
        }
      }
    }
  }

  /**
   * Computes a simple hash for the given entity type (flattened or unflattened). This hash does
   * <strong>NOT</strong> consider the entire entity type definition, only some properties used in
   * querying. Notable exclusions include owner information, any localized fields, and the ordering
   * of sources and columns.
   * <p>
   * This DOES take into effect values which affect queries and results, including but not limited
   * to:
   * - Sources (alias, type, target)
   * - Columns (name, data type, ID state, getters)
   * - Cross-tenant status
   * <p>
   * No assumptions should be made about the specific algorithm used to compute the hash, the hash
   * length, and the result should never be used to check if two entity types are equivalent. It
   * is only intended to detect changes which may affect query results. Additionally, no guarantees
   * are made about hash stability across different versions of FQM.
   *
   * @param entityType the entity type to compute the hash for
   * @return a string of the computed hash
   */
  public static String computeEntityTypeResultsHash(EntityType entityType) {
    SortedMap<String, Object> relevantProperties = new TreeMap<>();

    relevantProperties.put("id", entityType.getId());
    relevantProperties.put("crossTenantQueriesEnabled", entityType.getCrossTenantQueriesEnabled());
    relevantProperties.put("groupByFields", entityType.getGroupByFields());
    relevantProperties.put("filterConditions", entityType.getFilterConditions());
    relevantProperties.put("additionalEcsConditions", entityType.getAdditionalEcsConditions());

    if (entityType.getSources() != null) {
      List<SortedMap<String, String>> sources = entityType
        .getSources()
        .stream()
        .map((EntityTypeSource source) -> {
          SortedMap<String, String> sourceProperties = new TreeMap<>();
          sourceProperties.put("alias", source.getAlias());
          sourceProperties.put("type", source.getType());
          switch (source) {
            case EntityTypeSourceEntityType sourceEt -> {
              sourceProperties.put("sourceField", sourceEt.getSourceField());
              sourceProperties.put("targetId", Objects.toString(sourceEt.getTargetId()));
              sourceProperties.put("targetField", sourceEt.getTargetField());
              sourceProperties.put("overrideJoinDirection", Objects.toString(sourceEt.getOverrideJoinDirection()));
              sourceProperties.put("useIdColumns", Objects.toString(sourceEt.getUseIdColumns()));
              sourceProperties.put("essentialOnly", Objects.toString(sourceEt.getEssentialOnly()));
              sourceProperties.put("inheritCustomFields", Objects.toString(sourceEt.getInheritCustomFields()));
            }
            case EntityTypeSourceDatabase sourceDb -> {
              sourceProperties.put("target", sourceDb.getTarget());
              sourceProperties.put("join", Objects.toString(sourceDb.getJoin()));
            }
            default -> {
              /* do nothing */
            }
          }
          return sourceProperties;
        })
        .sorted(Comparator.comparing(m -> m.get("alias")))
        .toList();
      relevantProperties.put("sources", sources);
    }

    List<SortedMap<String, String>> fields = new ArrayList<>();
    runOnEveryField(
      entityType,
      (Field field, String parentPath) -> {
        SortedMap<String, String> fieldProperties = new TreeMap<>();
        fieldProperties.put("name", parentPath + field.getName());
        fieldProperties.put("dataType", field.getDataType().getDataType());
        fieldProperties.put("valueGetter", field.getValueGetter());
        fieldProperties.put("filterValueGetter", field.getFilterValueGetter());
        fieldProperties.put("valueFunction", field.getValueFunction());
        fieldProperties.put("idColumnName", field.getIdColumnName());
        if (field instanceof EntityTypeColumn column) {
          fieldProperties.put("isIdColumn", Objects.toString(column.getIsIdColumn()));
          fieldProperties.put("isCustomField", Objects.toString(column.getIsCustomField()));
        }
        if (field instanceof NestedObjectProperty prop) {
          fieldProperties.put("property", prop.getProperty());
        }
        fields.add(fieldProperties);
      }
    );
    fields.sort(Comparator.comparing(m -> m.get("name")));
    relevantProperties.put("fields", fields);

    try {
      return DigestUtils.sha256Hex(new ObjectMapper().writeValueAsString(relevantProperties));
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static void verifyEntityTypeHasNotChangedDuringQueryLifetime(Query query, EntityType entityType) {
    String currentHash = computeEntityTypeResultsHash(entityType);
    if (!currentHash.equals(query.entityTypeHash())) {
      throw log.throwing(
        new InvalidEntityTypeDefinitionException(
          "Entity type definition has changed since the query was submitted; please restart the query.",
          entityType
        )
      );
    }
  }

  public static List<String> getCountryLocalizationFieldPaths(EntityType entityType) {
    List<String> paths = new ArrayList<>();
    EntityTypeUtils.runOnEveryField(entityType, (field, parentPath) -> {
      if (field.getSource() == null
        || field.getSource().getType() != SourceColumn.TypeEnum.FQM
        || !COUNTRIES_SOURCE.equals(field.getSource().getName())) {
        return;
      }

      // Use the underlying JSON property name for nested fields
      String leaf = (field instanceof NestedObjectProperty prop
        && !StringUtils.isEmpty(prop.getProperty()))
        ? prop.getProperty()
        : field.getName();

      paths.add(parentPath + leaf);
    });

    return paths;
  }
}
