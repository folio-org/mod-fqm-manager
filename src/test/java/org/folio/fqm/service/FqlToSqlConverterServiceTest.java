package org.folio.fqm.service;

import org.folio.fql.service.FqlService;
import org.folio.fqm.exception.FieldNotFoundException;
import org.folio.fqm.exception.InvalidFqlException;
import org.folio.querytool.domain.dto.ArrayType;
import org.folio.querytool.domain.dto.DateTimeType;
import org.folio.querytool.domain.dto.DateType;
import org.folio.querytool.domain.dto.EntityDataType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.JsonbArrayType;
import org.folio.querytool.domain.dto.NestedObjectProperty;
import org.folio.querytool.domain.dto.NumberType;
import org.folio.querytool.domain.dto.ObjectType;
import org.folio.querytool.domain.dto.StringType;
import org.jooq.Condition;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.folio.fqm.service.FqlToSqlConverterService.UUID_REGEX;
import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.array;
import static org.jooq.impl.DSL.arrayOverlap;
import static org.jooq.impl.DSL.cardinality;
import static org.jooq.impl.DSL.cast;
import static org.jooq.impl.DSL.condition;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.falseCondition;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.not;
import static org.jooq.impl.DSL.or;
import static org.jooq.impl.DSL.param;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.unnest;
import static org.jooq.impl.DSL.val;
import static org.jooq.impl.DSL.trueCondition;
import static org.junit.jupiter.api.Assertions.*;

class FqlToSqlConverterServiceTest {

  private FqlToSqlConverterService fqlToSqlConverter;
  private EntityType entityType;

  @BeforeEach
  void setup() {
    fqlToSqlConverter = new FqlToSqlConverterService(new FqlService());
    entityType = new EntityType()
      .sources(List.of())
      .columns(
        List.of(
          new EntityTypeColumn().name("field1").dataType(new EntityDataType().dataType("stringType")),
          new EntityTypeColumn().name("field2").dataType(new EntityDataType().dataType("booleanType")),
          new EntityTypeColumn().name("field3").dataType(new EntityDataType().dataType("stringType")),
          new EntityTypeColumn().name("field4").dataType(new DateTimeType().dataType("dateTimeType")),
          new EntityTypeColumn().name("field5").dataType(new EntityDataType().dataType("integerType")),
          new EntityTypeColumn().name("field6").dataType(new DateType().dataType("dateType")),
          new EntityTypeColumn().name("rangedUUIDField").dataType(new EntityDataType().dataType("rangedUUIDType")),
          new EntityTypeColumn().name("stringUUIDField").dataType(new EntityDataType().dataType("stringUUIDType")),
          new EntityTypeColumn().name("openUUIDField").dataType(new EntityDataType().dataType("openUUIDType")),
          new EntityTypeColumn().name("validatedField").dataType(new EntityDataType().dataType("rangedUUIDType")).validated(true),
          // TODO: boolean tests?
          new EntityTypeColumn().name("booleanDefaultValue").dataType(new EntityDataType().dataType("booleanType")).defaultValue(false),
          new EntityTypeColumn().name("numberDefaultValue").dataType(new EntityDataType().dataType("numberType")).defaultValue(10),
          new EntityTypeColumn().name("stringDefaultValue").dataType(new EntityDataType().dataType("stringType")).defaultValue("default"),
          new EntityTypeColumn().name("arrayField").dataType(new ArrayType().dataType("arrayType").itemDataType(new NumberType().dataType("numberType"))),
          new EntityTypeColumn().name("jsonbArrayField").dataType(new EntityDataType().dataType("jsonbArrayType")),
          new EntityTypeColumn().name("stringArrayField").dataType(new ArrayType().dataType("arrayType").itemDataType(
            new ObjectType().dataType("objectType").properties(List.of(
              new NestedObjectProperty().name("stringSubField").dataType(new StringType().dataType("stringType"))
            ))
          )),
          new EntityTypeColumn().name("jsonbStringArray").dataType(new JsonbArrayType().dataType("jsonbArrayType").itemDataType(new StringType().dataType("stringType"))),
          new EntityTypeColumn().name("jsonbArrayFieldWithValueFunction")
            .dataType(new EntityDataType().dataType("jsonbArrayType"))
            .valueGetter("valueGetter")
            .filterValueGetter("foo(valueGetter)")
            .valueFunction("lower(:value)"),
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
            .valueFunction("upper(:value)"),
          new EntityTypeColumn().name("nested")
            .dataType(new ArrayType().dataType("arrayType")
              .itemDataType(new ObjectType().dataType("objectType")
                .properties(List.of(
                  new NestedObjectProperty().name("string").dataType(new EntityDataType().dataType("stringType")).valueGetter("nestStr"),
                  new NestedObjectProperty().name("ruuid").dataType(new EntityDataType().dataType("rangedUUIDType")).valueGetter("nestRUuid"),
                  new NestedObjectProperty().name("ouuid").dataType(new EntityDataType().dataType("openUUIDType")).valueGetter("nestOUuid")
                ))))
        )
      );
  }

  static Condition trueCondition = trueCondition();
  private static final Condition exists = null;

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
        "equals string (long)",
        """
          {"field1": {"$eq": "this string contains more than 32 characters."}}""",
        field("field1").equalIgnoreCase("this string contains more than 32 characters.")
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
        "equals date-time (no time component)",
        """
          {"field4": {"$eq": "2023-06-02"}}""",
        field("field4").greaterOrEqual("2023-06-02T00:00:00.000")
          .and(field("field4").lessThan("2023-06-03T00:00:00.000"))
      ),
      Arguments.of(
        "equals date-time (with time component)",
        """
          {"field4": {"$eq": "2024-09-13T04:00:00.000"}}""",
        field("field4").greaterOrEqual("2024-09-13T04:00:00.000")
          .and(field("field4").lessThan("2024-09-14T04:00:00.000"))
      ),
      Arguments.of(
        "equals date",
        """
          {"field6": {"$eq": "2023-06-02"}}""",
        field("field6").eq("2023-06-02")
      ),
      Arguments.of(
        "equals ranged UUID",
        """
          {"rangedUUIDField": {"$eq": "69939c9a-aa96-440a-a873-3b48f3f4f608"}}""",
        cast(field("rangedUUIDField"), UUID.class).eq(cast(inline(UUID.fromString("69939c9a-aa96-440a-a873-3b48f3f4f608")), UUID.class))
      ),
      Arguments.of(
        "equals invalid ranged UUID",
        "{\"rangedUUIDField\": {\"$eq\": \"invalid-uuid\"}}",
        cast(field("rangedUUIDField"), UUID.class).eq(cast(null, UUID.class))
      ),
      Arguments.of(
        "not equals invalid ranged UUID",
        "{\"rangedUUIDField\": {\"$ne\": \"invalid-uuid\"}}",
        trueCondition.or(field("rangedUUIDField").isNull())
      ),
      Arguments.of(
        "equals invalid open UUID",
        "{\"openUUIDField\": {\"$eq\": \"invalid-uuid\"}}",
        cast(field("openUUIDField"), UUID.class).eq(cast(null, UUID.class))
      ),
      Arguments.of(
        "not equals invalid open UUID",
        "{\"openUUIDField\": {\"$ne\": \"invalid-uuid\"}}",
        trueCondition.or(field("openUUIDField").isNull())
      ),
      Arguments.of(
        "equals open UUID",
        """
          {"openUUIDField": {"$eq": "69939c9a-aa96-440a-a873-3b48f3f4f608"}}""",
        cast(field("openUUIDField"), UUID.class).eq(cast(inline(UUID.fromString("69939c9a-aa96-440a-a873-3b48f3f4f608")), UUID.class))
      ),
      Arguments.of(
        "equals string UUID",
        """
          {"stringUUIDField": {"$eq": "69939c9a-aa96-440a-a873-3b48f3f4f608"}}""",
        cast(field("stringUUIDField"), String.class).equalIgnoreCase("69939c9a-aa96-440a-a873-3b48f3f4f608")
      ),
      Arguments.of(
        "equals array string",
        """
          {"arrayField": {"$eq": "value1"}}""",
        arrayOverlap(cast(field("arrayField"), String[].class), cast(array("value1"), String[].class))
      ),
      Arguments.of(
        "equals array numeric",
        """
          {"arrayField": {"$eq": 123}}""",
        arrayOverlap(cast(field("arrayField"), String[].class), cast(array(123), String[].class))
      ),
      Arguments.of(
        "equals array boolean",
        """
          {"arrayField": {"$eq": true}}""",
        arrayOverlap(cast(field("arrayField"), String[].class), cast(array(true), String[].class))
      ),
      Arguments.of(
        "equals jsonb array string",
        """
          {"jsonbArrayField": {"$eq": "value1"}}""",
        DSL.condition("{0} @> {1}::jsonb", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"value1\"]"))
      ),
      Arguments.of(
        "equals jsonb array numeric",
        """
          {"jsonbArrayField": {"$eq": 123}}""",
        DSL.condition("{0} @> {1}::jsonb", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"123\"]"))
      ),
      Arguments.of(
        "equals jsonb array boolean",
        """
          {"jsonbArrayField": {"$eq": false}}""",
        DSL.condition("{0} @> {1}::jsonb", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"false\"]"))
      ),
      Arguments.of(
        "equals array string with special characters",
        """
          {"arrayField": {"$eq": "value with spaces & special chars!"}}""",
        arrayOverlap(cast(field("arrayField"), String[].class), cast(array("value with spaces & special chars!"), String[].class))
      ),
      Arguments.of(
        "equals jsonb array string with special characters",
        """
          {"jsonbArrayField": {"$eq": "value with spaces & special chars!"}}""",
        DSL.condition("{0} @> {1}::jsonb", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"value with spaces & special chars!\"]"))
      ),
      Arguments.of(
        "equals array empty string",
        """
          {"arrayField": {"$eq": ""}}""",
        arrayOverlap(cast(field("arrayField"), String[].class), cast(array(""), String[].class))
      ),
      Arguments.of(
        "equals jsonb array empty string",
        """
          {"jsonbArrayField": {"$eq": ""}}""",
        DSL.condition("{0} @> {1}::jsonb", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"\"]"))
      ),
      Arguments.of(
        "equals jsonb array with value function",
        """
          {"jsonbArrayFieldWithValueFunction": {"$eq": "French"}}""",
        DSL.condition("{0} @> jsonb_build_array({1}::text)", field("foo(valueGetter)").cast(JSONB.class), field("lower(:value)", String.class, param("value", "French")))
      ),
      Arguments.of(
        "not equals string",
        """
          {"field1": {"$ne": "some value"}}""",
        field("field1").notEqualIgnoreCase("some value").or(field("field1").isNull())
      ),
      Arguments.of(
        "not equals string UUID",
        """
          {"stringUUIDField": {"$ne": "69939c9a-aa96-440a-a873-3b48f3f4f608"}}""",
        cast(field("stringUUIDField"), String.class).notEqualIgnoreCase("69939c9a-aa96-440a-a873-3b48f3f4f608").or(field("stringUUIDField").isNull())
      ),
      Arguments.of(
        "not equals numeric",
        """
          {"field5": {"$ne": 10}}""",
        field("field5").notEqual(10).or(field("field5").isNull())
      ),
      Arguments.of(
        "not equals boolean",
        """
          {"field2": {"$ne": true}}""",
        field("field2").notEqual(true).or(field("field2").isNull())
      ),
      Arguments.of(
        "not equals date-time (no time component)",
        """
          {"field4": {"$ne": "2023-06-02"}}""",
        field("field4").greaterOrEqual("2023-06-03T00:00:00.000")
          .or(field("field4").lessThan("2023-06-02T00:00:00.000"))
          .or(field("field4").isNull())
      ),
      Arguments.of(
        "not equals date-time (with time component)",
        """
          {"field4": {"$ne": "2023-06-02T04:00:00.000"}}""",
        field("field4").greaterOrEqual("2023-06-03T04:00:00.000")
          .or(field("field4").lessThan("2023-06-02T04:00:00.000"))
          .or(field("field4").isNull())
      ),
      Arguments.of(
        "not equals date",
        """
          {"field6": {"$ne": "2023-06-02"}}""",
        field("field6").ne("2023-06-02").or(field("field6").isNull())
      ),
      Arguments.of(
        "not equals ranged UUID",
        """
          {"rangedUUIDField": {"$ne": "69939c9a-aa96-440a-a873-3b48f3f4f608"}}""",
        cast(field("rangedUUIDField"), UUID.class).ne(cast(inline(UUID.fromString("69939c9a-aa96-440a-a873-3b48f3f4f608")), UUID.class)).or(field("rangedUUIDField").isNull())
      ),
      Arguments.of(
        "not equals open UUID",
        """
          {"openUUIDField": {"$ne": "69939c9a-aa96-440a-a873-3b48f3f4f608"}}""",
        cast(field("openUUIDField"), UUID.class).ne(cast(inline(UUID.fromString("69939c9a-aa96-440a-a873-3b48f3f4f608")), UUID.class)).or(field("openUUIDField").isNull())
      ),
      Arguments.of(
        "not equals array string",
        """
          {"arrayField": {"$ne": "value1"}}""",
        not(arrayOverlap(cast(field("arrayField"), String[].class), cast(array("value1"), String[].class))).or(field("arrayField").isNull())
      ),
      Arguments.of(
        "not equals array numeric",
        """
          {"arrayField": {"$ne": 123}}""",
        not(arrayOverlap(cast(field("arrayField"), String[].class), cast(array(123), String[].class))).or(field("arrayField").isNull())
      ),
      Arguments.of(
        "not equals array boolean",
        """
          {"arrayField": {"$ne": true}}""",
        not(arrayOverlap(cast(field("arrayField"), String[].class), cast(array(true), String[].class))).or(field("arrayField").isNull())
      ),
      Arguments.of(
        "not equals jsonb array string",
        """
          {"jsonbArrayField": {"$ne": "value1"}}""",
        DSL.condition("NOT({0} @> {1}::jsonb)", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"value1\"]")).or(field("jsonbArrayField").isNull())
      ),
      Arguments.of(
        "not equals jsonb array numeric",
        """
          {"jsonbArrayField": {"$ne": 123}}""",
        DSL.condition("NOT({0} @> {1}::jsonb)", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"123\"]")).or(field("jsonbArrayField").isNull())
      ),
      Arguments.of(
        "not equals jsonb array boolean",
        """
          {"jsonbArrayField": {"$ne": false}}""",
        DSL.condition("NOT({0} @> {1}::jsonb)", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"false\"]")).or(field("jsonbArrayField").isNull())
      ),
      Arguments.of(
        "not equals array string with special characters",
        """
          {"arrayField": {"$ne": "value with spaces & special chars!"}}""",
        not(arrayOverlap(cast(field("arrayField"), String[].class), cast(array("value with spaces & special chars!"), String[].class))).or(field("arrayField").isNull())
      ),
      Arguments.of(
        "not equals jsonb array string with special characters",
        """
          {"jsonbArrayField": {"$ne": "value with spaces & special chars!"}}""",
        DSL.condition("NOT({0} @> {1}::jsonb)", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"value with spaces & special chars!\"]")).or(field("jsonbArrayField").isNull())
      ),
      Arguments.of(
        "not equals array empty string",
        """
          {"arrayField": {"$ne": ""}}""",
        not(arrayOverlap(cast(field("arrayField"), String[].class), cast(array(""), String[].class))).or(field("arrayField").isNull())
      ),
      Arguments.of(
        "not equals jsonb array empty string",
        """
          {"jsonbArrayField": {"$ne": ""}}""",
        DSL.condition("NOT({0} @> {1}::jsonb)", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"\"]")).or(field("jsonbArrayField").isNull())
      ),
      Arguments.of(
        "not equals jsonb array with value function",
        """
          {"jsonbArrayFieldWithValueFunction": {"$ne": "French"}}""",
        DSL.condition("NOT({0} @> jsonb_build_array({1}::text))", field("foo(valueGetter)").cast(JSONB.class), field("lower(:value)", String.class, param("value", "French"))).or(field("foo(valueGetter)").isNull())
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
        "greater than date-time (no time component)",
        """
          {"field4": {"$gt": "2023-06-02"}}""",
        field("field4").greaterOrEqual("2023-06-03T00:00:00.000")
      ),
      Arguments.of(
        "greater than date-time (with time component)",
        """
          {"field4": {"$gt": "2023-06-02T04:00:00.000"}}""",
        field("field4").greaterOrEqual("2023-06-03T04:00:00.000")
      ),
      Arguments.of(
        "greater than date",
        """
          {"field6": {"$gt": "2023-06-02"}}""",
        field("field6").greaterThan("2023-06-02")
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
        "greater than or equal to date-time (no time component)",
        """
          {"field4": {"$gte": "2023-06-02"}}""",
        field("field4").greaterOrEqual("2023-06-02T00:00:00.000")
      ),
      Arguments.of(
        "greater than or equal to date-time (with time component)",
        """
          {"field4": {"$gte": "2023-06-02T05:30:00.000"}}""",
        field("field4").greaterOrEqual("2023-06-02T05:30:00.000")
      ),
      Arguments.of(
        "greater than or equal to date",
        """
          {"field6": {"$gte": "2023-06-02"}}""",
        field("field6").greaterOrEqual("2023-06-02")
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
        "less than date-time (no time component)",
        """
          {"field4": {"$lt": "2023-06-02"}}""",
        field("field4").lessThan("2023-06-02T00:00:00.000")
      ),
      Arguments.of(
        "less than date-time (with time component)",
        """
          {"field4": {"$lt": "2023-06-02T06:00:00.000"}}""",
        field("field4").lessThan("2023-06-02T06:00:00.000")
      ),
      Arguments.of(
        "less than date",
        """
          {"field6": {"$lt": "2023-06-02"}}""",
        field("field6").lessThan("2023-06-02")
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
        "less than or equal to date-time (no time component)",
        """
          {"field4": {"$lte": "2023-06-02"}}""",
        field("field4").lessThan("2023-06-03T00:00:00.000")
      ),
      Arguments.of(
        "less than or equal to date-time (with time component)",
        """
          {"field4": {"$lte": "2023-06-02T07:00:00.000"}}""",
        field("field4").lessThan("2023-06-03T07:00:00.000")
      ),
      Arguments.of(
        "less than or equal to date",
        """
          {"field6": {"$lte": "2023-06-02"}}""",
        field("field6").lessOrEqual("2023-06-02")
      ),
      Arguments.of(
        "regex",
        """
          {"field1": {"$regex": "some_text"}}""",
        condition("{0} ~* {1}", field("field1"), val("some_text"))
      ),
      Arguments.of(
        "regex array string",
        """
          {"arrayField": {"$regex": "value1"}}""",
        condition("exists (select 1 from unnest({0}) where unnest ~* {1})", field("arrayField"), "value1")
      ),
      Arguments.of(
        "regex jsonb array string",
        """
          {"jsonbArrayField": {"$regex": "value1"}}""",
        condition("exists (select 1 from jsonb_array_elements_text({0}) as elem where elem ~* {1})", field("jsonbArrayField").cast(JSONB.class), "value1")
      ),
      Arguments.of(
        "regex",
        """
          {"fieldWithAValueFunction": {"$regex": "some_text"}}""",
        condition("{0} ~* {1}", field("fieldWithAValueFunction"), field("upper(:value)", String.class, param("value", "some_text")))
      ),
      Arguments.of(
        "starts_with",
        """
          {"field1": {"$starts_with": "prefix"}}""",
        field("field1").startsWithIgnoreCase("prefix")
      ),
      Arguments.of(
        "contains",
        """
          {"field1": {"$contains": "substring"}}""",
        field("field1").containsIgnoreCase("substring")
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
        "in list of invalid open UUID",
        """
          {"openUUIDField": {"$in": ["invalid-uuid", "invalid-uuid-2"]}}""",
        cast(field("openUUIDField"), UUID.class)
          .eq(cast(null, UUID.class))
          .or(cast(field("openUUIDField"), UUID.class).eq(cast(null, UUID.class)))
      ),
      Arguments.of(
        "in list of invalid ranged UUID",
        """
          {"rangedUUIDField": {"$in": ["invalid-uuid", "invalid-uuid-2"]}}""",
        cast(field("rangedUUIDField"), UUID.class)
          .eq(cast(null, UUID.class))
          .or(cast(field("rangedUUIDField"), UUID.class).eq(cast(null, UUID.class)))
      ),
      Arguments.of(
        "not in list of invalid open UUID",
        """
          {"openUUIDField": {"$nin": ["invalid-uuid", "invalid-uuid-2"]}}""",
        or(
          trueCondition().and(trueCondition()),
          field("openUUIDField").isNull()
        )
      ),
      Arguments.of(
        "not in list of invalid ranged UUID",
        """
          {"rangedUUIDField": {"$nin": ["invalid-uuid", "invalid-uuid-2"]}}""",
        or(
          trueCondition().and(trueCondition()),
          field("rangedUUIDField").isNull()
        )
      ),
      Arguments.of(
        "in list of partially invalid ranged UUID",
        """
          {"rangedUUIDField": {"$in": ["69939c9a-aa96-440a-a873-3b48f3f4f608", "invalid-uuid-2"]}}""",
        cast(field("rangedUUIDField"), UUID.class).eq(cast(inline(UUID.fromString("69939c9a-aa96-440a-a873-3b48f3f4f608")), UUID.class))
          .or(cast(field("rangedUUIDField"), UUID.class).eq(cast(null, UUID.class)))
      ),
      Arguments.of(
        "in list of partially invalid open UUID",
        """
          {"openUUIDField": {"$in": ["69939c9a-aa96-440a-a873-3b48f3f4f608", "invalid-uuid-2"]}}""",
        cast(field("openUUIDField"), UUID.class).eq(cast(inline(UUID.fromString("69939c9a-aa96-440a-a873-3b48f3f4f608")), UUID.class))
          .or(cast(field("openUUIDField"), UUID.class).eq(cast(null, UUID.class)))
      ),
      Arguments.of(
        "in list array string",
        """
          {"arrayField": {"$in": ["value1", "value2"]}}""",
        arrayOverlap(cast(field("arrayField"), String[].class), cast(array("value1", "value2"), String[].class))
      ),
      Arguments.of(
        "in list array numeric",
        """
          {"arrayField": {"$in": [123, 456]}}""",
        arrayOverlap(cast(field("arrayField"), String[].class), cast(array(123, 456), String[].class))
      ),
      Arguments.of(
        "in list array boolean",
        """
          {"arrayField": {"$in": [true, false]}}""",
        arrayOverlap(cast(field("arrayField"), String[].class), cast(array(true, false), String[].class))
      ),
      Arguments.of(
        "in list jsonb array string",
        """
          {"jsonbArrayField": {"$in": ["value1", "value2"]}}""",
        or(
          DSL.condition("{0} @> {1}::jsonb", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"value1\"]")),
          DSL.condition("{0} @> {1}::jsonb", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"value2\"]"))
        )
      ),
      Arguments.of(
        "in list jsonb array numeric",
        """
          {"jsonbArrayField": {"$in": [123, 456]}}""",
        or(
          DSL.condition("{0} @> {1}::jsonb", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"123\"]")),
          DSL.condition("{0} @> {1}::jsonb", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"456\"]"))
        )
      ),
      Arguments.of(
        "in list jsonb array boolean",
        """
          {"jsonbArrayField": {"$in": [true, false]}}""",
        or(
          DSL.condition("{0} @> {1}::jsonb", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"true\"]")),
          DSL.condition("{0} @> {1}::jsonb", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"false\"]"))
        )
      ),
      Arguments.of(
        "not in list ranged UUID",
        """
          {"rangedUUIDField": {"$nin": ["69939c9a-aa96-440a-a873-3b48f3f4f608", "69939c9a-aa96-440a-a873-3b48f3f4f602"]}}""",
        or(
          cast(field("rangedUUIDField"), UUID.class).ne(cast(inline(UUID.fromString("69939c9a-aa96-440a-a873-3b48f3f4f608")), UUID.class)).
            and(cast(field("rangedUUIDField"), UUID.class).ne(cast(inline(UUID.fromString("69939c9a-aa96-440a-a873-3b48f3f4f602")), UUID.class))),
          field("rangedUUIDField").isNull()
        )
      ),
      Arguments.of(
        "not in list open UUID",
        """
          {"openUUIDField": {"$nin": ["69939c9a-aa96-440a-a873-3b48f3f4f608", "invalid-uuid-2"]}}""",
        or(
          cast(field("openUUIDField"), UUID.class).ne(cast(inline(UUID.fromString("69939c9a-aa96-440a-a873-3b48f3f4f608")), UUID.class))
            .and(trueCondition),
          field("openUUIDField").isNull()
        )
      ),
      Arguments.of(
        "validated field",
        """
          {"validatedField": {"$eq": "69939c9a-aa96-440a-a873-3b48f3f4f608"}}""",
        (field("validatedField").isNull().or(field("validatedField").likeRegex(UUID_REGEX))).and(cast(field("validatedField"), UUID.class).eq(cast("69939c9a-aa96-440a-a873-3b48f3f4f608", UUID.class)))
      ),
      Arguments.of(
        "complex condition 1",
        """
          {
            "$and": [
              {"rangedUUIDField": {"$eq": "invalid-uuid-2"}},
              {"field2": {"$eq": true}},
              {"field5": {"$lte": 3}},
              {"field2": {"$in": [false, true]}},
              {"field5": {"$ne": 5}},
              {"field5": {"$gt": 9}},
              {"field3": {"$nin": ["value1", "value2"]}}
            ]
          }""",
        (cast(field("rangedUUIDField"), UUID.class).eq(cast(null, UUID.class)))
          .and(field("field2").eq(true))
          .and(field("field5").lessOrEqual(3))
          .and(
            or(
              field("field2").eq(false),
              field("field2").eq(true)
            )
          )
          .and(field("field5").notEqual(5).or(field("field5").isNull()))
          .and(field("field5").greaterThan(9))
          .and(
            or(
              and(
                field("field3").notEqualIgnoreCase("value1"),
                field("field3").notEqualIgnoreCase("value2")
              ),
              field("field3").isNull()
            )
          )
      ),
      Arguments.of(
        "complex condition 2",
        """
          {
            "$and": [
              {"openUUIDField": {"$eq": "invalid-uuid-2"}},
              {"field2": {"$eq": true}},
              {"field5": {"$lte": 3}},
              {"field2": {"$in": [false, true]}},
              {"field5": {"$ne": 5}},
              {"field5": {"$gt": 9}},
              {"field3": {"$nin": ["value1", "value2"]}}
            ]
          }""",
        (cast(field("openUUIDField"), UUID.class).eq(cast(null, UUID.class)))
          .and(field("field2").eq(true))
          .and(field("field5").lessOrEqual(3))
          .and(
            or(
              field("field2").eq(false),
              field("field2").eq(true)
            )
          )
          .and(field("field5").notEqual(5).or(field("field5").isNull()))
          .and(field("field5").greaterThan(9))
          .and(
            or(
              and(
                field("field3").notEqualIgnoreCase("value1"),
                field("field3").notEqualIgnoreCase("value2")
              ),
              field("field3").isNull()
            )
          )
      ),
      Arguments.of(
        "not in list",
        """
          {"field1": {"$nin": ["value1", 2, true]}}""",
        or(
          and(
            field("field1").notEqualIgnoreCase("value1"),
            field("field1").notEqual(2),
            field("field1").notEqual(true)
          ),
          field("field1").isNull()
        )
      ),
      Arguments.of(
        "contains all for jsonb array (using $and with $eq)",
        """
          {"$and": [
            {"jsonbArrayField": {"$eq": "value1"}},
            {"jsonbArrayField": {"$eq": "value2"}}
          ]}""",
        DSL.and(
          DSL.condition("{0} @> {1}::jsonb", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"value1\"]")),
          DSL.condition("{0} @> {1}::jsonb", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"value2\"]"))
        )
      ),
      Arguments.of(
        "not contains all for jsonb array (using $and with $ne)",
        """
          {"$and": [
            {"jsonbArrayField": {"$ne": "value1"}},
            {"jsonbArrayField": {"$ne": "value2"}}
          ]}""",
        DSL.and(
          DSL.condition("NOT({0} @> {1}::jsonb)", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"value1\"]")).or(field("jsonbArrayField").isNull()),
          DSL.condition("NOT({0} @> {1}::jsonb)", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"value2\"]")).or(field("jsonbArrayField").isNull())
        )
      ),
      Arguments.of(
        "eq string for array field",
        """
          {"arrayField": {"$eq": "Some vALUE"}}""",
        arrayOverlap(cast(field("arrayField"), String[].class), cast(array("Some vALUE"), String[].class))
      ),
      Arguments.of(
        "contains all numeric (using $and with $eq)",
        """
          {"$and": [
            {"arrayField": {"$eq": 10}}
          ]}""",
        arrayOverlap(cast(field("arrayField"), String[].class), cast(array(10), String[].class))
      ),

      Arguments.of(
        "not contains all string (using $and with $ne)",
        """
          {"$and": [
            {"arrayField": {"$ne": "Some vALUE"}}
          ]}""",
        not(arrayOverlap(cast(field("arrayField"), String[].class), cast(array("Some vALUE"), String[].class))).or(field("arrayField").isNull())
      ),
      Arguments.of(
        "not contains all numeric (using $and with $ne)",
        """
          {"$and": [
            {"arrayField": {"$ne": 10}}
          ]}""",
        not(arrayOverlap(cast(field("arrayField"), String[].class), cast(array(10), String[].class))).or(field("arrayField").isNull())
      ),

      Arguments.of(
        "array field in string",
        """
          {"arrayField": {"$in": ["Some vALUE"]}}""",
        arrayOverlap(cast(field("arrayField"), String[].class), cast(array("Some vALUE"), String[].class))
      ),
      Arguments.of(
        "array field in numeric",
        """
          {"arrayField": {"$in": [10]}}""",
        arrayOverlap(cast(field("arrayField"), String[].class), cast(array(10), String[].class))
      ),

      Arguments.of(
        "array field nin string",
        """
          {"arrayField": {"$nin": ["Some vALUE"]}}""",
        or(
          not(arrayOverlap(cast(field("arrayField"), String[].class), cast(array("Some vALUE"), String[].class))),
          field("arrayField").isNull()
        )
      ),
      Arguments.of(
        "array field nin numeric",
        """
          {"arrayField": {"$nin": [10]}}""",
        or(
          not(arrayOverlap(cast(field("arrayField"), String[].class), cast(array(10), String[].class))),
          field("arrayField").isNull()
        )
      ),
      Arguments.of(
        "in for jsonb array",
        """
          {"jsonbArrayField": {"$in": ["value1", "value2"]}}""",
        DSL.or(
          DSL.condition("{0} @> {1}::jsonb", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"value1\"]")),
          DSL.condition("{0} @> {1}::jsonb", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"value2\"]"))
        )
      ),
      Arguments.of(
        "nin for jsonb array",
        """
          {"jsonbArrayField": {"$nin": ["value1", "value2"]}}""",
        or(
          and(
            DSL.condition("NOT({0} @> {1}::jsonb)", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"value1\"]")),
            DSL.condition("NOT({0} @> {1}::jsonb)", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"value2\"]"))
          ),
          field("jsonbArrayField").isNull()
        )
      ),

      Arguments.of(
        "complex condition 3",
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
          .and(field("field5").notEqual(5).or(field("field5").isNull()))
          .and(field("field5").greaterThan(9))
          .and(
            or(
              and(
                field("field3").notEqualIgnoreCase("value1"),
                field("field3").notEqualIgnoreCase("value2")
              ),
              field("field3").isNull()
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
        or(
          and(
            field("fieldWithAValueFunction").notEqualIgnoreCase(field("upper(:value)", String.class, param("value", "value1"))),
            field("fieldWithAValueFunction").notEqual(field("upper(:value)", String.class, param("value", 2))),
            field("fieldWithAValueFunction").notEqual(field("upper(:value)", String.class, param("value", true)))
          ),
          field("fieldWithAValueFunction").isNull()
        )
      ),

      Arguments.of(
        "eq conditions combined with booleanAnd on a field with a filter value getter and a value function",
        """
            {
              "$and": [
                { "arrayFieldWithValueFunction": { "$eq": "value1" } },
                { "arrayFieldWithValueFunction": { "$eq": "value2" } }
              ]
            }
          """,
        DSL.and(
          arrayOverlap(cast(field("foo(valueGetter)"), String[].class), cast(array(field("foo(:value)", String.class, param("value", "value1"))), String[].class)),
          arrayOverlap(cast(field("foo(valueGetter)"), String[].class), cast(array(field("foo(:value)", String.class, param("value", "value2"))), String[].class))
        )
      ),


      Arguments.of(
        "not contains all condition on a field with a filter value getter and a value function",
        """
          {
            "$and": [
              { "arrayFieldWithValueFunction": { "$ne": 10 } },
              { "arrayFieldWithValueFunction": { "$ne": 20 } }
            ]
          }
          """,
        DSL.and(
          not(arrayOverlap(cast(field("foo(valueGetter)"), String[].class), cast(array(field("foo(:value)", Integer.class, param("value", 10))), String[].class))).or(field("foo(valueGetter)").isNull()),
          not(arrayOverlap(cast(field("foo(valueGetter)"), String[].class), cast(array(field("foo(:value)", Integer.class, param("value", 20))), String[].class))).or(field("foo(valueGetter)").isNull())
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
        field("field5").isNull().not()
      ),
      Arguments.of(
        "empty string",
        """
          {"field1": {"$empty": true}}""",
        field("field1").isNull().or(cast(field("field1"), String.class).eq(""))
      ),
      Arguments.of(
        "not empty string",
        """
          {"field1": {"$empty": false}}""",
        (field("field1").isNull().or(cast(field("field1"), String.class).eq(""))).not()
      ),
      Arguments.of(
        "empty array",
        """
          {"arrayField": {"$empty": true}}""",
        field("arrayField").isNull()
          .or(cardinality(cast(field("arrayField"), String[].class)).eq(0))
          .or(
            exists(
              selectOne()
                .from(unnest(cast(field("arrayField"), String[].class)).as("elem", "value"))
                .where(field(name("value")).isNull().or(falseCondition()))
            )
          )
      ),
      Arguments.of(
        "not empty array",
        """
          {"arrayField": {"$empty": false}}""",
        (field("arrayField").isNull()
          .or(cardinality(cast(field("arrayField"), String[].class)).eq(0))
          .or(
            exists(
              selectOne()
                .from(unnest(cast(field("arrayField"), String[].class)).as("elem", "value"))
                .where(field(name("value")).isNull().or(falseCondition()))
            )
          )).not()
      ),
      Arguments.of(
        "empty JSONB array",
        """
          {"jsonbArrayField": {"$empty": true}}""",
        field("jsonbArrayField").isNull()
          .or(field("jsonb_typeof({0})", String.class, field("jsonbArrayField")).eq("null"))
          .or(
            field("jsonb_typeof({0})", String.class, field("jsonbArrayField")).eq("array")
              .and(
                field("jsonb_array_length({0})", Integer.class, field("jsonbArrayField")).eq(0)
                  .or(
                    exists(
                      selectOne()
                        .from(
                          table("jsonb_array_elements({0})", field("jsonbArrayField"))
                            .as("elem", "value")
                        )
                        .where(field("({0})::text", String.class, field(name("value"))).eq("null").or(falseCondition()))
                    )
                  )
              )
          )
      ),
      Arguments.of(
        "not empty JSONB array",
        """
          {"jsonbArrayField": {"$empty": false}}""",
        not(
          field("jsonbArrayField").isNull()
            .or(field("jsonb_typeof({0})", String.class, field("jsonbArrayField")).eq("null"))
            .or(
              field("jsonb_typeof({0})", String.class, field("jsonbArrayField")).eq("array")
                .and(
                  field("jsonb_array_length({0})", Integer.class, field("jsonbArrayField")).eq(0)
                    .or(
                      exists(
                        selectOne()
                          .from(
                            table("jsonb_array_elements({0})", field("jsonbArrayField"))
                              .as("elem", "value")
                          )
                          .where(field("({0})::text", String.class, field(name("value"))).eq("null").or(falseCondition()))
                      )
                    )
                )
            )
        )
      ),
      Arguments.of(
        "empty JSONB string array",
        """
          {"jsonbStringArray": {"$empty": true}}""",
        field("jsonbStringArray").isNull()
          .or(field("jsonb_typeof({0})", String.class, field("jsonbStringArray")).eq("null"))
          .or(
            field("jsonb_typeof({0})", String.class, field("jsonbStringArray")).eq("array")
              .and(
                field("jsonb_array_length({0})", Integer.class, field("jsonbStringArray")).eq(0)
                  .or(
                    exists(
                      selectOne()
                        .from(
                          table("jsonb_array_elements({0})", field("jsonbStringArray"))
                            .as("elem", "value")
                        )
                        .where(
                          field("({0})::text", String.class, field(name("value"))).eq("null")
                            .or(field("({0})::text", String.class, field(name("value"))).eq("\"\""))
                        )
                    )
                  )
              )
          )
      ),
      Arguments.of(
        "empty nested string",
        """
          {"stringArrayField[*]->stringSubField": {"$empty": true}}""",
        field("stringSubField").isNull()
          .or(cardinality(cast(field("stringSubField"), String[].class)).eq(0))
          .or(
            exists(
              selectOne()
                .from(unnest(cast(field("stringSubField"), String[].class)).as("elem", "value"))
                .where(
                  field(name("value")).isNull()
                    .or(field(name("value"), String.class).eq(""))
                )
            )
          )
      ),
      Arguments.of(
        "not empty nested string",
        """
          {"stringArrayField[*]->stringSubField": {"$empty": false}}""",
        not(
          field("stringSubField").isNull()
            .or(cardinality(cast(field("stringSubField"), String[].class)).eq(0))
            .or(
              exists(
                selectOne()
                  .from(unnest(cast(field("stringSubField"), String[].class)).as("elem", "value"))
                  .where(
                    field(name("value")).isNull()
                      .or(field(name("value"), String.class).eq(""))
                  )
              )
            )
        )
      ),
      Arguments.of(
        "not in list array string",
        """
          {"arrayField": {"$nin": ["value1", "value2"]}}""",
        or(
          and(
            not(arrayOverlap(cast(field("arrayField"), String[].class), cast(array("value1"), String[].class))),
            not(arrayOverlap(cast(field("arrayField"), String[].class), cast(array("value2"), String[].class)))
          ),
          field("arrayField").isNull()
        )
      ),
      Arguments.of(
        "not in list array numeric",
        """
          {"arrayField": {"$nin": [123, 456]}}""",
        or(
          and(
            not(arrayOverlap(cast(field("arrayField"), String[].class), cast(array(123), String[].class))),
            not(arrayOverlap(cast(field("arrayField"), String[].class), cast(array(456), String[].class)))
          ),
          field("arrayField").isNull()
        )
      ),
      Arguments.of(
        "not in list array boolean",
        """
          {"arrayField": {"$nin": [true, false]}}""",
        or(
          and(
            not(arrayOverlap(cast(field("arrayField"), String[].class), cast(array(true), String[].class))),
            not(arrayOverlap(cast(field("arrayField"), String[].class), cast(array(false), String[].class)))
          ),
          field("arrayField").isNull()
        )
      ),
      Arguments.of(
        "not in list jsonb array string",
        """
          {"jsonbArrayField": {"$nin": ["value1", "value2"]}}""",
        or(
          and(
            DSL.condition("NOT({0} @> {1}::jsonb)", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"value1\"]")),
            DSL.condition("NOT({0} @> {1}::jsonb)", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"value2\"]"))
          ),
          field("jsonbArrayField").isNull()
        )
      ),
      Arguments.of(
        "not in list jsonb array numeric",
        """
          {"jsonbArrayField": {"$nin": [123, 456]}}""",
        or(
          and(
            DSL.condition("NOT({0} @> {1}::jsonb)", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"123\"]")),
            DSL.condition("NOT({0} @> {1}::jsonb)", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"456\"]"))
          ),
          field("jsonbArrayField").isNull()
        )
      ),
      Arguments.of(
        "not in list jsonb array boolean",
        """
          {"jsonbArrayField": {"$nin": [true, false]}}""",
        or(
          and(
            DSL.condition("NOT({0} @> {1}::jsonb)", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"true\"]")),
            DSL.condition("NOT({0} @> {1}::jsonb)", field("jsonbArrayField").cast(JSONB.class), DSL.inline("[\"false\"]"))
          ),
          field("jsonbArrayField").isNull()
        )
      ),
      Arguments.of(
        "starts_with array field",
        """
          {"arrayField": {"$starts_with": "prefix"}}""",
        condition("exists (select 1 from unnest({0}) where unnest like {1})", field("arrayField"), DSL.concat("prefix", "%"))
      ),
      Arguments.of(
        "starts_with array field with value function",
        """
          {"arrayFieldWithValueFunction": {"$starts_with": "prefix"}}""",
        condition("exists (select 1 from unnest({0}) where unnest like {1})", field("foo(valueGetter)"), DSL.concat(field("foo(:value)", String.class, param("value", "prefix")), "%"))
      ),
      Arguments.of(
        "starts_with jsonb array field",
        """
          {"jsonbArrayField": {"$starts_with": "prefix"}}""",
        condition("exists (select 1 from jsonb_array_elements_text({0}) as elem where elem like {1})", field("jsonbArrayField").cast(JSONB.class), DSL.concat("prefix", "%"))
      ),
      Arguments.of(
        "starts_with jsonb array field with value function",
        """
          {"jsonbArrayFieldWithValueFunction": {"$starts_with": "prefix"}}""",
        condition("exists (select 1 from jsonb_array_elements_text({0}) as elem where elem like {1})", field("foo(valueGetter)").cast(JSONB.class), DSL.concat(field("lower(:value)", String.class, param("value", "prefix")), "%"))
      ),
      Arguments.of(
        "starts_with array field with special characters",
        """
          {"arrayField": {"$starts_with": "test & special! chars"}}""",
        condition("exists (select 1 from unnest({0}) where unnest like {1})", field("arrayField"), DSL.concat(val("test & special! chars"), "%"))
      ),
      Arguments.of(
        "starts_with jsonb array field with special characters",
        """
          {"jsonbArrayField": {"$starts_with": "test & special! chars"}}""",
        condition("exists (select 1 from jsonb_array_elements_text({0}) as elem where elem like {1})", field("jsonbArrayField").cast(JSONB.class), DSL.concat(val("test & special! chars"), "%"))
      ),
      Arguments.of(
        "eq nested array-object field string",
        """
          {"nested[*]->string": {"$eq": "foo bar"}}""",
        arrayOverlap(cast(field("nestStr"), String[].class), cast(array("foo bar"), String[].class))
      ),
      Arguments.of(
        "eq nested array-object field ruuid",
        """
          {"nested[*]->ruuid": {"$eq": "caf7d3db-bc1a-5551-b54d-360944585605"}}""",
        arrayOverlap(cast(field("nestRUuid"), String[].class), cast(array(cast(UUID.fromString("caf7d3db-bc1a-5551-b54d-360944585605"), UUID.class)), String[].class))
      ),
      Arguments.of(
        "eq nested array-object field invalid ranged uuid",
        """
          {"nested[*]->ruuid": {"$eq": "invalid"}}""",
        arrayOverlap(cast(field("nestRUuid"), String[].class), cast(array(cast(null, UUID.class)), String[].class))
      ),
      Arguments.of(
        "eq nested array-object field open uuid",
        """
          {"nested[*]->ouuid": {"$eq": "caf7d3db-bc1a-5551-b54d-360944585605"}}""",
        arrayOverlap(cast(field("nestOUuid"), String[].class), cast(array(cast(UUID.fromString("caf7d3db-bc1a-5551-b54d-360944585605"), UUID.class)), String[].class))
      ),
      Arguments.of(
        "eq nested array-object field invalid open uuid",
        """
          {"nested[*]->ouuid": {"$eq": "invalid"}}""",
        arrayOverlap(cast(field("nestOUuid"), String[].class), cast(array(cast(null, UUID.class)), String[].class))
      ),
      Arguments.of(
        "in nested array-object field string",
        """
          {"nested[*]->string": {"$in": ["foo", "bar"]}}""",
        arrayOverlap(cast(field("nestStr"), String[].class), cast(array("foo", "bar"), String[].class))
      ),
      Arguments.of(
        "in nested array-object field partially valid ranged uuid",
        """
          {"nested[*]->ruuid": {"$in": ["df3f3e8a-8694-59ad-ad52-3671613d02dc", "invalid"]}}""",
        arrayOverlap(cast(field("nestRUuid"), String[].class), cast(array(
          cast(UUID.fromString("df3f3e8a-8694-59ad-ad52-3671613d02dc"), UUID.class),
          cast(null, UUID.class)
        ), String[].class))
      ),
      Arguments.of(
        "in nested array-object field partially valid open uuid",
        """
          {"nested[*]->ouuid": {"$in": ["df3f3e8a-8694-59ad-ad52-3671613d02dc", "invalid"]}}""",
        arrayOverlap(cast(field("nestOUuid"), String[].class), cast(array(
          cast(UUID.fromString("df3f3e8a-8694-59ad-ad52-3671613d02dc"), UUID.class),
          cast(null, UUID.class)
        ), String[].class))
      ),
      Arguments.of(
        "equals string with matching default value",
        """
          {"stringDefaultValue": {"$eq": "default"}}""",
        field("stringDefaultValue").equalIgnoreCase("default").or(field("stringDefaultValue").isNull())
      ),
      Arguments.of(
        "equals string with non-matching default value",
        """
          {"stringDefaultValue": {"$eq": "something else"}}""",
        field("stringDefaultValue").equalIgnoreCase("something else")
      ),
      Arguments.of(
        "not equals string with matching default value",
        """
          {"stringDefaultValue": {"$ne": "default"}}""",
        field("stringDefaultValue").notEqualIgnoreCase("default")
      ),
      Arguments.of(
        "not equals string with non-matching default value",
        """
          {"stringDefaultValue": {"$ne": "something else"}}""",
        field("stringDefaultValue").notEqualIgnoreCase("something else").or(field("stringDefaultValue").isNull())
      ),
      Arguments.of(
        "in list with matching default value",
        """
          {"stringDefaultValue": {"$in": ["default", "another value"]}}""",
        or(
          field("stringDefaultValue").equalIgnoreCase("default"),
          field("stringDefaultValue").equalIgnoreCase("another value"),
          field("stringDefaultValue").isNull()
        )
      ),
      Arguments.of(
        "in list with non-matching default value",
        """
          {"stringDefaultValue": {"$in": ["a value", "another value"]}}""",
        or(
          field("stringDefaultValue").equalIgnoreCase("a value"),
          field("stringDefaultValue").equalIgnoreCase("another value")
        )
      ),
      Arguments.of(
        "not in list with matching default value",
        """
          {"stringDefaultValue": {"$nin": ["default", "another value"]}}""",
        and(
          field("stringDefaultValue").notEqualIgnoreCase("default"),
          field("stringDefaultValue").notEqualIgnoreCase("another value")
        )
      ),
      Arguments.of(
        "not in list with non-matching default value",
        """
          {"stringDefaultValue": {"$nin": ["a value", "another value"]}}""",
        or(
          and(
            field("stringDefaultValue").notEqualIgnoreCase("a value"),
            field("stringDefaultValue").notEqualIgnoreCase("another value")
          ),
          field("stringDefaultValue").isNull()
        )
      ),
      Arguments.of(
        "starts with string with matching default value",
        """
          {"stringDefaultValue": {"$starts_with": "default"}}""",
        field("stringDefaultValue").startsWithIgnoreCase("default").or(field("stringDefaultValue").isNull())
      ),
      Arguments.of(
        "starts with string with non-matching default value",
        """
          {"stringDefaultValue": {"$starts_with": "something else"}}""",
        field("stringDefaultValue").startsWithIgnoreCase("something else")
      ),
      Arguments.of(
        "contains string with matching default value",
        """
          {"stringDefaultValue": {"$contains": "default"}}""",
        field("stringDefaultValue").containsIgnoreCase("default").or(field("stringDefaultValue").isNull())
      ),
      Arguments.of(
        "contains string with non-matching default value",
        """
          {"stringDefaultValue": {"$contains": "something else"}}""",
        field("stringDefaultValue").containsIgnoreCase("something else")
      ),

      Arguments.of(
        "equals int with matching default value",
        """
          {"numberDefaultValue": {"$eq": 10}}""",
        field("numberDefaultValue").eq(10).or(field("numberDefaultValue").isNull())
      ),
      Arguments.of(
        "equals int with non-matching default value",
        """
          {"numberDefaultValue": {"$eq": 9}}""",
        field("numberDefaultValue").eq(9)
      ),
      Arguments.of(
        "not equals int with matching default value",
        """
          {"numberDefaultValue": {"$ne": 10}}""",
        field("numberDefaultValue").ne(10)
      ),
      Arguments.of(
        "not equals int with non-matching default value",
        """
          {"numberDefaultValue": {"$ne": 9}}""",
        field("numberDefaultValue").ne(9).or(field("numberDefaultValue").isNull())
      ),
      Arguments.of(
        "greater than int with matching default value",
        """
          {"numberDefaultValue": {"$gt": 9}}""",
        field("numberDefaultValue").greaterThan(9).or(field("numberDefaultValue").isNull())
      ),
      Arguments.of(
        "greater than int with non-matching default value",
        """
          {"numberDefaultValue": {"$gt": 11}}""",
        field("numberDefaultValue").greaterThan(11)
      ),
      Arguments.of(
        "greater than or equal int with matching default value",
        """
          {"numberDefaultValue": {"$gte": 9}}""",
        field("numberDefaultValue").greaterOrEqual(9).or(field("numberDefaultValue").isNull())
      ),
      Arguments.of(
        "greater than or equal int with non-matching default value",
        """
          {"numberDefaultValue": {"$gte": 11}}""",
        field("numberDefaultValue").greaterOrEqual(11)
      ),
      Arguments.of(
        "less than int with matching default value",
        """
          {"numberDefaultValue": {"$lt": 11}}""",
        field("numberDefaultValue").lessThan(11).or(field("numberDefaultValue").isNull())
      ),
      Arguments.of(
        "less than int with non-matching default value",
        """
          {"numberDefaultValue": {"$lt": 9}}""",
        field("numberDefaultValue").lessThan(9)
      ),
      Arguments.of(
        "less than or equal int with matching default value",
        """
          {"numberDefaultValue": {"$lte": 11}}""",
        field("numberDefaultValue").lessOrEqual(11).or(field("numberDefaultValue").isNull())
      ),
      Arguments.of(
        "less than or equal int with non-matching default value",
        """
          {"numberDefaultValue": {"$lte": 9}}""",
        field("numberDefaultValue").lessOrEqual(9)
      ),

      // The next 2 scenarios shouldn't occur in practice, but we should still ensure the default-values code handles them gracefully
      Arguments.of(
        "greater than int with invalid value",
        """
          {"numberDefaultValue": {"$gt": "invalid"}}""",
        field("numberDefaultValue").greaterThan("invalid")
      ),
      Arguments.of(
        "less than int with invalid value",
        """
          {"numberDefaultValue": {"$lt": "invalid"}}""",
        field("numberDefaultValue").lessThan("invalid")
      ),
      Arguments.of(
        "empty for int with default value",
        """
          {"numberDefaultValue": {"$empty": true}}""",
        falseCondition()
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

  @Test
  void shouldThrowExceptionForInvalidDateTimeValue() {
    String invalidDateFql = """
      {"field4": {"$eq": "03-09-2024"}}""";
    assertThrows(
      InvalidFqlException.class,
      () -> fqlToSqlConverter.getSqlCondition(invalidDateFql, entityType)
    );
  }
}
