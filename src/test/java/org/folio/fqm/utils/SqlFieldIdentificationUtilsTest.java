package org.folio.fqm.utils;

import static org.jooq.impl.DSL.field;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.junit.jupiter.api.Test;

class SqlFieldIdentificationUtilsTest {

  @Test
  void testSqlResultsFilterWithValueGetter() {
    EntityTypeColumn entityTypeColumn = new EntityTypeColumn()
      .name("derived_column_name_01")
      .valueGetter("valueGetter");
    var actualField = SqlFieldIdentificationUtils.getSqlResultsField(entityTypeColumn);
    var expectedField = field(entityTypeColumn.getValueGetter());
    assertEquals(expectedField, actualField);
  }

  @Test
  void testSqlResultsFieldWhenValueGetterMissing() {
    EntityTypeColumn entityTypeColumn = new EntityTypeColumn()
      .name("derived_column_name_01");
    var actualField = SqlFieldIdentificationUtils.getSqlResultsField(entityTypeColumn);
    var expectedField = field(entityTypeColumn.getName());
    assertEquals(expectedField, actualField);
  }

  @Test
  void testSqlFilterFieldWhenValueGetterAndFilterValueGetterMissing() {
    EntityTypeColumn entityTypeColumn = new EntityTypeColumn()
      .name("derived_column_name_01");
    var actualField = SqlFieldIdentificationUtils.getSqlFilterField(entityTypeColumn);
    var expectedField = field(entityTypeColumn.getName());
    assertEquals(expectedField, actualField);
  }

  @Test
  void testSqlFilterFieldWithValueGetter() {
    EntityTypeColumn entityTypeColumn = new EntityTypeColumn()
      .name("derived_column_name_01")
      .valueGetter("valueGetter");
    var actualField = SqlFieldIdentificationUtils.getSqlFilterField(entityTypeColumn);
    var expectedField = field(entityTypeColumn.getValueGetter());
    assertEquals(expectedField, actualField);
  }

  @Test
  void testSqlFilterFieldWithValueGetterAndFilterValueGetter() {
    EntityTypeColumn entityTypeColumn = new EntityTypeColumn()
      .name("derived_column_name_01")
      .valueGetter("valueGetter")
      .filterValueGetter("this is a filter. We want it to be used instead of the real column name");
    var actualField = SqlFieldIdentificationUtils.getSqlFilterField(entityTypeColumn);
    var expectedField = field(entityTypeColumn.getFilterValueGetter());
    assertEquals(expectedField, actualField);
  }
}
