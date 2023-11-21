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
          new EntityTypeColumn().name("arrayField").dataType(new ArrayType())
        )
      );
  }

  @Test
  void shouldGetJooqConditionForFqlEqualsStringCondition() {
    String fqlCondition = """
      {"field1": {"$eq": "some value"}}
      """;
    Condition expectedCondition = field("field1").equalIgnoreCase("some value");
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlEqualsNumericCondition() {
    String fqlCondition = """
      {"field1": {"$eq": 10}}
      """;
    Condition expectedCondition = field("field1").equal(10);
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlEqualsBooleanCondition() {
    String fqlCondition = """
      {"field1": {"$eq": true}}
      """;
    Condition expectedCondition = field("field1").equal(true);
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlEqualsDateAndTimeCondition() {
    String fqlCondition = """
      {"field4": {"$eq": "2023-06-02T16:11:08.766+00:00"}}
      """;
    Condition expectedCondition = field("field4").equalIgnoreCase("2023-06-02T16:11:08.766+00:00");
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlEqualsDateCondition() {
    String fqlCondition = """
      {"field4": {"$eq": "2023-06-02"}}
      """;
    Condition expectedCondition = field("field4").greaterOrEqual("2023-06-02")
      .and(field("field4").lessThan("2023-06-03"));
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlNotEqualsStringCondition() {
    String fqlCondition = """
      {"field1": {"$ne": "some value"}}
      """;
    Condition expectedCondition = field("field1").notEqualIgnoreCase("some value");
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlNotEqualsNumericCondition() {
    String fqlCondition = """
      {"field1": {"$ne": 10}}
      """;
    Condition expectedCondition = field("field1").notEqual(10);
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlNotEqualsBooleanCondition() {
    String fqlCondition = """
      {"field1": {"$ne": true}}
      """;
    Condition expectedCondition = field("field1").notEqual(true);
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlNotEqualsDateAndTimeCondition() {
    String fqlCondition = """
      {"field4": {"$ne": "2023-06-02T16:11:08.766+00:00"}}
      """;
    Condition expectedCondition = field("field4").notEqualIgnoreCase("2023-06-02T16:11:08.766+00:00");
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlNotEqualsDateCondition() {
    String fqlCondition = """
      {"field4": {"$ne": "2023-06-02"}}
      """;
    Condition expectedCondition = field("field4").greaterOrEqual("2023-06-03")
      .or(field("field4").lessThan("2023-06-02"));
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlGtCondition() {
    String fqlCondition = """
      {"field1": {"$gt": "some value"}}
      """;
    Condition expectedCondition = field("field1").greaterThan("some value");
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlGtDateCondition() {
    String fqlCondition = """
      {"field4": {"$gt": "2023-06-02"}}
      """;
    Condition expectedCondition = field("field4").greaterOrEqual("2023-06-03");
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlGteCondition() {
    String fqlCondition = """
      {"field1": {"$gte": "some value"}}
      """;
    Condition expectedCondition = field("field1").greaterOrEqual("some value");
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlGteDateCondition() {
    String fqlCondition = """
      {"field4": {"$gte": "2023-06-02"}}
      """;
    Condition expectedCondition = field("field4").greaterOrEqual("2023-06-02");
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlLtCondition() {
    String fqlCondition = """
      {"field1": {"$lt": "some value"}}
      """;
    Condition expectedCondition = field("field1").lessThan("some value");
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlLtDateCondition() {
    String fqlCondition = """
      {"field4": {"$lt": "2023-06-02"}}
      """;
    Condition expectedCondition = field("field4").lessThan("2023-06-02");
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlLteCondition() {
    String fqlCondition = """
      {"field1": {"$lte": "some value"}}
      """;
    Condition expectedCondition = field("field1").lessOrEqual("some value");
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlLteDateCondition() {
    String fqlCondition = """
      {"field4": {"$lte": "2023-06-02"}}
      """;
    Condition expectedCondition = field("field4").lessThan("2023-06-03");
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlRegExCondition() {
    String fqlCondition = """
      {"field1": {"$regex": "some_text"}}
      """;
    Condition expectedCondition = condition("{0} ~* {1}", field("field1"), val("some_text"));
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlInCondition() {
    Condition expectedCondition = or(
      field("field1").equalIgnoreCase("value1"),
      field("field1").eq(2),
      field("field1").eq(true)
    );
    String fqlCondition = """
      {"field1": {"$in": ["value1", 2, true]}}
      """;
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlNotInCondition() {
    Condition expectedCondition = and(
      field("field1").notEqualIgnoreCase("value1"),
      field("field1").notEqual(2),
      field("field1").notEqual(true)
    );
    String fqlCondition = """
      {"field1": {"$nin": ["value1", 2, true]}}
      """;
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlContainsStringCondition() {
    String fqlCondition = """
      {"arrayField": {"$contains": "some value"}}
      """;
    Condition expectedCondition = field("arrayField").containsIgnoreCase("some value");
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlContainsNumericCondition() {
    String fqlCondition = """
      {"arrayField": {"$contains": 10}}
      """;
    Condition expectedCondition = field("arrayField").contains(10);
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlNotContainsStringCondition() {
    String fqlCondition = """
      {"arrayField": {"$not_contains": "some value"}}
      """;
    Condition expectedCondition = field("arrayField").notContainsIgnoreCase("some value").or(field("arrayField").isNull());
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForFqlNotContainsNumericCondition() {
    String fqlCondition = """
      {"arrayField": {"$not_contains": 10}}
      """;
    Condition expectedCondition = field("arrayField").notContains(10).or(field("arrayField").isNull());
    Condition actualCondition = fqlToSqlConverter.getSqlCondition(fqlCondition, entityType);
    assertEquals(expectedCondition, actualCondition);
  }

  @Test
  void shouldGetJooqConditionForComplexFql() {
    String complexFql = """
      {
         "$and":[
            { "field1": { "$eq":"some value" } },
            { "field2": { "$eq":true } },
            {
              "$and":[
                  { "field3": { "$lte":3 } },
                  { "field1": { "$eq":false } }
              ]},
            { "field2":{ "$in":[ "value1", 2, true ] }},
            { "field3":{ "$ne": 5 }},
            { "field1":{ "$gt": 9 }},
            { "field2":{ "$lt": 11 }},
            { "field3":{ "$nin":[ "value1", 2, true ] }}
         ]
      }
      """;
    Condition expectedCondition = field("field1").equalIgnoreCase("some value")
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
      );

    Condition actualCondition = fqlToSqlConverter.getSqlCondition(complexFql, entityType);
    assertEquals(expectedCondition.toString(), actualCondition.toString());
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
