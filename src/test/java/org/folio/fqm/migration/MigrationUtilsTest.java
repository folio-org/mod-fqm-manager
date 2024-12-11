package org.folio.fqm.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.node.TextNode;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Log4j2
class MigrationUtilsTest {

  static List<Arguments> functionCallTestCases() {
    return List.of(
      // query, version transformation expected calls, list of field transformation calls
      Arguments.of("{}", null, List.of()),
      Arguments.of("{\"_version\":\"1\"}", "1", List.of()),
      // basic single-field query
      Arguments.of("{\"field\":{\"$eq\":\"foo\"}}", null, List.of(Pair.of("field", "{\"$eq\":\"foo\"}"))),
      Arguments.of(
        "{\"_version\":\"1\", \"field\":{\"$eq\":\"foo\"}}",
        "1",
        List.of(Pair.of("field", "{\"$eq\":\"foo\"}"))
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
        null,
        List.of(
          Pair.of("field1", "{\"$eq\":\"foo\"}"),
          Pair.of("field2", "{\"$le\":\"bar\"}"),
          Pair.of("field3", "{\"$ne\":\"baz\"}")
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
        "newest and coolest",
        List.of(
          Pair.of("field1", "{\"$eq\":\"foo\"}"),
          Pair.of("field2", "{\"$le\":\"bar\"}"),
          Pair.of("field3", "{\"$ne\":\"baz\"}")
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
        null,
        List.of(Pair.of("field1", "{\"$le\":500,\"$ge\":100}"))
      ),
      // query with $and
      Arguments.of(
        """
          {"$and": [
            { "field": {"$ne": "foo"} },
            { "field": {"$ne": "bar"} }
          ]}
          """,
        null,
        List.of(Pair.of("field", "{\"$ne\":\"foo\"}"), Pair.of("field", "{\"$ne\":\"bar\"}"))
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
        "1",
        List.of(
          Pair.of("field1", "{\"$eq\":\"foo\"}"),
          Pair.of("field2", "{\"$le\":\"bar\"}"),
          Pair.of("field3", "{\"$ne\":\"baz\",\"$gt\":100}"),
          Pair.of("field4", "{\"$ne\":\"foo\"}"),
          Pair.of("field5", "{\"$ne\":\"bar\",\"$lt\":100}")
        )
      )
    );
  }

  @ParameterizedTest
  @MethodSource("functionCallTestCases")
  void testFunctionCalls(String query, String versionArgument, List<Pair<String, String>> fieldArguments) {
    AtomicInteger versionTransformationCalls = new AtomicInteger(0);
    UnaryOperator<String> versionTransformer = input -> {
      assertThat(input, is(versionArgument));
      versionTransformationCalls.incrementAndGet();
      return "newVersion";
    };

    List<Pair<String, String>> fieldArgumentsLeftToGet = new ArrayList<>(fieldArguments);

    MigrationUtils.migrateFql(
      query,
      versionTransformer,
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

    assertThat(versionTransformationCalls.get(), is(1));
    assertThat(fieldArgumentsLeftToGet, is(empty()));
  }

  @Test
  void testReturnedResultWithNoFieldsOrVersion() {
    assertThat(
      MigrationUtils.migrateFql("{}", v -> "new version", (r, k, v) -> {}),
      is(equalTo("{\"_version\":\"new version\"}"))
    );
  }

  @Test
  void testReturnedResultWithVersionAndNoFields() {
    assertThat(
      MigrationUtils.migrateFql("{\"_version\":\"old\"}", v -> "new version", (r, k, v) -> {}),
      is(equalTo("{\"_version\":\"new version\"}"))
    );
  }

  @Test
  void testReturnedResultWithVersionAndFields() {
    assertThat(
      MigrationUtils.migrateFql(
        "{\"_version\":\"old\",\"test\":{}}",
        v -> "new version",
        // this is solely responsible for determining what gets set back into the query
        // (excluding the special _version)
        (result, k, v) -> result.set("foo", new TextNode("bar"))
      ),
      is(equalTo("{\"_version\":\"new version\",\"foo\":\"bar\"}"))
    );
  }

  @Test
  void testReturnedResultWithAndOperator() {
    assertThat(
      MigrationUtils.migrateFql(
        "{\"_version\":\"old\",\"$and\":[{\"test\":{}}]}",
        v -> "new version",
        // this is solely responsible for determining what gets set back into the query
        // (excluding the special _version)
        (result, k, v) -> result.set("foo", new TextNode("bar"))
      ),
      is(equalTo("{\"_version\":\"new version\",\"$and\":[{\"foo\":\"bar\"}]}"))
    );
  }

  @Test
  void testInvalidJson() {
    assertThrows(UncheckedIOException.class, () -> MigrationUtils.migrateFql("invalid", v -> null, (r, k, v) -> {}));
  }
}
