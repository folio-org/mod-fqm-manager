package org.folio.fqm.service;

import org.folio.fql.service.FqlService;
import org.folio.fqm.exception.FieldNotFoundException;
import org.folio.querytool.domain.dto.DateType;
import org.folio.querytool.domain.dto.EntityDataType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.cardinality;
import static org.jooq.impl.DSL.cast;
import static org.jooq.impl.DSL.condition;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.or;
import static org.jooq.impl.DSL.param;
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
          new EntityTypeColumn().name("field2").dataType(new EntityDataType().dataType("booleanType")),
          new EntityTypeColumn().name("field3").dataType(new EntityDataType().dataType("stringType")),
          new EntityTypeColumn().name("field4").dataType(new DateType().dataType("dateType")),
          new EntityTypeColumn().name("field5").dataType(new EntityDataType().dataType("integerType")),
          new EntityTypeColumn().name("rangedUUIDField").dataType(new EntityDataType().dataType("rangedUUIDType")),
          new EntityTypeColumn().name("openUUIDField").dataType(new EntityDataType().dataType("openUUIDType")),
          new EntityTypeColumn().name("arrayField").dataType(new EntityDataType().dataType("arrayType")),
          new EntityTypeColumn().name("arrayFieldWithValueFunction")
            .dataType(new EntityDataType().dataType("arrayType"))
            .valueGetter("valueGetter")
            .filterValueGetter("foo(valueGetter)")
            .valueFunction("foo(:value)"),
          new EntityTypeColumn().name("fieldWithFilterValueGetter")
            .dataType(new EntityDataType().dataType("stringType"))
            .filterValueGetter("thisIsAFilterValueGetter"),
          new EntityTypeColumn().name("fieldWithFilterValueGetterAndValueFunction")
            .dataType(new EntityDataType().dataType("stringType"))
            .filterValueGetter("thisIsAFilterValueGetter")
            .valueFunction("lower(:value)"),
          new EntityTypeColumn().name("fieldWithAValueFunction")
            .dataType(new EntityDataType().dataType("stringType"))
            .valueFunction("upper(:value)")
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
          {"field5": {"$eq": 10}}""",
        field("field5").equal(10)
      ),
      Arguments.of(
        "equals boolean",
        """
          {"field2": {"$eq": true}}""",
        field("field2").equal(true)
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
        "equals ranged UUID",
        """
          {"rangedUUIDField": {"$eq": "69939c9a-aa96-440a-a873-3b48f3f4f608"}}""",
        field("rangedUUIDField").equalIgnoreCase("69939c9a-aa96-440a-a873-3b48f3f4f608")
      ),
      Arguments.of(
        "equals open UUID",
        """
          {"openUUIDField": {"$eq": "69939c9a-aa96-440a-a873-3b48f3f4f608"}}""",
        field("openUUIDField").equalIgnoreCase("69939c9a-aa96-440a-a873-3b48f3f4f608")
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
          {"field5": {"$ne": 10}}""",
        field("field5").notEqual(10)
      ),
      Arguments.of(
        "not equals boolean",
        """
          {"field2": {"$ne": true}}""",
        field("field2").notEqual(true)
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
        "not equals ranged UUID",
        """
          {"rangedUUIDField": {"$ne": "69939c9a-aa96-440a-a873-3b48f3f4f608"}}""",
        field("rangedUUIDField").notEqualIgnoreCase("69939c9a-aa96-440a-a873-3b48f3f4f608")
      ),
      Arguments.of(
        "not equals open UUID",
        """
          {"openUUIDField": {"$ne": "69939c9a-aa96-440a-a873-3b48f3f4f608"}}""",
        field("openUUIDField").notEqualIgnoreCase("69939c9a-aa96-440a-a873-3b48f3f4f608")
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
        "regex",
        """
          {"fieldWithAValueFunction": {"$regex": "some_text"}}""",
        condition("{0} ~* {1}", field("fieldWithAValueFunction"), field("upper(:value)", String.class, param("value", "some_text")))
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
          {"arrayField": {"$contains_all": ["Some vALUE"]}}""",
        DSL.cast(field("arrayField"), String[].class).contains(DSL.cast(DSL.array("Some vALUE"), String[].class))
      ),
      Arguments.of(
        "contains numeric",
        """
          {"arrayField": {"$contains_all": [10]}}""",
        DSL.cast(field("arrayField"), String[].class).contains(DSL.cast(DSL.array(10), String[].class))
      ),

      Arguments.of(
        "not contains string",
        """
          {"arrayField": {"$not_contains_all": ["Some vALUE"]}}""",
        DSL.cast(field("arrayField"), String[].class).notContains(DSL.cast(DSL.array("Some vALUE"), String[].class))
      ),
      Arguments.of(
        "not contains numeric",
        """
          {"arrayField": {"$not_contains_all": [10]}}""",
        DSL.cast(field("arrayField"), String[].class).notContains(DSL.cast(DSL.array(10), String[].class))
      ),

      Arguments.of(
        "complex condition",
        """
          {
            "$and": [
              {"field1": {"$eq": "some value"}},
              {"field2": {"$eq": true}},
              {"field5": {"$lte": 3}},
              {"field2": {"$in": [false, true]}},
              {"field5": {"$ne": 5}},
              {"field5": {"$gt": 9}},
              {"field3": {"$nin": ["value1", "value2"]}}
            ]
          }""",
        field("field1").equalIgnoreCase("some value")
          .and(field("field2").eq(true))
          .and(field("field5").lessOrEqual(3))
          .and(
            or(
              field("field2").eq(false),
              field("field2").eq(true)
            )
          )
          .and(field("field5").notEqual(5))
          .and(field("field5").greaterThan(9))
          .and(
            and(
              field("field3").notEqualIgnoreCase("value1"),
              field("field3").notEqualIgnoreCase("value2")
            )
          )
      ),

      Arguments.of(
        "condition on a field with a filter value getter",
        """
          {"fieldWithFilterValueGetter": {"$eq": "Test value"}}""",
        field("thisIsAFilterValueGetter").eq("Test value".toLowerCase())
      ),

      Arguments.of(
        "condition on a field with a filter value getter and a value function",
        """
         {
            "$and": [
              {"fieldWithFilterValueGetterAndValueFunction": {"$eq": "Test value"}},
              {"fieldWithAValueFunction": {"$eq": "Test value2"}}
            ]
          }""",
        field("thisIsAFilterValueGetter").eq(field("lower(:value)", param("value", "Test value".toLowerCase())))
          .and(field("fieldWithAValueFunction").equalIgnoreCase(field("upper(:value)", String.class, param("value", "Test value2"))))
      ),

      Arguments.of(
        "in operator on a field with a valueFunction",
        """
          {"fieldWithAValueFunction": {"$in": ["value1", 2, true]}}""",
        or(
          field("fieldWithAValueFunction").equalIgnoreCase(field("upper(:value)", String.class, param("value", "value1"))),
          field("fieldWithAValueFunction").eq(field("upper(:value)", String.class, param("value", 2))),
          field("fieldWithAValueFunction").eq(field("upper(:value)", String.class, param("value", true))))
      ),

      Arguments.of(
        "not-in operator on a field with a valueFunction",
        """
          {"fieldWithAValueFunction": {"$nin": ["value1", 2, true]}}""",
        and(
          field("fieldWithAValueFunction").notEqualIgnoreCase(field("upper(:value)", String.class, param("value", "value1"))),
          field("fieldWithAValueFunction").notEqual(field("upper(:value)", String.class, param("value", 2))),
          field("fieldWithAValueFunction").notEqual(field("upper(:value)", String.class, param("value", true)))
        )
      ),

      Arguments.of(
        "contains_all condition on a field with a filter value getter and a value function",
        """
         {    "arrayFieldWithValueFunction": {"$contains_all": ["value1", "value2"]}}
         }""",
        DSL
          .cast(
            field("foo(valueGetter)"),
            String[].class
          )
          .contains(
            DSL.cast(
              DSL.array(
                field("foo(:value1)",  String.class, param("value", "value1")),
                field("foo(:value)", String.class, param("value", "value2"))
              ),
              String[].class
            )
          )
      ),

      Arguments.of(
        "not_contains_all condition on a field with a filter value getter and a value function",
        """
         {    "arrayFieldWithValueFunction": {"$not_contains_all": [10, 20]}}
         }""",
        DSL
          .cast(
            field("foo(valueGetter)"),
            String[].class
          )
          .notContains(
            DSL.cast(
              DSL.array(
                field("foo(:value)",  Integer.class, param("value", 10)),
                field("foo(:value)", Integer.class, param("value", 20))
              ),
              String[].class
            )
          )
      ),

      Arguments.of(
        "empty",
        """
          {"field5": {"$empty": true}}""",
        field("field5").isNull()
      ),
      Arguments.of(
        "not empty",
        """
          {"field5": {"$empty": false}}""",
        field("field5").isNotNull()
      ),
      Arguments.of(
        "empty string",
        """
          {"field1": {"$empty": true}}""",
        field("field1").isNull().or(field("field1").eq(""))
      ),
      Arguments.of(
        "not empty string",
        """
          {"field1": {"$empty": false}}""",
        field("field1").isNotNull().and(field("field1").ne(""))
      ),
      Arguments.of(
        "empty array",
        """
          {"arrayField": {"$empty": true}}""",
        field("arrayField").isNull().or(cardinality(cast(field("arrayField"), String[].class)).eq(0))
      ),
      Arguments.of(
        "not empty array",
        """
          {"arrayField": {"$empty": false}}""",
        field("arrayField").isNotNull().and(cardinality(cast(field("arrayField"), String[].class)).ne(0))
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
      FieldNotFoundException.class,
      () -> fqlToSqlConverter.getSqlCondition(fqlWithNonExistingColumn, entityType)
    );
  }
}
