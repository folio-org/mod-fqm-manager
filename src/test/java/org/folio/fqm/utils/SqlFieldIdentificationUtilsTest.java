package org.folio.fqm.utils;

import static org.jooq.impl.DSL.field;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.folio.querytool.domain.dto.ColumnValueGetter;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.junit.jupiter.api.Test;

class SqlFieldIdentificationUtilsTest {

  @Test
  void testRmbJsonbColumn() {
    ColumnValueGetter columnValueGetter = new ColumnValueGetter()
      .type(ColumnValueGetter.TypeEnum.RMB_JSONB)
      .param("name");

    EntityTypeColumn entityTypeColumn = new EntityTypeColumn()
      .name("derived_column_name_01")
      .valueGetter(columnValueGetter);
    var actualField = SqlFieldIdentificationUtils.getSqlField(entityTypeColumn);
    var expectedField = field("jsonb ->> 'name'");
    assertEquals(expectedField, actualField);
  }

  @Test
  void testSqlFieldWhenValueGetterMissing() {
    EntityTypeColumn entityTypeColumn = new EntityTypeColumn()
      .name("derived_column_name_01");
    var actualField = SqlFieldIdentificationUtils.getSqlField(entityTypeColumn);
    var expectedField = field(entityTypeColumn.getName());
    assertEquals(expectedField, actualField);
  }
}
