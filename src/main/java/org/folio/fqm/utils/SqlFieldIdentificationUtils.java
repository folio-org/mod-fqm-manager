package org.folio.fqm.utils;

import lombok.experimental.UtilityClass;
import org.folio.querytool.domain.dto.ColumnValueGetter.TypeEnum;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.jooq.Field;

import java.util.Map;
import java.util.function.UnaryOperator;

import static org.jooq.impl.DSL.field;

/**
 * Class responsible for identifying the SQL field, corresponding to an entity type column
 */
@UtilityClass
public class SqlFieldIdentificationUtils {
  private static final Map<TypeEnum, UnaryOperator<String>> MAPPERS = Map.of(
    TypeEnum.RMB_JSONB, param -> "jsonb ->> '" + param + "'"
  );

  /**
   * Returns the version of a SQL field corresponding to an entity type column, intended to be displayed to users
   */
  public static Field<Object> getSqlResultsField(EntityTypeColumn entityTypeColumn) {
    if (entityTypeColumn.getValueGetter() != null) {
      return getMappedField(entityTypeColumn);
    }
    // No value getter is defined for this column. Use column name as the SQL field name.
    return field(entityTypeColumn.getName());
  }

  /**
   * Returns the version of a SQL field corresponding to an entity type column, intended to be used in the WHERE clause.
   *
   * Note: If a both a value getter and a filter value getter is defined for this column, the value getter will be used.
   */
  public static Field<Object> getSqlFilterField(EntityTypeColumn entityTypeColumn) {
    if (entityTypeColumn.getValueGetter() != null) {
      return getMappedField(entityTypeColumn);
    }
    if (entityTypeColumn.getFilterValueGetter() != null) {
      return field(entityTypeColumn.getFilterValueGetter());
    } else {
      // No value getter or filter is defined for this column. Use column name as the SQL field name.
      return field(entityTypeColumn.getName());
    }
  }

  private static Field<Object> getMappedField(EntityTypeColumn entityTypeColumn) {
    var valueGetter = entityTypeColumn.getValueGetter();
    UnaryOperator<String> mapperFunction = MAPPERS.get(valueGetter.getType());
    String sqlFieldName = mapperFunction.apply(valueGetter.getParam());
    return field(sqlFieldName);
  }
}
