package org.folio.fqm.utils;

import lombok.experimental.UtilityClass;
import org.folio.querytool.domain.dto.DateType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeDefaultSort;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.impl.DSL;

import java.util.Collections;
import java.util.List;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.jooq.impl.DSL.field;

/**
 * Class responsible for retrieving information related to the ID columns of an entity type.
 */
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
    return (!isEmpty(entityType.getColumns()) ? entityType.getColumns() : Collections.<EntityTypeColumn>emptyList())
        .stream()
        .filter(column -> Boolean.TRUE.equals(column.getIsIdColumn()))
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
    var columns = entityType
      .getColumns();

      return columns
      .stream()
      .filter(column -> Boolean.TRUE.equals(column.getIsIdColumn()))
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

  public static List<String> getDateFields(EntityType entityType) {
    return entityType
      .getColumns()
      .stream()
      .filter(col -> col.getDataType() instanceof DateType)
      .map(org.folio.querytool.domain.dto.Field::getName)
      .toList();
  }
}
