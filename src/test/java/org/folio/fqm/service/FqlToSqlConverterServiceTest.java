package org.folio.fqm.service;

import org.folio.fql.service.FqlService;
import org.folio.fqm.exception.ColumnNotFoundException;
import org.folio.querytool.domain.dto.ArrayType;
import org.folio.querytool.domain.dto.DateType;
import org.folio.querytool.domain.dto.EntityDataType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.jooq.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;

import static org.jooq.impl.DSL.condition;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.or;
import static org.jooq.impl.DSL.val;
import static org.junit.jupiter.api.Assertions.*;

class FqlToSqlConverterServiceTest {

  private FqlToSqlConverterService fqlToSqlConverter;
  private EntityType entityType;

  @BeforeEach
  void setup() {
    fqlToSqlConverter = new FqlToSqlConverterService(new FqlService());
    entityType = new EntityType()
      .columns(
        List.of(
          new EntityTypeColumn().name("field1").dataType(new EntityDataType().dataType("stringType")),
          new EntityTypeColumn().name("field2").dataType(new EntityDataType().dataType("stringType")),
          new EntityTypeColumn().name("field3").dataType(new EntityDataType().dataType("stringType")),
          new EntityTypeColumn().name("field4").dataType(new DateType()),
          new EntityTypeColumn().name("arrayField").dataType(new ArrayType()),
          new EntityTypeColumn().name("fieldWithFilterValueGetter")
            .dataType(new EntityDataType().dataType("stringType"))
            .filterValueGetter("thisIsAFilterValueGetter")
        )
      );
  }

  static List<Arguments> jooqConditionsSource() {
    // list of fqlCondition, expectedCondition
    return Arrays.asList(
      Arguments.of(
        "equals string",
        """
          {"field1": {"$eq": "some value"}}""",
        field("field1").equalIgnoreCase("some value")
      ),
      Arguments.of(
        "equals numeric",
        """
          {"field1": {"$eq": 10}}""",
        field("field1").equal(10)
      ),
      Arguments.of(
        "equals boolean",
        """
          {"field1": {"$eq": true}}""",
        field("field1").equal(true)
      ),
      Arguments.of(
        "equals date and time",
        """
          {"field4": {"$eq": "2023-06-02T16:11:08.766+00:00"}}""",
        field("field4").equalIgnoreCase("2023-06-02T16:11:08.766+00:00")
      ),
      Arguments.of(
        "equals date",
        """
          {"field4": {"$eq": "2023-06-02"}}""",
        field("field4").greaterOrEqual("2023-06-02")
          .and(field("field4").lessThan("2023-06-03"))
      ),

      Arguments.of(
        "not equals string",
        """
          {"field1": {"$ne": "some value"}}""",
        field("field1").notEqualIgnoreCase("some value")
      ),
      Arguments.of(
        "not equals numeric",
        """
          {"field1": {"$ne": 10}}""",
        field("field1").notEqual(10)
      ),
      Arguments.of(
        "not equals boolean",
        """
          {"field1": {"$ne": true}}""",
        field("field1").notEqual(true)
      ),
      Arguments.of(
        "not equals date and time",
        """
          {"field4": {"$ne": "2023-06-02T16:11:08.766+00:00"}}""",
        field("field4").notEqualIgnoreCase("2023-06-02T16:11:08.766+00:00")
      ),
      Arguments.of(
        "not equals date",
        """
          {"field4": {"$ne": "2023-06-02"}}""",
        field("field4").greaterOrEqual("2023-06-03")
          .or(field("field4").lessThan("2023-06-02"))
      ),

      Arguments.of(
        "greater than string",
        """
          {"field1": {"$gt": "some value"}}""",
        field("field1").greaterThan("some value")
      ),
      Arguments.of(
        "greater than numeric",
        """
          {"field1": {"$gt": 10}}""",
        field("field1").greaterThan(10)
      ),
      Arguments.of(
        "greater than date",
        """
          {"field4": {"$gt": "2023-06-02"}}""",
        field("field4").greaterOrEqual("2023-06-03")
      ),

      Arguments.of(
        "greater than or equal to string",
        """
          {"field1": {"$gte": "some value"}}""",
        field("field1").greaterOrEqual("some value")
      ),
      Arguments.of(
        "greater than or equal to numeric",
        """
          {"field1": {"$gte": 10}}""",
        field("field1").greaterOrEqual(10)
      ),
      Arguments.of(
        "greater than or equal to date",
        """
          {"field4": {"$gte": "2023-06-02"}}""",
        field("field4").greaterOrEqual("2023-06-02")
      ),

      Arguments.of(
        "less than string",
        """
          {"field1": {"$lt": "some value"}}""",
        field("field1").lessThan("some value")
      ),
      Arguments.of(
        "less than numeric",
        """
          {"field1": {"$lt": 10}}""",
        field("field1").lessThan(10)
      ),
      Arguments.of(
        "less than date",
        """
          {"field4": {"$lt": "2023-06-02"}}""",
        field("field4").lessThan("2023-06-02")
      ),

      Arguments.of(
        "less than or equal to string",
        """
          {"field1": {"$lte": "some value"}}""",
        field("field1").lessOrEqual("some value")
      ),
      Arguments.of(
        "less than or equal to numeric",
        """
          {"field1": {"$lte": 10}}""",
        field("field1").lessOrEqual(10)
      ),
      Arguments.of(
        "less than or equal to date",
        """
          {"field4": {"$lte": "2023-06-02"}}""",
        field("field4").lessThan("2023-06-03")
      ),

      Arguments.of(
        "regex",
        """
          {"field1": {"$regex": "some_text"}}""",
        condition("{0} ~* {1}", field("field1"), val("some_text"))
      ),
      Arguments.of(
        "in list",
        """
          {"field1": {"$in": ["value1", 2, true]}}""",
        or(
          field("field1").equalIgnoreCase("value1"),
          field("field1").eq(2),
          field("field1").eq(true)
        )
      ),
      Arguments.of(
        "not in list",
        """
          {"field1": {"$nin": ["value1", 2, true]}}""",
        and(
          field("field1").notEqualIgnoreCase("value1"),
          field("field1").notEqual(2),
          field("field1").notEqual(true)
        )
      ),

      Arguments.of(
        "contains string",
        """
          {"arrayField": {"$contains": "some value"}}""",
        field("arrayField").containsIgnoreCase("some value")
      ),
      Arguments.of(
        "contains numeric",
        """
          {"arrayField": {"$contains": 10}}""",
        field("arrayField").contains(10)
      ),

      Arguments.of(
        "not contains string",
        """
          {"arrayField": {"$not_contains": "some value"}}""",
        field("arrayField").notContainsIgnoreCase("some value").or(field("arrayField").isNull())
      ),
      Arguments.of(
        "not contains numeric",
        """
          {"arrayField": {"$not_contains": 10}}""",
        field("arrayField").notContains(10).or(field("arrayField").isNull())
      ),

      Arguments.of(
        "complex condition",
        """
          {
            "$and": [
              {"field1": {"$eq": "some value"}},
              {"field2": {"$eq": true}},
              {"field3": {"$lte": 3}},
              {"field1": {"$eq": false}},
              {"field2": {"$in": ["value1", 2, true]}},
              {"field3": {"$ne": 5}},
              {"field1": {"$gt": 9}},
              {"field2": {"$lt": 11}},
              {"field3": {"$nin": ["value1", 2, true]}}
            ]
          }""",
        field("field1").equalIgnoreCase("some value")
          .and(field("field2").eq(true))
          .and(field("field3").lessOrEqual(3))
          .and(field("field1").eq(false))
          .and(
            or(
              field("field2").equalIgnoreCase("value1"),
              field("field2").eq(2),
              field("field2").eq(true)
            )
          )
          .and(field("field3").notEqual(5))
          .and(field("field1").greaterThan(9))
          .and(field("field2").lessThan(11))
          .and(
            and(
              field("field3").notEqualIgnoreCase("value1"),
              field("field3").notEqual(2),
              field("field3").notEqual(true)
            )
          )
      ),

      Arguments.of(
        "condition on a field with a filter value getter",
        """
           {"fieldWithFilterValueGetter": {"$eq": "Test value"}}""",
        field("thisIsAFilterValueGetter").eq("Test value".toLowerCase())
      )
    );
  }

  @ParameterizedTest
  @MethodSource("jooqConditionsSource")
  void shouldGetJooqConditionForFqlCondition(String label, String fqlCondition, Condition expectedCondition) {
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition, "Jooq Condition equals FQL Condition for " + label);
  }

  @Test
  void shouldThrowExceptionForInvalidColumn() {
    String fqlWithNonExistingColumn = """
      {"non_existing_column": {"$nin": ["value1", 2, true]}}
      """;
    assertThrows(
      ColumnNotFoundException.class,
      () -> fqlToSqlConverter.getSqlCondition(fqlWithNonExistingColumn, entityType)
    );
  }
}
