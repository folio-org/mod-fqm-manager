package org.folio.fqm.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.folio.fql.service.FqlService;
import org.folio.fqm.config.MigrationConfiguration;
import org.folio.fqm.migration.warnings.DeprecatedEntityWarning;
import org.folio.fqm.migration.warnings.DeprecatedFieldWarning;
import org.folio.fqm.migration.warnings.EntityTypeWarning;
import org.folio.fqm.migration.warnings.FieldWarning;
import org.folio.fqm.migration.warnings.QueryBreakingWarning;
import org.folio.fqm.migration.warnings.RemovedEntityWarning;
import org.folio.fqm.migration.warnings.RemovedFieldWarning;
import org.folio.fqm.migration.warnings.Warning;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AbstractSimpleMigrationStrategyTest {

  FqlService fqlService = new FqlService();
  ObjectMapper objectMapper = new ObjectMapper();

  // A -> B, field changes
  static final UUID UUID_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  static final UUID UUID_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  // C -> D, no field changes
  static final UUID UUID_C = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
  static final UUID UUID_D = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
  // E has field changes
  static final UUID UUID_E = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
  // F has field changes with wildcard
  static final UUID UUID_F = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
  // 0 has no changes
  static final UUID UUID_0 = UUID.fromString("00000000-0000-0000-0000-000000000000");

  // deprecated ET
  static final UUID UUID_0A = UUID.fromString("00000000-0000-0000-0000-aaaaaaaaaaaa");
  // removed ET
  static final UUID UUID_0B = UUID.fromString("00000000-0000-0000-0000-bbbbbbbbbbbb");
  // field warnings
  static final UUID UUID_0C = UUID.fromString("00000000-0000-0000-0000-cccccccccccc");

  static class Impl extends AbstractSimpleMigrationStrategy {

    @Override
    public String getLabel() {
      return "label";
    }

    @Override
    public String getMaximumApplicableVersion() {
      return "source";
    }

    @Override
    public Map<UUID, UUID> getEntityTypeChanges() {
      return Map.ofEntries(Map.entry(UUID_A, UUID_B), Map.entry(UUID_C, UUID_D));
    }

    @Override
    public Map<UUID, Map<String, String>> getFieldChanges() {
      return Map.ofEntries(
        Map.entry(UUID_A, Map.of("foo", "bar")),
        Map.entry(UUID_E, Map.of("foo", "bar")),
        Map.entry(UUID_F, Map.of("*", "bar.%s"))
      );
    }

    @Override
    public Map<UUID, Function<String, EntityTypeWarning>> getEntityTypeWarnings() {
      return Map.ofEntries(
        Map.entry(UUID_0A, DeprecatedEntityWarning.withoutAlternative("0a")),
        Map.entry(UUID_0B, RemovedEntityWarning.withoutAlternative("0b"))
      );
    }

    @Override
    public Map<UUID, Map<String, BiFunction<String, String, FieldWarning>>> getFieldWarnings() {
      return Map.ofEntries(
        Map.entry(
          UUID_0C,
          Map.ofEntries(
            Map.entry("deprecated", DeprecatedFieldWarning.build()),
            Map.entry("query_breaking", QueryBreakingWarning.withAlternative("alt")),
            Map.entry("removed", RemovedFieldWarning.withAlternative("alt"))
          )
        )
      );
    }
  }

  static List<Arguments> sourcesForMigrationResults() {
    return List.of(
      // ET change, no FQL changes
      Arguments.of(
        new MigratableQueryInformation(
          UUID_A,
          "{\"_version\":\"version\",\"test\":{\"$eq\":\"foo\"}}",
          List.of("unrelated", "foo", "also_unrelated")
        ),
        new MigratableQueryInformation(
          UUID_B,
          "{\"_version\":\"version\",\"test\":{\"$eq\":\"foo\"}}",
          List.of("unrelated", "bar", "also_unrelated")
        )
      ),
      // ET change and FQL changes
      Arguments.of(
        new MigratableQueryInformation(
          UUID_A,
          "{\"_version\":\"version\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("unrelated", "foo", "also_unrelated")
        ),
        new MigratableQueryInformation(
          UUID_B,
          "{\"_version\":\"version\",\"bar\":{\"$eq\":\"foo\"}}",
          List.of("unrelated", "bar", "also_unrelated")
        )
      ),
      // ET change and no complex FQL changes
      Arguments.of(
        new MigratableQueryInformation(
          UUID_A,
          """
          {"_version":"version","$and":[
            {"field1": {"$eq": true}},
            {"field2": {"$lte": 3}}
          ]}
          """,
          List.of("unrelated", "foo", "also_unrelated")
        ),
        new MigratableQueryInformation(
          UUID_B,
          """
          {"_version":"version","$and":[
            {"field1": {"$eq": true}},
            {"field2": {"$lte": 3}}
          ]}
          """,
          List.of("unrelated", "bar", "also_unrelated")
        )
      ),
      // ET change and complex FQL field change
      Arguments.of(
        new MigratableQueryInformation(
          UUID_A,
          """
          {"_version":"version","$and":[
            {"field1": {"$eq": true}},
            {"$and": [
              {"field2": {"$gte": 2}},
              {"foo": {"$eq": "aaa"}}
            ]},
            {"field3": {"$lte": 3}}
          ]}
          """,
          List.of("unrelated", "foo", "also_unrelated")
        ),
        new MigratableQueryInformation(
          UUID_B,
          """
          {"_version":"version","$and":[
            {"field1": {"$eq": true}},
            {"$and": [
              {"field2": {"$gte": 2}},
              {"bar": {"$eq": "aaa"}}
            ]},
            {"field3": {"$lte": 3}}
          ]}
          """,
          List.of("unrelated", "bar", "also_unrelated")
        )
      ),
      // ET change, no FQL changes
      Arguments.of(
        new MigratableQueryInformation(
          UUID_C,
          "{\"_version\":\"version\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("unrelated", "foo", "also_unrelated")
        ),
        new MigratableQueryInformation(
          UUID_D,
          "{\"_version\":\"version\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("unrelated", "foo", "also_unrelated")
        )
      ),
      // No ET change, FQL changes
      Arguments.of(
        new MigratableQueryInformation(
          UUID_E,
          "{\"_version\":\"version\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("unrelated", "foo", "also_unrelated")
        ),
        new MigratableQueryInformation(
          UUID_E,
          "{\"_version\":\"version\",\"bar\":{\"$eq\":\"foo\"}}",
          List.of("unrelated", "bar", "also_unrelated")
        )
      ),
      // No ET change, FQL changes (wildcard)
      Arguments.of(
        new MigratableQueryInformation(
          UUID_F,
          "{\"_version\":\"version\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("field1", "foo", "field2")
        ),
        new MigratableQueryInformation(
          UUID_F,
          "{\"_version\":\"version\",\"bar.foo\":{\"$eq\":\"foo\"}}",
          List.of("bar.field1", "bar.foo", "bar.field2")
        )
      ),
      // No changes
      Arguments.of(
        new MigratableQueryInformation(
          UUID_0,
          "{\"_version\":\"version\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("field1", "foo", "field2")
        ),
        new MigratableQueryInformation(
          UUID_0,
          "{\"_version\":\"version\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("field1", "foo", "field2")
        )
      ),
      // Deprecated ET
      Arguments.of(
        new MigratableQueryInformation(
          UUID_0A,
          "{\"_version\":\"version\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("field1", "foo", "field2")
        ),
        new MigratableQueryInformation(
          UUID_0A,
          "{\"_version\":\"version\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("field1", "foo", "field2"),
          List.of(DeprecatedEntityWarning.withoutAlternative("0a").apply(null)),
          null,
          false
        )
      ),
      // Removed ET
      Arguments.of(
        new MigratableQueryInformation(
          UUID_0B,
          "{\"_version\":\"version\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("field1", "foo", "field2")
        ),
        new MigratableQueryInformation(
          MigrationConfiguration.REMOVED_ENTITY_TYPE_ID,
          "{}",
          List.of(),
          List.of(
            RemovedEntityWarning.withoutAlternative("0b").apply("{\"_version\":\"version\",\"foo\":{\"$eq\":\"foo\"}}")
          ),
          null,
          true
        )
      ),
      // field-level warnings
      Arguments.of(
        new MigratableQueryInformation(
          UUID_0C,
          """
          {"_version":"version","$and":[
            {"unrelated": {"$eq": true}},
            {"removed": {"$lte": 3}},
            {"deprecated": {"$lte": 2}},
            {"query_breaking": {"$ne": 2}}
          ]}
          """,
          List.of("unrelated", "removed", "deprecated", "query_breaking")
        ),
        new MigratableQueryInformation(
          UUID_0C,
          """
          {"_version":"version","$and":[
            {"unrelated": {"$eq": true}},
            {"deprecated": {"$lte": 2}}
          ]}
          """,
          List.of("unrelated", "deprecated", "query_breaking"),
          List.of(
            DeprecatedFieldWarning.build().apply("deprecated", "{\n  \"$lte\" : 2\n}"),
            DeprecatedFieldWarning.build().apply("deprecated", null),
            QueryBreakingWarning.withAlternative("alt").apply("query_breaking", "{\n  \"$ne\" : 2\n}"),
            RemovedFieldWarning.withAlternative("alt").apply("removed", "{\n  \"$lte\" : 3\n}"),
            RemovedFieldWarning.withAlternative("alt").apply("removed", null)
          ),
          null,
          false
        )
      ),
      // removed top-level query field
      Arguments.of(
        new MigratableQueryInformation(
          UUID_0C,
          """
          {
            "_version":"version",
            "query_breaking": {"$ne": 2}
          }
          """,
          List.of("query_breaking")
        ),
        new MigratableQueryInformation(
          UUID_0C,
          "{\"_version\":\"version\"}",
          List.of("query_breaking"),
          List.of(QueryBreakingWarning.withAlternative("alt").apply("query_breaking", "{\n  \"$ne\" : 2\n}")),
          null,
          false
        )
      )
    );
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("sourcesForMigrationResults")
  void testMigrationResults(MigratableQueryInformation source, MigratableQueryInformation expected)
    throws JsonProcessingException {
    MigratableQueryInformation result = new Impl().apply(fqlService, source);

    assertThat(result.entityTypeId(), is(expected.entityTypeId()));
    // deserialize to help prevent whitespace/etc breaking the test
    assertThat(objectMapper.readTree(result.fqlQuery()), is(objectMapper.readTree(expected.fqlQuery())));
    assertThat(result.fields(), is(expected.fields()));
    for (Warning warning : result.warnings()) {
      assertThat(warning.toString() + " is expected", expected.warnings().stream().anyMatch(warning::equals), is(true));
    }
    assertThat(result.warnings(), hasSize(expected.warnings().size()));
  }
}
