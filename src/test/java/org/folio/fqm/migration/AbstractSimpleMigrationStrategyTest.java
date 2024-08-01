package org.folio.fqm.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.folio.fql.service.FqlService;
import org.folio.fqm.migration.warnings.DeprecatedEntityWarning;
import org.folio.fqm.migration.warnings.EntityTypeWarning;
import org.folio.fqm.migration.warnings.RemovedEntityWarning;
import org.folio.fqm.service.MigrationService;
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

  static class Impl extends AbstractSimpleMigrationStrategy {

    @Override
    public String getLabel() {
      return "label";
    }

    @Override
    public String getSourceVersion() {
      return "source";
    }

    @Override
    public String getTargetVersion() {
      return "target";
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
        Map.entry(UUID_0A, fql -> new DeprecatedEntityWarning("0a", null)),
        Map.entry(UUID_0B, fql -> new RemovedEntityWarning("0b", null, fql))
      );
    }
  }

  static List<Arguments> sourcesWithShouldApply() {
    return List.of(
      Arguments.of("", false),
      Arguments.of("0", false),
      Arguments.of("1", false),
      Arguments.of("-1", false),
      Arguments.of("source", true)
    );
  }

  @ParameterizedTest(name = "{0} applies={1}")
  @MethodSource("sourcesWithShouldApply")
  void testAppliesToMatchingVersions(String version, boolean shouldApply) {
    assertThat(new Impl().applies(version), is(shouldApply));
  }

  static List<Arguments> sourcesForMigrationResults() {
    return List.of(
      // ET change, no FQL changes
      Arguments.of(
        new MigratableQueryInformation(
          UUID_A,
          "{\"_version\":\"source\",\"test\":{\"$eq\":\"foo\"}}",
          List.of("unrelated", "foo", "also_unrelated")
        ),
        new MigratableQueryInformation(
          UUID_B,
          "{\"_version\":\"target\",\"test\":{\"$eq\":\"foo\"}}",
          List.of("unrelated", "bar", "also_unrelated")
        )
      ),
      // ET change and FQL changes
      Arguments.of(
        new MigratableQueryInformation(
          UUID_A,
          "{\"_version\":\"source\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("unrelated", "foo", "also_unrelated")
        ),
        new MigratableQueryInformation(
          UUID_B,
          "{\"_version\":\"target\",\"bar\":{\"$eq\":\"foo\"}}",
          List.of("unrelated", "bar", "also_unrelated")
        )
      ),
      // ET change and no complex FQL changes
      Arguments.of(
        new MigratableQueryInformation(
          UUID_A,
          """
          {"_version":"source","$and":[
            {"field1": {"$eq": true}},
            {"field2": {"$lte": 3}}
          ]}
          """,
          List.of("unrelated", "foo", "also_unrelated")
        ),
        new MigratableQueryInformation(
          UUID_B,
          """
          {"_version":"target","$and":[
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
          {"_version":"source","$and":[
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
          {"_version":"target","$and":[
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
          "{\"_version\":\"source\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("unrelated", "foo", "also_unrelated")
        ),
        new MigratableQueryInformation(
          UUID_D,
          "{\"_version\":\"target\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("unrelated", "foo", "also_unrelated")
        )
      ),
      // No ET change, FQL changes
      Arguments.of(
        new MigratableQueryInformation(
          UUID_E,
          "{\"_version\":\"source\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("unrelated", "foo", "also_unrelated")
        ),
        new MigratableQueryInformation(
          UUID_E,
          "{\"_version\":\"target\",\"bar\":{\"$eq\":\"foo\"}}",
          List.of("unrelated", "bar", "also_unrelated")
        )
      ),
      // No ET change, FQL changes (wildcard)
      Arguments.of(
        new MigratableQueryInformation(
          UUID_F,
          "{\"_version\":\"source\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("field1", "foo", "field2")
        ),
        new MigratableQueryInformation(
          UUID_F,
          "{\"_version\":\"target\",\"bar.foo\":{\"$eq\":\"foo\"}}",
          List.of("bar.field1", "bar.foo", "bar.field2")
        )
      ),
      // No changes
      Arguments.of(
        new MigratableQueryInformation(
          UUID_0,
          "{\"_version\":\"source\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("field1", "foo", "field2")
        ),
        new MigratableQueryInformation(
          UUID_0,
          "{\"_version\":\"target\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("field1", "foo", "field2")
        )
      ),
      // Deprecated ET
      Arguments.of(
        new MigratableQueryInformation(
          UUID_0A,
          "{\"_version\":\"source\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("field1", "foo", "field2")
        ),
        new MigratableQueryInformation(
          UUID_0A,
          "{\"_version\":\"target\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("field1", "foo", "field2"),
          List.of(new DeprecatedEntityWarning("0a", null))
        )
      ),
      // Removed ET
      Arguments.of(
        new MigratableQueryInformation(
          UUID_0B,
          "{\"_version\":\"source\",\"foo\":{\"$eq\":\"foo\"}}",
          List.of("field1", "foo", "field2")
        ),
        new MigratableQueryInformation(
          MigrationService.REMOVED_ENTITY_TYPE_ID,
          "{\"_version\":\"target\"}",
          List.of(),
          List.of(new RemovedEntityWarning("0b", null, "{\"_version\":\"source\",\"foo\":{\"$eq\":\"foo\"}}"))
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
  }
}
