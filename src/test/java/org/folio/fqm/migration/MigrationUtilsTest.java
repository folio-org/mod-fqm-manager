package org.folio.fqm.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.TextNode;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.folio.fqm.exception.InvalidFqlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Log4j2
class MigrationUtilsTest {

  static List<Arguments> functionCallTestCases() {
    return List.of(
      // query, list of expected field transformation calls from migrateFql,
      //        list of field transformation calls from migrateAndReshapeFql
      Arguments.of("{}", List.of(), List.of()),
      Arguments.of("{\"_version\":\"1\"}", List.of(), List.of()),
      // basic single-field query
      Arguments.of(
        "{\"field\":{\"$eq\":\"foo\"}}",
        List.of(Pair.of("field", "{\"$eq\":\"foo\"}")),
        List.of(Triple.of("field", "$eq", "\"foo\""))
      ),
      Arguments.of(
        "{\"_version\":\"1\", \"field\":{\"$eq\":\"foo\"}}",
        List.of(Pair.of("field", "{\"$eq\":\"foo\"}")),
        List.of(Triple.of("field", "$eq", "\"foo\""))
      ),
      // multi-field query, without $and
      Arguments.of(
        """
          {
            "field1": {"$eq": "foo"},
            "field2": {"$le": "bar"},
            "field3": {"$ne": "baz"}
          }
          """,
        List.of(
          Pair.of("field1", "{\"$eq\":\"foo\"}"),
          Pair.of("field2", "{\"$le\":\"bar\"}"),
          Pair.of("field3", "{\"$ne\":\"baz\"}")
        ),
        List.of(
          Triple.of("field1", "$eq", "\"foo\""),
          Triple.of("field2", "$le", "\"bar\""),
          Triple.of("field3", "$ne", "\"baz\"")
        )
      ),
      Arguments.of(
        """
          {
            "_version": "newest and coolest",
            "field1": {"$eq": "foo"},
            "field2": {"$le": "bar"},
            "field3": {"$ne": "baz"}
          }
          """,
        List.of(
          Pair.of("field1", "{\"$eq\":\"foo\"}"),
          Pair.of("field2", "{\"$le\":\"bar\"}"),
          Pair.of("field3", "{\"$ne\":\"baz\"}")
        ),
        List.of(
          Triple.of("field1", "$eq", "\"foo\""),
          Triple.of("field2", "$le", "\"bar\""),
          Triple.of("field3", "$ne", "\"baz\"")
        )
      ),
      // multi-operator single-field query
      Arguments.of(
        """
          {"field1": {
            "$le": 500,
            "$ge": 100
          }}
          """,
        List.of(Pair.of("field1", "{\"$le\":500,\"$ge\":100}")),
        List.of(Triple.of("field1", "$le", "500"), Triple.of("field1", "$ge", "100"))
      ),
      // query with $and
      Arguments.of(
        """
          {"$and": [
            { "field": {"$ne": "foo"} },
            { "field": {"$ne": "bar"} }
          ]}
          """,
        List.of(Pair.of("field", "{\"$ne\":\"foo\"}"), Pair.of("field", "{\"$ne\":\"bar\"}")),
        List.of(Triple.of("field", "$ne", "\"foo\""), Triple.of("field", "$ne", "\"bar\""))
      ),
      // putting everything together
      Arguments.of(
        """
          {
            "_version": "1",
            "field1": {"$eq": "foo"},
            "field2": {"$le": "bar"},
            "field3": {
              "$ne": "baz",
              "$gt": 100
            },
            "$and": [
              {"field4": {"$ne": "foo"}},
              {"field5": {
                "$ne": "bar",
                "$lt": 100
              }}
            ]
          }
          """,
        List.of(
          Pair.of("field1", "{\"$eq\":\"foo\"}"),
          Pair.of("field2", "{\"$le\":\"bar\"}"),
          Pair.of("field3", "{\"$ne\":\"baz\",\"$gt\":100}"),
          Pair.of("field4", "{\"$ne\":\"foo\"}"),
          Pair.of("field5", "{\"$ne\":\"bar\",\"$lt\":100}")
        ),
        List.of(
          Triple.of("field1", "$eq", "\"foo\""),
          Triple.of("field2", "$le", "\"bar\""),
          Triple.of("field3", "$ne", "\"baz\""),
          Triple.of("field3", "$gt", "100"),
          Triple.of("field4", "$ne", "\"foo\""),
          Triple.of("field5", "$ne", "\"bar\""),
          Triple.of("field5", "$lt", "100")
        )
      )
    );
  }

  @ParameterizedTest
  @MethodSource("functionCallTestCases")
  void testMigrateFqlFunctionCalls(
    String query,
    List<Pair<String, String>> fieldArguments,
    List<Triple<String, String, String>> unused
  ) {
    List<Pair<String, String>> fieldArgumentsLeftToGet = new ArrayList<>(fieldArguments);

    MigrationUtils.migrateFql(
      query,
      (node, field, value) -> {
        assertThat(node, is(notNullValue()));
        assertThat(field, is(notNullValue()));
        assertThat(value, is(notNullValue()));

        Pair<String, String> actual = Pair.of(field, value.toString());
        if (fieldArgumentsLeftToGet.contains(actual)) {
          fieldArgumentsLeftToGet.remove(actual);
        } else {
          fail("Unexpected field transformation call: " + actual.getLeft() + " -> " + actual.getRight());
        }
      }
    );

    assertThat(fieldArgumentsLeftToGet, is(empty()));
  }

  @ParameterizedTest
  @MethodSource("functionCallTestCases")
  void testMigrateAndReshapeFqlFunctionCalls(
    String query,
    List<Pair<String, String>> unused,
    List<Triple<String, String, String>> fieldArguments
  ) {
    List<Triple<String, String, String>> fieldArgumentsLeftToGet = new ArrayList<>(fieldArguments);

    MigrationUtils.migrateAndReshapeFql(
      query,
      original -> {
        assertThat(original.field(), is(notNullValue()));
        assertThat(original.operator(), is(notNullValue()));
        assertThat(original.value(), is(notNullValue()));

        Triple<String, String, String> actual = Triple.of(
          original.field(),
          original.operator(),
          original.value().toString()
        );
        if (fieldArgumentsLeftToGet.contains(actual)) {
          fieldArgumentsLeftToGet.remove(actual);
        } else {
          fail("Unexpected field transformation call: " + actual.getLeft() + " -> " + actual.getRight());
        }

        return List.of();
      }
    );

    assertThat(fieldArgumentsLeftToGet, is(empty()));
  }

  @Test
  void testReturnedResultWithNoFieldsOrVersion() {
    assertThat(MigrationUtils.migrateFql("{}", (r, k, v) -> {}), is(equalTo("{}")));
  }

  @Test
  void testReturnedResultWithVersionAndNoFields() {
    assertThat(
      MigrationUtils.migrateFql("{\"_version\":\"old\"}", (r, k, v) -> {}),
      is(equalTo("{\"_version\":\"old\"}"))
    );
  }

  @Test
  void testReturnedResultWithVersionAndFields() {
    assertThat(
      MigrationUtils.migrateFql(
        "{\"_version\":\"old\",\"test\":{}}",
        // this is solely responsible for determining what gets set back into the query
        // (excluding the special _version)
        (result, k, v) -> result.set("foo", new TextNode("bar"))
      ),
      is(equalTo("{\"_version\":\"old\",\"foo\":\"bar\"}"))
    );
  }

  @Test
  void testReturnedResultWithAndOperator() {
    assertThat(
      MigrationUtils.migrateFql(
        "{\"_version\":\"old\",\"$and\":[{\"test\":{}}]}",
        // this is solely responsible for determining what gets set back into the query
        // (excluding the special _version)
        (result, k, v) -> result.set("foo", new TextNode("bar"))
      ),
      is(equalTo("{\"_version\":\"old\",\"$and\":[{\"foo\":\"bar\"}]}"))
    );
  }

  @Test
  void testReshapeWithZeroFields() {
    assertThat(
      MigrationUtils.migrateAndReshapeFql(
        "{\"_version\":\"old\",\"$and\":[{\"test\":{\"$eq\": 123}}]}",
        // this is solely responsible for determining what gets set back into the query
        // (excluding the special _version)
        original -> List.of()
      ),
      is(equalTo("{\"_version\":\"old\"}"))
    );
  }

  @Test
  void testReshapeWithOneField() {
    assertThat(
      MigrationUtils.migrateAndReshapeFql(
        "{\"_version\":\"old\",\"$and\":[{\"test\":{\"$eq\": 123}}]}",
        // this is solely responsible for determining what gets set back into the query
        // (excluding the special _version)
        original -> List.of(new MigrationUtils.FqlFieldAndCondition("field", "op", new TextNode("value")))
      ),
      is(equalTo("{\"_version\":\"old\",\"field\":{\"op\":\"value\"}}"))
    );
  }

  @Test
  void testReshapeWithMultipleFields() {
    assertThat(
      MigrationUtils.migrateAndReshapeFql(
        "{\"_version\":\"old\",\"$and\":[{\"test\":{\"$eq\": 123}}]}",
        // this is solely responsible for determining what gets set back into the query
        // (excluding the special _version)
        original ->
          List.of(
            new MigrationUtils.FqlFieldAndCondition("field", "op", new TextNode("value")),
            new MigrationUtils.FqlFieldAndCondition("field", "op2", new TextNode("value2")),
            new MigrationUtils.FqlFieldAndCondition("field2", "op3", new TextNode("value3"))
          )
      ),
      is(
        equalTo(
          "{\"_version\":\"old\",\"$and\":[{\"field\":{\"op\":\"value\"}},{\"field\":{\"op2\":\"value2\"}},{\"field2\":{\"op3\":\"value3\"}}]}"
        )
      )
    );
  }

  @Test
  void testInvalidJson() {
    assertThrows(UncheckedIOException.class, () -> MigrationUtils.migrateFql("invalid", (r, k, v) -> {}));
    assertThrows(UncheckedIOException.class, () -> MigrationUtils.migrateAndReshapeFql("invalid", r -> List.of()));
  }

  @Test
  void testInvalidVersionNesting() {
    assertThrows(
      InvalidFqlException.class,
      () -> MigrationUtils.migrateAndReshapeFql("{\"$and\":[{\"_version\":\"old\"}]}", r -> null)
    );
  }

  @Test
  void testEqualVersions() {
    assertEquals(0, MigrationUtils.compareVersions("1.2.3", "1.2.3"));
    assertEquals(0, MigrationUtils.compareVersions("alpha.beta", "alpha.beta"));
  }

  @Test
  void testNumericComparison() {
    assertTrue(MigrationUtils.compareVersions("1.2.10", "1.2.2") > 0);
    assertTrue(MigrationUtils.compareVersions("2.0.0", "10.0.0") < 0);
    assertTrue(MigrationUtils.compareVersions("1.10.1", "1.2.9") > 0);
  }

  @Test
  void testLexicalComparison() {
    assertTrue(MigrationUtils.compareVersions("1.2.alpha", "1.2.beta") < 0);
    assertTrue(MigrationUtils.compareVersions("1.2.beta", "1.2.alpha") > 0);
    assertTrue(MigrationUtils.compareVersions("1.alpha.3", "1.beta.3") < 0);
  }

  @Test
  void testMixedNumericAndString() {
    assertTrue(MigrationUtils.compareVersions("1.2.10", "1.2.alpha") < 0);
    assertTrue(MigrationUtils.compareVersions("1.2.alpha", "1.2.10") > 0);
    assertTrue(MigrationUtils.compareVersions("1.2.0", "1.2.beta") < 0);
    assertTrue(MigrationUtils.compareVersions("1.2.beta", "1.2.0") > 0);
  }

  @Test
  void testPrefixCases() {
    assertEquals(0, MigrationUtils.compareVersions("1.2", "1.2.0"));
    assertEquals(0, MigrationUtils.compareVersions("1.2.0", "1.2"));
    assertEquals(0, MigrationUtils.compareVersions("1", "1.0.0"));
    assertEquals(0, MigrationUtils.compareVersions("1.0.0", "1"));
  }

  @Test
  void testDifferentLengthsAndContent() {
    assertTrue(MigrationUtils.compareVersions("1.2.3", "1.2.3.4") < 0);
    assertTrue(MigrationUtils.compareVersions("1.2.3.4", "1.2.3") > 0);
    assertTrue(MigrationUtils.compareVersions("1.2.3.alpha", "1.2.3.alpha.1") < 0);
    assertTrue(MigrationUtils.compareVersions("1.2.3.alpha.1", "1.2.3.alpha") > 0);
  }

  @Test
  void testEmptyStrings() {
    assertEquals(0, MigrationUtils.compareVersions("", ""));
    assertTrue(MigrationUtils.compareVersions("", "1") < 0);
    assertTrue(MigrationUtils.compareVersions("1", "") > 0);
  }

  @Test
  void testSingleSegment() {
    assertTrue(MigrationUtils.compareVersions("1", "2") < 0);
    assertTrue(MigrationUtils.compareVersions("b", "a") > 0);
    assertEquals(0, MigrationUtils.compareVersions("x", "x"));
  }
}
