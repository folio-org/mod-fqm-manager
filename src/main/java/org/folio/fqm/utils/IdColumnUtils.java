package org.folio.fqm.utils;

import lombok.experimental.UtilityClass;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;

import static org.jooq.impl.DSL.field;

/**
 * Class responsible for retrieving information related to the ID columns of an entity type.
 */
@UtilityClass
public class IdColumnUtils {

  public static final Field<String[]> RESULT_ID_FIELD = field("result_id", String[].class);

  /**
   * Returns a list of strings corresponding to the names of the id columns of an entity type.
   *
   * @param entityType Entity type to extract id column information from
   * @return List of id column names for the entity type
   */
  public static List<String> getIdColumnNames(EntityType entityType) {
    return entityType
      .getColumns()
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
//      .map(valueGetter -> {
//        EntityTypeSource source = entityType.getSources()
//          .stream()
//          .filter(currentSource -> valueGetter.contains(":" + currentSource.getAlias()))
//          .findFirst()
//          .orElseThrow(() -> new IllegalStateException("FAILED TO GET VALUE GETTER"));
//
//        String toReplace = ":" + source.getAlias();
//        System.out.println("Replacing string " + toReplace);
//        String alias = "\"" + source.getAlias() + "\"";
//        System.out.println("Before: " + valueGetter);
//        String afterValueGetter = valueGetter.replace(toReplace, alias);
//        System.out.println("After: " + afterValueGetter);
//        return afterValueGetter;
//      })
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
}
