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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EntityTypeUtilsTest {

    static Stream<Arguments> topLevelPropertyChangeTestCases() {
  EntityType baseline = new EntityType()
    .id("00000000-0000-0000-0000-000000000000")
    .crossTenantQueriesEnabled(false)
    .groupByFields(List.of("field1"))
    .filterConditions(List.of("c1"))
    .additionalEcsConditions(List.of("ecs1"));
  return Stream.of(
    Arguments.of(baseline.toBuilder().id("11111111-1111-1111-1111-111111111111").build(), "id"),
    Arguments.of(baseline.toBuilder().crossTenantQueriesEnabled(true).build(), "crossTenantQueriesEnabled"),
    Arguments.of(baseline.toBuilder().groupByFields(List.of("field1", "field2")).build(), "groupByFields"),
    Arguments.of(baseline.toBuilder().filterConditions(List.of("c1", "c2")).build(), "filterConditions"),
    Arguments.of(baseline.toBuilder().additionalEcsConditions(List.of("ecs1", "ecs2")).build(), "additionalEcsConditions")
  );
}

    static Stream<Arguments> fieldMetadataChangeTestCases() {
    EntityType baseline = new EntityType()
      .id("00000000-0000-0000-0000-000000000000")
      .columns(List.of(
        new EntityTypeColumn()
          .name("col1")
          .dataType(new StringType().dataType("stringType"))
          .isIdColumn(true)
          .isCustomField(false)
          .idColumnName("id")
          .valueFunction(null),
        new EntityTypeColumn()
          .name("obj_col")
          .dataType(new ObjectType()
            .dataType("objectType")
            .properties(List.of(
              new NestedObjectProperty()
                .name("nested1")
                .dataType(new StringType().dataType("stringType"))
                .property("json_key_a")
            ))
          )
      ));
    // Scenario 1: Modify isIdColumn
    EntityType mod1 = baseline.toBuilder().build();
    ((EntityTypeColumn) mod1.getColumns().get(0)).setIsIdColumn(false);
    // Scenario 2: Modify isCustomField
    EntityType mod2 = baseline.toBuilder().build();
    ((EntityTypeColumn) mod2.getColumns().get(0)).setIsCustomField(true);
    // Scenario 3: Modify valueFunction
    EntityType mod3 = baseline.toBuilder().build();
    ((EntityTypeColumn) mod3.getColumns().get(0)).setValueFunction("lower(:value)");
    // Scenario 4: Modify idColumnName
    EntityType mod4 = baseline.toBuilder().build();
    ((EntityTypeColumn) mod4.getColumns().get(0)).setIdColumnName("uuid");
    // Scenario 5: Modify property in NestedObjectProperty
    EntityType mod5 = baseline.toBuilder().build();
    ObjectType modObjectType = (ObjectType) mod5.getColumns().get(1).getDataType();
    ((NestedObjectProperty) modObjectType.getProperties().get(0)).setProperty("json_key_b");
    return Stream.of(
      Arguments.of(mod1, "isIdColumn"),
      Arguments.of(mod2, "isCustomField"),
      Arguments.of(mod3, "valueFunction"),
      Arguments.of(mod4, "idColumnName"),
      Arguments.of(mod5, "property")
    );
  }

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
  void testGetEntityTypeSourceAliasMapNull() {
    EntityType entityType = new EntityType().sources(null);

    assertThat(EntityTypeUtils.getEntityTypeSourceAliasMap(entityType), is(Map.of()));
  }

  @Test
  void testGetEntityTypeSourceAliasMapDuplicate() {
    EntityType entityType = new EntityType()
      .sources(
        List.of(
          new EntityTypeSourceEntityType()
            .alias("source1")
            .targetId(UUID.fromString("24281f3e-d064-5c6f-b1b5-e26e269cc8fb")),
          new EntityTypeSourceEntityType()
            .alias("source1")
            .targetId(UUID.fromString("24281f3e-d064-5c6f-b1b5-e26e269cc8fb"))
        )
      );

    assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> EntityTypeUtils.getEntityTypeSourceAliasMap(entityType)
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

  @Test
  void shouldNotAttemptCountryLocalizationWhenSourceNameIsNotCountries() {
    EntityType entityType = new EntityType().columns(List.of(
      new EntityTypeColumn()
        .name("country")
        .source(new org.folio.querytool.domain.dto.SourceColumn()
          .type(org.folio.querytool.domain.dto.SourceColumn.TypeEnum.FQM)
          .name("not-countries"))
    ));
    List<String> result = EntityTypeUtils.getCountryLocalizationFieldPaths(entityType);
    assertTrue(result.isEmpty(), "Should not return field paths when source name is not 'countries'");
  }

  @Test
  void shouldNotAttemptCountryLocalizationForNonFqmSource() {
    EntityType entityType = new EntityType().columns(List.of(
      new EntityTypeColumn()
        .name("country")
        .source(new org.folio.querytool.domain.dto.SourceColumn()
          .type(org.folio.querytool.domain.dto.SourceColumn.TypeEnum.ENTITY_TYPE)
          .name("countries"))
    ));
    List<String> result = EntityTypeUtils.getCountryLocalizationFieldPaths(entityType);
    assertTrue(result.isEmpty(), "Should not return field paths when source type is not FQM");
  }

  @Test
  void shouldReturnEmptyDefaultValuesMapForEntityTypeWithNoColumns() {
    EntityType entityType = new EntityType().columns(null);
    Map<String, Object> result = EntityTypeUtils.getFieldDefaultValues(entityType);
    assertTrue(result.isEmpty(), "Default values map should be empty for entity type with no columns");
  }

    @Test
void testComputeEntityTypeResultsHashShouldBeDeterministicRegardlessOfInputOrder() {
  // TestMate-89fb2dceebba28a16df51ffe2134e0da
  UUID entityTypeId = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
  EntityTypeSourceDatabase source1 = new EntityTypeSourceDatabase()
    .alias("alpha")
    .type("db")
    .target("table_a");
  EntityTypeSourceDatabase source2 = new EntityTypeSourceDatabase()
    .alias("beta")
    .type("db")
    .target("table_b");
  EntityTypeColumn column1 = new EntityTypeColumn()
    .name("column_1")
    .dataType(new StringType().dataType("stringType"));
  EntityTypeColumn column2 = new EntityTypeColumn()
    .name("column_2")
    .dataType(new StringType().dataType("stringType"));
  EntityType entityWithOrderedLists = new EntityType()
    .id(entityTypeId.toString())
    .sources(List.of(source1, source2))
    .columns(List.of(column1, column2));
  EntityType entityWithShuffledLists = new EntityType()
    .id(entityTypeId.toString())
    .sources(List.of(source2, source1))
    .columns(List.of(column2, column1));
  String hash1 = EntityTypeUtils.computeEntityTypeResultsHash(entityWithOrderedLists);
  String hash2 = EntityTypeUtils.computeEntityTypeResultsHash(entityWithShuffledLists);
  assertEquals(hash1, hash2);
}

    @Test
void testComputeEntityTypeResultsHashShouldBeStableWhenMetadataPropertiesChange() {
  // TestMate-7de752bcad95062ebc1ea441b307ddf2
  // Arrange
  String entityTypeId = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
  EntityTypeSourceDatabase source = new EntityTypeSourceDatabase()
    .alias("source_a")
    .type("db")
    .target("table_a");
  EntityTypeColumn column = new EntityTypeColumn()
    .name("column_1")
    .dataType(new StringType().dataType("stringType"));
  EntityType baselineEntity = new EntityType()
    .id(entityTypeId)
    .name("test_entity")
    .sources(List.of(source))
    .columns(List.of(column))
    .description("Initial description")
    .labelAlias("initial.alias")
    .requiredPermissions(List.of("perm.view"))
    .usedBy(List.of("mod-a"));
  EntityType metadataModifiedEntity = baselineEntity.toBuilder()
    .description("Modified description")
    .labelAlias("modified.alias")
    .requiredPermissions(List.of("perm.view", "perm.admin"))
    .usedBy(List.of("mod-b"))
    .build();
  // Act
  String hash1 = EntityTypeUtils.computeEntityTypeResultsHash(baselineEntity);
  String hash2 = EntityTypeUtils.computeEntityTypeResultsHash(metadataModifiedEntity);
  // Assert
  assertEquals(hash1, hash2);
}

    @ParameterizedTest
@MethodSource("topLevelPropertyChangeTestCases")
void testComputeEntityTypeResultsHashShouldDetectChangesInTopLevelProperties(EntityType modifiedEntity, String propertyName) {
  // TestMate-d27cd55320e4c5480bbac3d3a479253c
  // Arrange
  EntityType baselineEntity = new EntityType()
    .id("00000000-0000-0000-0000-000000000000")
    .crossTenantQueriesEnabled(false)
    .groupByFields(List.of("field1"))
    .filterConditions(List.of("c1"))
    .additionalEcsConditions(List.of("ecs1"));
  String baselineHash = EntityTypeUtils.computeEntityTypeResultsHash(baselineEntity);
  // Act
  String modifiedHash = EntityTypeUtils.computeEntityTypeResultsHash(modifiedEntity);
  // Assert
  assertNotEquals(baselineHash, modifiedHash, "Hash should change when property '" + propertyName + "' is modified");
}

    @ParameterizedTest
  @MethodSource("fieldMetadataChangeTestCases")
  void testComputeEntityTypeResultsHashShouldDetectChangesInFieldMetadata(EntityType modifiedEntity, String propertyName) {
    // TestMate-1b3323f463ebd3714908f68580c8e867
    // Arrange
    EntityType baselineEntity = new EntityType()
      .id("00000000-0000-0000-0000-000000000000")
      .columns(List.of(
        new EntityTypeColumn()
          .name("col1")
          .dataType(new StringType().dataType("stringType"))
          .isIdColumn(true)
          .isCustomField(false)
          .idColumnName("id")
          .valueFunction(null),
        new EntityTypeColumn()
          .name("obj_col")
          .dataType(new ObjectType()
            .dataType("objectType")
            .properties(List.of(
              new NestedObjectProperty()
                .name("nested1")
                .dataType(new StringType().dataType("stringType"))
                .property("json_key_a")
            ))
          )
      ));
    String baselineHash = EntityTypeUtils.computeEntityTypeResultsHash(baselineEntity);
    // Act
    String modifiedHash = EntityTypeUtils.computeEntityTypeResultsHash(modifiedEntity);
    // Assert
    assertNotEquals(baselineHash, modifiedHash, "Hash should change when field property '" + propertyName + "' is modified");
  }

    @Test
void testComputeEntityTypeResultsHashShouldHandleNullSourcesAndColumns() {
  // TestMate-254935feee8bdd795accb5100cae2b93
  // Given
  String entityTypeId = "00000000-0000-0000-0000-000000000000";
  String sha256Pattern = "^[a-f0-9]{64}$";
  EntityType entityWithNullSources = new EntityType()
    .id(entityTypeId)
    .sources(null)
    .columns(List.of(
      new EntityTypeColumn()
        .name("column_1")
        .dataType(new StringType().dataType("stringType"))
    ));
  EntityType entityWithNullColumns = new EntityType()
    .id(entityTypeId)
    .sources(List.of(
      new EntityTypeSourceDatabase()
        .alias("source_a")
        .type("db")
        .target("table_a")
    ))
    .columns(null);
  EntityType entityWithBothNull = new EntityType()
    .id(entityTypeId)
    .sources(null)
    .columns(null);
  // When
  String hashNullSources = assertDoesNotThrow(() -> EntityTypeUtils.computeEntityTypeResultsHash(entityWithNullSources));
  String hashNullColumns = assertDoesNotThrow(() -> EntityTypeUtils.computeEntityTypeResultsHash(entityWithNullColumns));
  String hashBothNull = assertDoesNotThrow(() -> EntityTypeUtils.computeEntityTypeResultsHash(entityWithBothNull));
  // Then
  assertNotNull(hashNullSources);
  assertThat(hashNullSources, matchesPattern(sha256Pattern));
  assertNotNull(hashNullColumns);
  assertThat(hashNullColumns, matchesPattern(sha256Pattern));
  assertNotNull(hashBothNull);
  assertThat(hashBothNull, matchesPattern(sha256Pattern));
  assertThat(hashNullSources, not(equalTo(hashNullColumns)));
  assertThat(hashNullSources, not(equalTo(hashBothNull)));
  assertThat(hashNullColumns, not(equalTo(hashBothNull)));
}
}
