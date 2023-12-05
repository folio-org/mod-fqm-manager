package org.folio.fqm.utils;

import static org.jooq.impl.DSL.field;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.folio.querytool.domain.dto.ColumnValueGetter;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.junit.jupiter.api.Test;

class SqlFieldIdentificationUtilsTest {

  @Test
  void testRmbJsonbResultsColumn() {
    ColumnValueGetter columnValueGetter = new ColumnValueGetter()
      .type(ColumnValueGetter.TypeEnum.RMB_JSONB)
      .param("name");

    EntityTypeColumn entityTypeColumn = new EntityTypeColumn()
      .name("derived_column_name_01")
      .valueGetter(columnValueGetter);
    var actualField = SqlFieldIdentificationUtils.getSqlResultsField(entityTypeColumn);
    var expectedField = field("jsonb ->> 'name'");
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
  void testRmbJsonbFilterColumn() {
    ColumnValueGetter columnValueGetter = new ColumnValueGetter()
      .type(ColumnValueGetter.TypeEnum.RMB_JSONB)
      .param("name");

    EntityTypeColumn entityTypeColumn = new EntityTypeColumn()
      .name("derived_column_name_01")
      .filterValueGetter("this is a filter which is being overridden by the value getter")
      .valueGetter(columnValueGetter);
    var actualField = SqlFieldIdentificationUtils.getSqlFilterField(entityTypeColumn);
    var expectedField = field("jsonb ->> 'name'");
    assertEquals(expectedField, actualField);
  }

  @Test
  void testSqlFilterFieldWhenValueGetterMissing() {
    EntityTypeColumn entityTypeColumn = new EntityTypeColumn()
      .name("derived_column_name_01");
    var actualField = SqlFieldIdentificationUtils.getSqlFilterField(entityTypeColumn);
    var expectedField = field(entityTypeColumn.getName());
    assertEquals(expectedField, actualField);
  }

  @Test
  void testSqlFilterField() {
    EntityTypeColumn entityTypeColumn = new EntityTypeColumn()
      .name("derived_column_name_01")
      .filterValueGetter("this is a filter. We want it to be used instead of the real column name");
    var actualField = SqlFieldIdentificationUtils.getSqlFilterField(entityTypeColumn);
    var expectedField = field(entityTypeColumn.getFilterValueGetter());
    assertEquals(expectedField, actualField);
  }
}
