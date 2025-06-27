package org.folio.fqm.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// tests for MigrationUtils#migrateFqlValues, split off from other test file due to complexity
class MigrationUtilsValuesTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  static List<Arguments> functionCallTestCases() {
    Predicate<String> alwaysTrue = k -> true;
    Predicate<String> alwaysFalse = k -> false;

    return List.of(
      // query, version transformation expected calls, list of field transformation calls
      Arguments.of("{}", alwaysTrue, List.of(), "{}"),
      Arguments.of("{\"_version\":\"1\"}", alwaysTrue, List.of(), "{}"),
      // basic single-field query
      Arguments.of(
        "{\"field\":{\"$eq\":\"foo\"}}",
        alwaysTrue,
        List.of(Triple.of("field", "foo", "{\"$eq\":\"foo\"}")),
        "{\"field\":{\"$eq\":\"[foo]\"}}"
      ),
      Arguments.of("{\"field\":{\"$eq\":\"foo\"}}", alwaysFalse, List.of(), "{\"field\":{\"$eq\":\"foo\"}}"),
      // multi-field query, without $and
      Arguments.of(
        """
          {
            "field1": {"$eq": "foo"},
            "field2": {"$le": "bar"},
            "field3": {"$ne": "baz"}
          }
          """,
        alwaysTrue,
        List.of(
          Triple.of("field1", "foo", "{\"$eq\":\"foo\"}"),
          Triple.of("field2", "bar", "{\"$le\":\"bar\"}"),
          Triple.of("field3", "baz", "{\"$ne\":\"baz\"}")
        ),
        """
          {
            "field1": {"$eq": "[foo]"},
            "field2": {"$le": "[bar]"},
            "field3": {"$ne": "[baz]"}
          }
          """
      ),
      // multi-field query, limited predicate, without $and
      Arguments.of(
        """
          {
            "field1": {"$eq": "foo"},
            "field2": {"$le": "bar"},
            "field3": {"$ne": "baz"}
          }
          """,
        (Predicate<String>) "field2"::equals,
        List.of(Triple.of("field2", "bar", "{\"$le\":\"bar\"}")),
        """
          {
            "field1": {"$eq": "foo"},
            "field2": {"$le": "[bar]"},
            "field3": {"$ne": "baz"}
          }
          """
      ),
      // multi-operator single-field query + multi-type
      Arguments.of(
        """
          {"field1": {
            "$le": 500,
            "$ge": 100,
            "$empty": false,
            "$eq": "foo",
            "$ne": "bar"
          }}
          """,
        alwaysTrue,
        List.of(Triple.of("field1", "foo", "{\"$eq\":\"foo\"}"), Triple.of("field1", "bar", "{\"$ne\":\"bar\"}")),
        """
          {"field1": {
            "$le": 500,
            "$ge": 100,
            "$empty": false,
            "$eq": "[foo]",
            "$ne": "[bar]"
          }}
          """
      ),
      // query with $and
      Arguments.of(
        """
          {"$and": [
            { "field": {"$ne": "foo"} },
            { "field": {"$ne": "bar"} }
          ]}
          """,
        alwaysTrue,
        List.of(Triple.of("field", "foo", "{\"$ne\":\"foo\"}"), Triple.of("field", "bar", "{\"$ne\":\"bar\"}")),
        """
          {"$and": [
            { "field": {"$ne": "[foo]"} },
            { "field": {"$ne": "[bar]"} }
          ]}
          """
      ),
      // array operators + multiple type
      Arguments.of(
        """
          {"field1": {
            "$empty": false,
            "$eq": "foo",
            "$in": ["bar", "baz", 1234]
          }}
          """,
        (Predicate<String>) "field1"::equals,
        List.of(
          Triple.of("field1", "foo", "{\"$eq\":\"foo\"}"),
          Triple.of("field1", "bar", "{\"$in\":[\"bar\",\"baz\",1234]}"),
          Triple.of("field1", "baz", "{\"$in\":[\"bar\",\"baz\",1234]}")
        ),
        """
          {"field1": {
            "$empty": false,
            "$eq": "[foo]",
            "$in": ["[bar]", "[baz]", 1234]
          }}
          """
      )
    );
  }

  @ParameterizedTest
  @MethodSource("functionCallTestCases")
  void testFunctionCalls(
    String query,
    Predicate<String> predicate,
    List<Triple<String, String, String>> fieldArguments,
    String expectedQuery
  ) throws JsonProcessingException {
    List<Triple<String, String, String>> fieldArgumentsLeftToGet = new ArrayList<>(fieldArguments);

    String actualQuery = MigrationUtils.migrateFqlValues(
      query,
        predicate,
      (String key, String value, Supplier<String> fql) -> {
        assertThat(key, is(notNullValue()));
        assertThat(value, is(notNullValue()));
        assertThat(fql.get(), is(notNullValue()));

        // re-parse JSON to compact, for test sanity
        Triple<String, String, String> actual;
        try {
          actual = Triple.of(key, value, objectMapper.readTree(fql.get()).toString());
        } catch (JsonProcessingException e) {
          throw new RuntimeException(e);
        }

        if (!fieldArgumentsLeftToGet.remove(actual)) {
          fail(
            "Unexpected field transformation call: %s -> %s (%s)".formatted(
                actual.getLeft(),
                actual.getMiddle(),
                actual.getRight()
              )
          );
        }

        return "[%s]".formatted(value);
      }
    );

    assertThat(fieldArgumentsLeftToGet, is(empty()));
    assertThat(
      ((ObjectNode) objectMapper.readTree(actualQuery)).without("_version"),
      is(objectMapper.readTree(expectedQuery))
    );
  }

  @Test
  void testRemovesWhenApplicable() throws JsonProcessingException {
    String originalQuery =
      """
      {
        "do-not-touch": {"$eq": "remove-me"},
        "field1": {"$eq": "keep-me", "$nin": ["remove-me"]},
        "field2": {"$eq": "keep-me", "$in": ["keep-me", "remove-me"]},
        "field3": {"$eq": "remove-me"},
        "field4": {"$contains_any": ["remove-me"]}
      }
      """;
    String expectedQuery =
      """
        {
          "do-not-touch": {"$eq": "remove-me"},
          "field1": {"$eq": "keep-me"},
          "field2": {"$eq": "keep-me", "$in": ["keep-me"]}
        }
        """;

    String actualQuery = MigrationUtils.migrateFqlValues(
      originalQuery,
        k -> !k.equals("do-not-touch"),
      (String key, String value, Supplier<String> fql) -> "keep-me".equals(value) ? value : null
    );

    assertThat(
      ((ObjectNode) objectMapper.readTree(actualQuery)).without("_version"),
      is(objectMapper.readTree(expectedQuery))
    );
  }
}
