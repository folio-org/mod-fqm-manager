package org.folio.fqm.utils;

import org.jooq.Field;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.folio.fqm.utils.IdStreamerTestDataProvider.TEST_ENTITY_TYPE_DEFINITION;

class IdColumnUtilsTest {

  @Test
  void shouldGetIdColumnNames() {
    List<String> expectedIdColumnNames = List.of("id");
    List<String> actualIdColumnNames = IdColumnUtils.getIdColumnNames(TEST_ENTITY_TYPE_DEFINITION);
    assertEquals(expectedIdColumnNames, actualIdColumnNames);
  }

  @Test
  void shouldGetIdColumnValueGetters() {
    List<String> expectedIdColumnValueGetters = List.of(":sourceAlias.id");
    List<String> actualIdColumnValueGetters = IdColumnUtils.getIdColumnValueGetters(TEST_ENTITY_TYPE_DEFINITION);
    assertEquals(expectedIdColumnValueGetters, actualIdColumnValueGetters);
  }

  @Test
  void shouldGetResultIdValueGetter() {
    Field<String[]> expectedField = DSL.cast(DSL.array(DSL.field(":sourceAlias.id")), String[].class);
    Field<String[]> actualField = IdColumnUtils.getResultIdValueGetter(TEST_ENTITY_TYPE_DEFINITION);
    assertEquals(expectedField, actualField);
  }
}
