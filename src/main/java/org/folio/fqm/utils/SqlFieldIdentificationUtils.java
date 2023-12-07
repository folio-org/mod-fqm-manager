package org.folio.fqm.utils;

import lombok.experimental.UtilityClass;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.jooq.Field;

import static org.jooq.impl.DSL.field;

/**
 * Class responsible for identifying the SQL field, corresponding to an entity type column
 */
@UtilityClass
public class SqlFieldIdentificationUtils {

  /**
   * Returns the version of a SQL field corresponding to an entity type column, intended to be displayed to users
   */
  public static Field<Object> getSqlResultsField(EntityTypeColumn entityTypeColumn) {
    if (entityTypeColumn.getValueGetter() != null) {
      return field(entityTypeColumn.getValueGetter());
    }
    // No value getter is defined for this column. Use column name as the SQL field name.
    return field(entityTypeColumn.getName());
  }

  /**
   * Returns the version of a SQL field corresponding to an entity type column, intended to be used in the WHERE clause.
   *
   * Note: If both a value getter and a filter value getter is defined for this column, the filter value getter will be used.
   */
  public static Field<Object> getSqlFilterField(EntityTypeColumn entityTypeColumn) {
    if (entityTypeColumn.getFilterValueGetter() != null) {
      return field(entityTypeColumn.getFilterValueGetter());
    }
    if (entityTypeColumn.getValueGetter() != null) {
      return field(entityTypeColumn.getValueGetter());
    } else {
      // No value getter or filter is defined for this column. Use column name as the SQL field name.
      return field(entityTypeColumn.getName());
    }
  }
}
