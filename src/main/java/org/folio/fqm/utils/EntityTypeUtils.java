package org.folio.fqm.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.querytool.domain.dto.DateTimeType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeDefaultSort;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.Join;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.impl.DSL;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.jooq.impl.DSL.field;

/**
 * Class responsible for retrieving information related to the ID columns of an entity type.
 */
@Log4j2
@UtilityClass
public class EntityTypeUtils {

  public static final Field<String[]> RESULT_ID_FIELD = field("result_id", String[].class);

  /**
   * Returns a list of strings corresponding to the names of the id columns of an entity type.
   *
   * @param entityType Entity type to extract id column information from
   * @return List of id column names for the entity type
   */
  public static List<String> getIdColumnNames(EntityType entityType) {
    return getIdColumns(entityType)
      .stream()
      .map(EntityTypeColumn::getName)
      .toList();
  }

  /**
   * Returns a list of strings corresponding to the valueGetters for the id columns of an entity type.
   *
   * @param entityType Entity type to extract id column information from
   * @return List of value getters for the id columns of the entity type
   */
  public static List<String> getIdColumnValueGetters(EntityType entityType) {
    return getIdColumns(entityType)
      .stream()
      .map(EntityTypeColumn::getValueGetter)
      .toList();
  }

  /**
   * Returns a JOOQ field corresponding to the array of valueGetters for the id columns of an entity type.
   *
   * @param entityType Entity type to extract id column information from
   * @return JOOQ field corresponding to the valueGetters for the id columns of the entity type
   */
  public static Field<String[]> getResultIdValueGetter(EntityType entityType) {
    List<Field<Object>> idColumnValueGetters = getIdColumnValueGetters(entityType)
      .stream()
      .map(DSL::field)
      .toList();
    return DSL.cast(
      DSL.array(idColumnValueGetters.toArray(new Field[0])),
      String[].class
    );
  }

  public static List<SortField<Object>> getSortFields(EntityType entityType, boolean sortResults) {
    if (sortResults && !isEmpty(entityType.getDefaultSort())) {
      return entityType
        .getDefaultSort()
        .stream()
        .map(EntityTypeUtils::toSortField)
        .toList();
    }
    return List.of();
  }

  public static SortField<Object> toSortField(EntityTypeDefaultSort entityTypeDefaultSort) {
    Field<Object> field = field(entityTypeDefaultSort.getColumnName());
    return entityTypeDefaultSort.getDirection() == EntityTypeDefaultSort.DirectionEnum.DESC ? field.desc() : field.asc();
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
}
