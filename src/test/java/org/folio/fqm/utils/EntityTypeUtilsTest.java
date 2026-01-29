package org.folio.fqm.utils;

import static org.folio.fqm.repository.ResultSetRepositoryTestDataProvider.TEST_ENTITY_TYPE_DEFINITION;
import static org.folio.fqm.utils.EntityTypeUtils.verifyEntityTypeHasNotChangedDuringQueryLifetime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.jooq.impl.DSL.field;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.fqm.domain.Query;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.querytool.domain.dto.ArrayType;
import org.folio.querytool.domain.dto.DateTimeType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeDefaultSort;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.querytool.domain.dto.IntegerType;
import org.folio.querytool.domain.dto.NestedObjectProperty;
import org.folio.querytool.domain.dto.ObjectType;
import org.folio.querytool.domain.dto.StringType;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

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
    List<SortField<Object>> expectedSortFields = List.of(field("id").asc());
    List<SortField<Object>> actualSortFields = EntityTypeUtils.getSortFields(TEST_ENTITY_TYPE_DEFINITION, true);
    assertEquals(expectedSortFields, actualSortFields);
  }

  @Test
  void shouldHandleDescendingSortFields() {
    EntityType entityType = new EntityType()
      .defaultSort(
        List.of(new EntityTypeDefaultSort().columnName("id").direction(EntityTypeDefaultSort.DirectionEnum.DESC))
      );
    List<SortField<Object>> expectedSortFields = List.of(field("id").desc());
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

  @Test
  void shouldGetDateTimeFields() {
    EntityType entityType = new EntityType()
      .columns(List.of(
        new EntityTypeColumn().name("dateTimeField").dataType(new DateTimeType()),
        new EntityTypeColumn().name("notDateField").dataType(new StringType())
      ));
    List<String> expectedDateFields = List.of("dateTimeField");
    List<String> actualDateFields = EntityTypeUtils.getDateTimeFields(entityType);
    assertEquals(expectedDateFields, actualDateFields);
  }

  @Test
  void shouldOrderResultIdColumns() {
    EntityType entityType = new EntityType()
      .columns(List.of(
        new EntityTypeColumn().name("tenant_id").isIdColumn(true),
        new EntityTypeColumn().name("field2").isIdColumn(true)
      ));
    List<String> expectedIdColumnNames = List.of("field2", "tenant_id");
    List<String> actualIdColumnNames = EntityTypeUtils.getIdColumnNames(entityType);
    assertEquals(expectedIdColumnNames, actualIdColumnNames);
  }

  @Test
  void testFindColumnByName() {
    EntityType entityType = new EntityType().columns(List.of(new EntityTypeColumn().name("field1")));

    assertEquals(entityType.getColumns().get(0), EntityTypeUtils.findColumnByName(entityType, "field1"));
  }

  @Test
  void testFindColumnByNameMissing() {
    EntityType entityType = new EntityType().columns(List.of(new EntityTypeColumn().name("field1")));

    assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> EntityTypeUtils.findColumnByName(entityType, "missing")
    );
  }

  @Test
  void testFindSourceByAlias() {
    EntityType entityType = new EntityType().sources(List.of(new EntityTypeSourceEntityType().alias("source1")));

    assertEquals(entityType.getSources().get(0), EntityTypeUtils.findSourceByAlias(entityType, "source1", "ref"));
  }

  @Test
  void testFindSourceByAliasMissing() {
    EntityType entityType = new EntityType().sources(List.of(new EntityTypeSourceEntityType().alias("source1")));

    assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> EntityTypeUtils.findSourceByAlias(entityType, "missing", "ref")
    );
  }

  @Test
  void testGetEntityTypeSourceAliasMap() {
    EntityType entityType = new EntityType()
      .sources(
        List.of(
          new EntityTypeSourceEntityType()
            .alias("source1")
            .targetId(UUID.fromString("24281f3e-d064-5c6f-b1b5-e26e269cc8fb")),
          new EntityTypeSourceDatabase().alias("source2")
        )
      );

    assertThat(
      EntityTypeUtils.getEntityTypeSourceAliasMap(entityType),
      is(Map.of("source1", UUID.fromString("24281f3e-d064-5c6f-b1b5-e26e269cc8fb")))
    );
  }

  @Test
  void testIsEntityTypeSimple() {
    EntityType simpleEntityType = new EntityType()
      .sources(List.of(
        new EntityTypeSourceDatabase().alias("db-source").type("db")
      ));

    EntityType compositeEntityType = new EntityType()
      .sources(List.of(
        new EntityTypeSourceDatabase().alias("db-source").type("db"),
        new EntityTypeSourceEntityType().alias("et-source").type("entityType")
      ));

    assertTrue(EntityTypeUtils.isSimple(simpleEntityType));
    assertFalse(EntityTypeUtils.isSimple(compositeEntityType));
  }

  @Test
  void testHashingYieldsDifferentResultsForSourceContents() {
    List<List<EntityTypeSource>> cases = Arrays.asList(
      null,
      List.of(),
      List.of(new EntityTypeSource("type", "alias") {}),
      List.of(new EntityTypeSourceDatabase().alias("alias").type("type").target("table")),
      List.of(
        new EntityTypeSourceEntityType()
          .alias("alias")
          .type("type")
          .targetId(UUID.fromString("2812614d-2c09-56b1-8daf-3d6351d2cb7b"))
      )
    );

    assertEquals(
      cases.size(),
      cases
        .stream()
        .map(s -> new EntityType().sources(s))
        .map(EntityTypeUtils::computeEntityTypeResultsHash)
        .distinct()
        .count()
    );
  }

  @Test
  void testHashingYieldsDifferentResultsForNestedFields() {
    List<List<EntityTypeColumn>> cases = List.of(
      List.of(),
      List.of(new EntityTypeColumn().name("outer").dataType(new ArrayType().itemDataType(new StringType()))),
      List.of(
        new EntityTypeColumn()
          .name("outer")
          .dataType(
            new ObjectType().properties(List.of(new NestedObjectProperty().name("inner").dataType(new IntegerType())))
          )
      ),
      List.of(
        new EntityTypeColumn()
          .name("outer")
          .dataType(
            new ArrayType()
              .itemDataType(
                new ArrayType()
                  .itemDataType(
                    new ObjectType()
                      .properties(List.of(new NestedObjectProperty().name("inner").dataType(new IntegerType())))
                  )
              )
          )
      )
    );

    assertEquals(
      cases.size(),
      cases
        .stream()
        .map(s -> new EntityType().columns(s))
        .map(EntityTypeUtils::computeEntityTypeResultsHash)
        .distinct()
        .count()
    );
  }

  @Test
  void testVerifyWithSameEntity() {
    EntityType entity = new EntityType().columns(null);
    String hash = EntityTypeUtils.computeEntityTypeResultsHash(entity);
    Query query = Query.newQuery(null, hash, null, null, null);

    assertDoesNotThrow(() -> verifyEntityTypeHasNotChangedDuringQueryLifetime(query, entity));
  }

  @Test
  void testVerifyWithDifferentEntity() {
    EntityType entity1 = new EntityType().crossTenantQueriesEnabled(false);
    EntityType entity2 = new EntityType().crossTenantQueriesEnabled(true);
    String hash = EntityTypeUtils.computeEntityTypeResultsHash(entity1);
    Query query = Query.newQuery(null, hash, null, null, null);

    assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> verifyEntityTypeHasNotChangedDuringQueryLifetime(query, entity2)
    );
  }
}
