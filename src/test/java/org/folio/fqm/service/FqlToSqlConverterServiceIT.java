package org.folio.fqm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.folio.fqm.IntegrationTestBase;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.RangedUUIDType;
import org.folio.querytool.domain.dto.StringType;
import org.folio.spring.integration.XOkapiHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@Testcontainers
class FqlToSqlConverterServiceIT extends IntegrationTestBase {

  @Test
  void valueFunctionTest() throws JsonProcessingException {

    // Given this dummy entity type with built-in mock data (including a column with a filterValueGetter and valueFunction)
    EntityType entityType = new EntityType()
      .id(UUID.randomUUID().toString())
      .name("test")
      .labelAlias("test alias")
      .root(true)
      ._private(false)
      .columns(List.of(
        new EntityTypeColumn()
          .name("id")
          .dataType(new RangedUUIDType())
          .labelAlias("id alias")
          .visibleByDefault(true)
          .valueGetter(":sourceAlias.id")
          .sourceAlias("t")
          .isIdColumn(true),
        new EntityTypeColumn()
          .name("some_column")
          .dataType(new StringType())
          .labelAlias("some column")
          .visibleByDefault(true)
          .valueGetter(":sourceAlias.some_column")
          .filterValueGetter("lower(\"left\"(:sourceAlias.some_column, 10))")
          .valueFunction("lower(\"left\"(:value, 10))")
          .sourceAlias("t")
      )).sources(List.of(
        new EntityTypeSource("db", "t")
          .target("""
              (select id, some_column, unused_column
              from (values
                     ('2af997b6-2655-459e-bdca-decbf54795ae'::uuid, 'AbCdEfGhIjKlMnOpQrStUvWxYz', 456),
                     ('e0e4233e-fea0-4834-96ac-78739a1856d3'::uuid, 'blah blah blah', 789)
                   ) as t (id, some_column, unused_column)
              )"""))
      );

    var json = new ObjectMapper().writeValueAsString(entityType);
    var sql = """
      insert into %s_mod_fqm_manager.entity_type_definition
        (id, definition) values
        (:id, :definition::json)
      """.formatted(TENANT_ID);

    NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(getDataSource());
    jdbcTemplate.update(sql, Map.of("id", UUID.fromString(entityType.getId()), "definition", json));

    // When we query for a value that only actually matches the mock data when it gets run through the valueFunction
    // and is compared against the value produced by the filterValueGetter
    String fql = """
      {
        "some_column": { "$eq": "aBcDeFgHiJ...and nothing else matters after 10 chars. Note that the capitalization is different than in the 'real' value, too" }
      }""";

    // Then we get back the 1 expected row
    given()
      .headers(getOkapiHeaders())
      .contentType("application/json")
      .when()
      .queryParams(
        "query", fql,
        "entityTypeId", entityType.getId(),
        "fields", List.of("some_column")
      )
      .get("/query")
      .then()
      .statusCode(200)
      .body(
        // Of the 2 possible results, only 1 should match, due to the filterValueGetter and valueFilter (despite the
        // query not matching what's in the DB)
        "content.size()", is(1),
        // Also, the returned value should exactly match what's in the DB, not the truncated lower-case one from the
        // filterValueGetter or valueGetter
        "content[0].some_column", is("AbCdEfGhIjKlMnOpQrStUvWxYz")
      );
  }
}
