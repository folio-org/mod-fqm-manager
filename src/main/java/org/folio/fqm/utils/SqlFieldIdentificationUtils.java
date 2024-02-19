package org.folio.fqm.utils;

import lombok.experimental.UtilityClass;
import org.folio.querytool.domain.dto.Field;

import static org.jooq.impl.DSL.field;

/**
 * Class responsible for identifying the SQL field, corresponding to an entity type column
 */
@UtilityClass
public class SqlFieldIdentificationUtils {

  /**
   * Returns the version of a SQL field corresponding to an entity type field, intended to be displayed to users
   */
  public static org.jooq.Field<Object> getSqlResultsField(Field src) {
    if (src.getValueGetter() != null) {
      return field(src.getValueGetter());
    }
    // No value getter is defined for this column. Use column name as the SQL field name.
    return field(src.getName());
  }

  /**
   * Returns the version of a SQL field corresponding to an entity type field, intended to be used in the WHERE clause.
   *
   * Note: If both a value getter and a filter value getter is defined for this column, the filter value getter will be used.
   */
  public static org.jooq.Field<Object> getSqlFilterField(Field src) {
    if (src.getFilterValueGetter() != null) {
      return field(src.getFilterValueGetter());
    }
    if (src.getValueGetter() != null) {
      return field(src.getValueGetter());
    } else {
      // No value getter or filter is defined for this column. Use column name as the SQL field name.
      return field(src.getName());
    }
  }
}
