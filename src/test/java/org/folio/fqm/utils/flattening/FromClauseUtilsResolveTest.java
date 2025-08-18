package org.folio.fqm.utils.flattening;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabaseJoin;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class FromClauseUtilsResolveTest {

  // simple ET root source
  private static final EntityTypeSourceDatabase DATABASE_WITHOUT_JOIN = new EntityTypeSourceDatabase()
    .alias("db_legacy_no_join");

  // simple ET secondary source
  private static final EntityTypeSourceDatabase DATABASE_LEGACY_JOIN = new EntityTypeSourceDatabase()
    .alias("db_legacy")
    .join(new EntityTypeSourceDatabaseJoin().type("left join").joinTo("other").condition(":this <-> :that"));

  // composite base ET
  private static final EntityTypeSourceEntityType ENTITY_TYPE_ROOT = new EntityTypeSourceEntityType()
    .alias("et_no_join");

  // single DB under base ET
  private static final EntityTypeSourceDatabase DATABASE_UNDER_ROOT_ET_WITHOUT_JOIN = new EntityTypeSourceDatabase()
    .alias("db_under_root_et_with_no_join")
    .joinedViaEntityType("et_no_join");

  // composite secondary ET
  private static final EntityTypeSourceEntityType ENTITY_TYPE_JOINED = new EntityTypeSourceEntityType()
    .alias("et_joined")
    .sourceField("other.foo")
    .targetField("bar");

  // single DB under composite root ET
  private static final EntityTypeSourceDatabase DATABASE_UNDER_JOINED_ET = new EntityTypeSourceDatabase()
    .alias("db_under_joined_et")
    .joinedViaEntityType("et_joined");

  // secondary ET in a composite below the main composite ET
  private static final EntityTypeSourceEntityType ENTITY_TYPE_JOINED_WITH_PARENT = new EntityTypeSourceEntityType()
    .alias("et_joined_with_parent")
    .joinedViaEntityType("et_no_join")
    .sourceField("other.foo")
    .targetField("bar");

  // single DB under sub-composite composite root ET
  private static final EntityTypeSourceDatabase DATABASE_UNDER_JOINED_ET_WITH_PARENT = new EntityTypeSourceDatabase()
    .alias("db_under_root_et_with_no_join")
    .joinedViaEntityType("et_joined_with_parent");

  // for a very deeply nested simple, joined way farther up
  private static final EntityTypeSourceEntityType ENTITY_TYPE_JOINED_CHILD = new EntityTypeSourceEntityType()
    .alias("et_joined_child")
    .joinedViaEntityType("et_joined");

  private static final EntityTypeSourceEntityType ENTITY_TYPE_JOINED_CHILD_CHILD = new EntityTypeSourceEntityType()
    .alias("et_joined_child_child")
    .joinedViaEntityType("et_joined_child");

  private static final EntityTypeSourceEntityType ENTITY_TYPE_JOINED_CHILD_CHILD_CHILD = new EntityTypeSourceEntityType()
    .alias("et_joined_child_child_child")
    .joinedViaEntityType("et_joined_child_child");

  private static final EntityTypeSourceDatabase DATABASE_UNDER_JOINED_CHILD_CHILD_CHILD = new EntityTypeSourceDatabase()
    .alias("db_under_joined_child_child_child")
    .joinedViaEntityType("et_joined_child_child_child");

  static List<Arguments> resolutionTestCases() {
    return List.of(
      // list of joins, map of expected join aliases -> resolved join conditions
      Arguments.of(List.of(DATABASE_WITHOUT_JOIN), Map.of()),
      Arguments.of(List.of(DATABASE_LEGACY_JOIN), Map.of("db_legacy", "\"db_legacy\" <-> \"other\"")),
      Arguments.of(List.of(ENTITY_TYPE_ROOT, DATABASE_UNDER_ROOT_ET_WITHOUT_JOIN), Map.of())
      // Arguments.of(
      //   List.of(ENTITY_TYPE_ROOT, ENTITY_TYPE_JOINED, DATABASE_UNDER_JOINED_ET),
      //   Map.of("db_under_joined_et", "via et_joined")
      // )
      // Arguments.of(
      //   List.of(
      //     ENTITY_TYPE_ROOT,
      //     ENTITY_TYPE_JOINED,
      //     ENTITY_TYPE_JOINED_WITH_PARENT,
      //     DATABASE_UNDER_JOINED_ET_WITH_PARENT
      //   ),
      //   // should be joined via sub-et, not the parent above et_joined_with_parent
      //   Map.of("db_under_root_et_with_no_join", "via et_joined_with_parent")
      // )
      // Arguments.of(
      //   List.of(
      //     ENTITY_TYPE_ROOT,
      //     ENTITY_TYPE_JOINED,
      //     ENTITY_TYPE_JOINED_CHILD,
      //     ENTITY_TYPE_JOINED_CHILD_CHILD,
      //     ENTITY_TYPE_JOINED_CHILD_CHILD_CHILD,
      //     DATABASE_UNDER_JOINED_CHILD_CHILD_CHILD
      //   ),
      //   // gets joined based on the thing at the top
      //   Map.of("db_under_joined_child_child_child", "via et_joined")
      // ),
      // Arguments.of(
      //   List.of( // it's a party
      //     DATABASE_WITHOUT_JOIN,
      //     DATABASE_LEGACY_JOIN,
      //     ENTITY_TYPE_ROOT,
      //     DATABASE_UNDER_ROOT_ET_WITHOUT_JOIN,
      //     ENTITY_TYPE_JOINED,
      //     DATABASE_UNDER_JOINED_ET,
      //     ENTITY_TYPE_JOINED_WITH_PARENT,
      //     DATABASE_UNDER_JOINED_ET_WITH_PARENT,
      //     ENTITY_TYPE_JOINED_CHILD,
      //     ENTITY_TYPE_JOINED_CHILD_CHILD,
      //     ENTITY_TYPE_JOINED_CHILD_CHILD_CHILD,
      //     DATABASE_UNDER_JOINED_CHILD_CHILD_CHILD
      //   ),
      //   Map.ofEntries(
      //     Map.entry("db_legacy", "\"db_legacy\" <-> \"other\""),
      //     Map.entry("db_under_joined_et", "via et_joined"),
      //     Map.entry("db_under_root_et_with_no_join", "via et_joined_with_parent"),
      //     Map.entry("db_under_joined_child_child_child", "via et_joined")
      //   )
      // )
    );
  }

  @ParameterizedTest
  @MethodSource("resolutionTestCases")
  void testResolveJoinConditions(List<EntityTypeSource> sources, Map<String, String> expected) {
    try (MockedStatic<FromClauseUtils> mocked = mockStatic(FromClauseUtils.class, Mockito.CALLS_REAL_METHODS)) {
      mocked
        .when(() -> FromClauseUtils.computeJoin(any(), any(), any(), any()))
        .thenAnswer(invocation ->
          new EntityTypeSourceDatabaseJoin()
            .condition(
              "ET[%s -> %s]".formatted(
                  ((EntityTypeColumn) invocation.getArgument(1)).getName(),
                  ((EntityTypeColumn) invocation.getArgument(2)).getName()
                )
            )
        );
      mocked
        .when(() -> FromClauseUtils.computeJoin(any(), any(), any(), any()))
        .thenAnswer(invocation ->
          new EntityTypeSourceDatabaseJoin()
            .condition(
              "ET[%s -> %s]".formatted(
                  ((EntityTypeColumn) invocation.getArgument(1)).getName(),
                  ((EntityTypeColumn) invocation.getArgument(2)).getName()
                )
            )
        );

      List<EntityTypeSourceDatabase> resolved = FromClauseUtils.resolveJoins(
        new EntityType()
          .sources(sources)
          .addColumnsItem(new EntityTypeColumn().name("other.foo").sourceAlias("other"))
          .addColumnsItem(new EntityTypeColumn().name("et_joined.bar").sourceAlias("db_under_joined_et"))
          .addColumnsItem(
            new EntityTypeColumn().name("et_joined_with_parent").sourceAlias("db_under_root_et_with_no_join")
          )
      );

      Map<String, String> result = resolved
        .stream()
        .filter(EntityTypeSourceDatabase.class::isInstance)
        .map(EntityTypeSourceDatabase.class::cast)
        .filter(s -> s.getJoin() != null)
        .collect(Collectors.toMap(EntityTypeSourceDatabase::getAlias, s -> s.getJoin().getCondition()));

      assertThat(result, is(expected));
    }
  }
}
