package org.folio.fqm.utils;

import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeDefaultSort;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.jooq.impl.DSL.field;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.folio.fqm.utils.IdStreamerTestDataProvider.TEST_ENTITY_TYPE_DEFINITION;

class EntityTypeUtilsTest {

  @Test
  void shouldGetIdColumnNames() {
    List<String> expectedIdColumnNames = List.of("id");
    List<String> actualIdColumnNames = EntityTypeUtils.getIdColumnNames(TEST_ENTITY_TYPE_DEFINITION);
    assertEquals(expectedIdColumnNames, actualIdColumnNames);
  }

  @Test
  void shouldReturnEmptyIdColumnListForNullColumns() {
    EntityType entityType = new EntityType();
    List<String> expectedIdColumnNames = List.of();
    List<String> actualIdColumnNames = EntityTypeUtils.getIdColumnNames(entityType);
    assertEquals(expectedIdColumnNames, actualIdColumnNames);
  }

  @Test
  void shouldGetIdColumnValueGetters() {
    List<String> expectedIdColumnValueGetters = List.of(":sourceAlias.id");
    List<String> actualIdColumnValueGetters = EntityTypeUtils.getIdColumnValueGetters(TEST_ENTITY_TYPE_DEFINITION);
    assertEquals(expectedIdColumnValueGetters, actualIdColumnValueGetters);
  }

  @Test
  void shouldGetResultIdValueGetter() {
    Field<String[]> expectedField = DSL.cast(DSL.array(DSL.field(":sourceAlias.id")), String[].class);
    Field<String[]> actualField = EntityTypeUtils.getResultIdValueGetter(TEST_ENTITY_TYPE_DEFINITION);
    assertEquals(expectedField, actualField);
  }

  @Test
  void shouldGetSortFields() {
    List<SortField<Object>> expectedSortFields = List.of(
      field("id").asc()
    );
    List<SortField<Object>> actualSortFields = EntityTypeUtils.getSortFields(TEST_ENTITY_TYPE_DEFINITION, true);
    assertEquals(expectedSortFields, actualSortFields);
  }

  @Test
  void shouldHandleDescendingSortFields() {
    EntityType entityType = new EntityType()
      .defaultSort(
        List.of(
          new EntityTypeDefaultSort()
            .columnName("id")
            .direction(EntityTypeDefaultSort.DirectionEnum.DESC)
        )
      );
    List<SortField<Object>> expectedSortFields = List.of(
      field("id").desc()
    );
    List<SortField<Object>> actualSortFields = EntityTypeUtils.getSortFields(entityType, true);
    assertEquals(expectedSortFields, actualSortFields);
  }

  @Test
  void shouldReturnEmptySortFieldsWhenSortResultsIsFalse() {
    List<SortField<Object>> expectedSortFields = List.of();
    List<SortField<Object>> actualSortFields = EntityTypeUtils.getSortFields(TEST_ENTITY_TYPE_DEFINITION, false);
    assertEquals(expectedSortFields, actualSortFields);
  }

  @Test
  void shouldReturnEmptySortFieldsForMissingDefaultSort() {
    EntityType entityType = new EntityType();
    List<SortField<Object>> expectedSortFields = List.of();
    List<SortField<Object>> actualSortFields = EntityTypeUtils.getSortFields(entityType, true);
    assertEquals(expectedSortFields, actualSortFields);
  }


}
