package org.folio.fqm.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.exception.InvalidFqlException;
import org.folio.fqm.migration.types.MigratableFqlFieldAndCondition;
import org.folio.fqm.migration.types.SingleFieldMigrationResult;
import org.folio.fqm.migration.warnings.RemovedFieldWarning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Log4j2
class MigrationUtilsTest {

  private static final UUID TEST_UUID = UUID.fromString("d686ef05-fb3d-5edc-a87f-c3001c579dfb");
  private static final UUID UUID_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID UUID_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private static final UUID UUID_C = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
  private static final Map<UUID, Map<String, UUID>> EMPTY_SOURCE_MAP = Map.of();
  private static final Map<UUID, Map<String, UUID>> DUMMY_SOURCE_MAP = Map.ofEntries(
    Map.entry(TEST_UUID, Map.of("sourceA", UUID_A, "sourceB", UUID_B)),
    Map.entry(UUID_B, Map.of("sourceC", UUID_C))
  );

  static List<Arguments> migrateFqlFunctionCallTestCases() {
    return List.of(
      // query, list of expected field transformation calls from migrateFql
      Arguments.of("{}", List.of()),
      Arguments.of("{\"_version\":\"1\"}", List.of()),
      // basic single-field query
      Arguments.of("{\"field\":{\"$eq\":\"foo\"}}", List.of(callWith("field", "$eq", "\"foo\""))),
      Arguments.of("{\"_version\":\"1\", \"field\":{\"$eq\":\"foo\"}}", List.of(callWith("field", "$eq", "\"foo\""))),
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
          callWith("field1", "$eq", "\"foo\""),
          callWith("field2", "$le", "\"bar\""),
          callWith("field3", "$ne", "\"baz\"")
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
          callWith("field1", "$eq", "\"foo\""),
          callWith("field2", "$le", "\"bar\""),
          callWith("field3", "$ne", "\"baz\"")
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
        List.of(callWith("field1", "$le", "500"), callWith("field1", "$ge", "100"))
      ),
      // query with $and
      Arguments.of(
        """
          {"$and": [
            { "field": {"$ne": "foo"} },
            { "field": {"$ne": "bar"} }
          ]}
          """,
        List.of(callWith("field", "$ne", "\"foo\""), callWith("field", "$ne", "\"bar\""))
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
          callWith("field1", "$eq", "\"foo\""),
          callWith("field2", "$le", "\"bar\""),
          callWith("field3", "$ne", "\"baz\""),
          callWith("field3", "$gt", "100"),
          callWith("field4", "$ne", "\"foo\""),
          callWith("field5", "$ne", "\"bar\""),
          callWith("field5", "$lt", "100")
        )
      ),
      // basic nested source
      Arguments.of(
        "{\"sourceA.field\":{\"$eq\":\"foo\"}}",
        List.of(callWith("sourceA.field", "$eq", "\"foo\""), callWith(UUID_A, "sourceA.", "field", "$eq", "\"foo\""))
      ),
      // doubly nested source
      Arguments.of(
        "{\"sourceB.sourceC.field\":{\"$eq\":\"foo\"}}",
        List.of(
          callWith(TEST_UUID, "", "sourceB.sourceC.field", "$eq", "\"foo\""),
          callWith(UUID_B, "sourceB.", "sourceC.field", "$eq", "\"foo\""),
          callWith(UUID_C, "sourceB.sourceC.", "field", "$eq", "\"foo\"")
        )
      ),
      // no nested source match, so we can't traverse inside
      Arguments.of(
        """
        {
          "nested.field":{"$eq":"foo"},
          "sourceB.garbage.field":{"$eq":"bar"}
        }
        """,
        List.of(
          callWith(TEST_UUID, "", "nested.field", "$eq", "\"foo\""),
          callWith(TEST_UUID, "", "sourceB.garbage.field", "$eq", "\"bar\""),
          callWith(UUID_B, "sourceB.", "garbage.field", "$eq", "\"bar\"")
        )
      ),
      // early breaks. since the outer (composite'd) field was changed, we shouldn't keep traversing
      // (e.g. migration applies to composite specifically and/or has composite-specific logic)
      // these are configured to perform the $ACTION with exactly one outer source
      Arguments.of(
        """
        {
          "sourceB.$REMOVE":{"$eq":"foo"},
          "sourceB.$CHANGE":{"$eq":"bar"},
          "sourceB.$ADD":{"$eq":"boo"},
          "sourceB.$BREAK":{"$eq":"baz"},
          "sourceB.sourceC.$WARN":{"$eq":"baz"}
        }
        """,
        List.of(
          callWith("sourceB.$REMOVE", "$eq", "\"foo\""),
          callWith("sourceB.$CHANGE", "$eq", "\"bar\""),
          callWith("sourceB.$ADD", "$eq", "\"boo\""),
          callWith("sourceB.$BREAK", "$eq", "\"baz\""),
          callWith("sourceB.sourceC.$WARN", "$eq", "\"baz\""),
          callWith(UUID_B, "sourceB.", "sourceC.$WARN", "$eq", "\"baz\"")
        )
      )
    );
  }

  @ParameterizedTest
  @MethodSource("migrateFqlFunctionCallTestCases")
  void testMigrateFqlFunctionCalls(String query, List<MigratableFqlFieldAndCondition> fieldArguments) {
    List<MigratableFqlFieldAndCondition> fieldArgumentsLeftToGet = new ArrayList<>(fieldArguments);

    MigrationUtils.migrateFql(
      TEST_UUID,
      query,
      (MigratableFqlFieldAndCondition original) -> {
        assertThat(original.entityTypeId(), is(notNullValue()));
        assertThat(original.fieldPrefix(), is(notNullValue()));
        assertThat(original.field(), is(notNullValue()));
        assertThat(original.operator(), is(notNullValue()));
        assertThat(original.value(), is(notNullValue()));

        if (fieldArgumentsLeftToGet.contains(original)) {
          fieldArgumentsLeftToGet.remove(original);
        } else {
          fail("Unexpected field transformation call: " + original);
        }

        if (original.field().matches("^\\w+\\.\\$REMOVE.*$")) {
          return SingleFieldMigrationResult.removed();
        } else if (original.field().matches("^\\w+\\.\\$CHANGE.*$")) {
          return SingleFieldMigrationResult.withField(original.withField("new_field"));
        } else if (original.field().matches("^\\w+\\.\\$ADD.*$")) {
          return new SingleFieldMigrationResult<>(List.of(original, original), List.of(), false);
        } else if (original.field().matches("^\\w+\\.\\$BREAK.*$")) {
          return SingleFieldMigrationResult.noop(original).withHadBreakingChange(true);
        } else if (original.field().matches("^\\w+\\.\\$WARN.*$")) {
          return SingleFieldMigrationResult.noop(original).withWarnings(List.of(RemovedFieldWarning.builder().build()));
        }
        return SingleFieldMigrationResult.noop(original);
      },
      DUMMY_SOURCE_MAP
    );

    assertThat(fieldArgumentsLeftToGet, is(empty()));
  }

  @Test
  void testReshapeWithZeroFields() {
    assertThat(
      MigrationUtils
        .migrateFql(
          TEST_UUID,
          "{\"_version\":\"old\",\"$and\":[{\"test\":{\"$eq\": 123}}]}",
          // this is solely responsible for determining what gets set back into the query
          // (excluding the special _version)
          original -> SingleFieldMigrationResult.removed(),
          EMPTY_SOURCE_MAP
        )
        .result(),
      is(equalTo("{\"_version\":\"old\"}"))
    );
  }

  @Test
  void testReshapeWithOneField() {
    assertThat(
      MigrationUtils
        .migrateFql(
          TEST_UUID,
          "{\"_version\":\"old\",\"$and\":[{\"test\":{\"$eq\": 123}}]}",
          // this is solely responsible for determining what gets set back into the query
          // (excluding the special _version)
          original ->
            SingleFieldMigrationResult.withField(
              new MigratableFqlFieldAndCondition(null, "prefix.", "field", "op", new TextNode("value"))
            ),
          EMPTY_SOURCE_MAP
        )
        .result(),
      is(equalTo("{\"_version\":\"old\",\"prefix.field\":{\"op\":\"value\"}}"))
    );
  }

  @Test
  void testReshapeWithMultipleFields() {
    assertThat(
      MigrationUtils
        .migrateFql(
          TEST_UUID,
          "{\"_version\":\"old\",\"$and\":[{\"test\":{\"$eq\": 123}}]}",
          // this is solely responsible for determining what gets set back into the query
          // (excluding the special _version)
          original ->
            new SingleFieldMigrationResult<>(
              List.of(
                new MigratableFqlFieldAndCondition(null, "", "field", "op", new TextNode("value")),
                new MigratableFqlFieldAndCondition(null, "", "field", "op2", new TextNode("value2")),
                new MigratableFqlFieldAndCondition(null, "", "field2", "op3", new TextNode("value3"))
              ),
              List.of(),
              false
            ),
          EMPTY_SOURCE_MAP
        )
        .result(),
      is(
        equalTo(
          "{\"_version\":\"old\",\"$and\":[{\"field\":{\"op\":\"value\"}},{\"field\":{\"op2\":\"value2\"}},{\"field2\":{\"op3\":\"value3\"}}]}"
        )
      )
    );
  }

  @Test
  void testReshapeWithNestedSourceField() {
    assertThat(
      MigrationUtils
        .migrateFql(
          TEST_UUID,
          "{\"_version\":\"old\",\"sourceB.sourceC.field\":{\"$eq\": 123}}",
          original -> {
            if (original.equals(callWith("sourceB.sourceC.field", "$eq", "123"))) {
              return SingleFieldMigrationResult.noop(original); // noop
            } else if (original.equals(callWith(UUID_B, "sourceB.", "sourceC.field", "$eq", "123"))) {
              // change is triggered on sourceB level, replacing sourceC.field with these.
              // sourceC will no longer be referenced directly in the resulting query
              return new SingleFieldMigrationResult<>(
                List.of(
                  original.withField("new1").withOperator("$a").withValue(new TextNode("aaa")),
                  original.withField("new2").withOperator("$b").withValue(new TextNode("bbb"))
                ),
                List.of(),
                false
              );
            } else {
              fail("Unexpected field transformation call: " + original);
              return null;
            }
          },
          DUMMY_SOURCE_MAP
        )
        .result(),
      is(
        equalTo(
          "{\"_version\":\"old\",\"$and\":[{\"sourceB.new1\":{\"$a\":\"aaa\"}},{\"sourceB.new2\":{\"$b\":\"bbb\"}}]}"
        )
      )
    );
  }

  @Test
  void testInvalidJson() {
    assertThrows(
      UncheckedIOException.class,
      () -> MigrationUtils.migrateFql(TEST_UUID, "invalid", r -> null, EMPTY_SOURCE_MAP)
    );
  }

  @Test
  void testInvalidVersionNesting() {
    assertThrows(
      InvalidFqlException.class,
      () -> MigrationUtils.migrateFql(TEST_UUID, "{\"$and\":[{\"_version\":\"old\"}]}", r -> null, EMPTY_SOURCE_MAP)
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

  private static MigratableFqlFieldAndCondition callWith(String field, String op, String value) {
    return callWith(TEST_UUID, "", field, op, value);
  }

  @SneakyThrows(IOException.class)
  private static MigratableFqlFieldAndCondition callWith(
    UUID entityId,
    String fieldPrefix,
    String field,
    String op,
    String value
  ) {
    return new MigratableFqlFieldAndCondition(entityId, fieldPrefix, field, op, new ObjectMapper().readTree(value));
  }
}
