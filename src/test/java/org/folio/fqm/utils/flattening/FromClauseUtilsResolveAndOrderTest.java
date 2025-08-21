package org.folio.fqm.utils.flattening;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.folio.fqm.utils.flattening.FromClauseUtils.NecessaryJoin;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabaseJoin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class FromClauseUtilsResolveAndOrderTest {

  @Test
  void testDatabaseJoinResolution() {
    EntityTypeSourceDatabase source = new EntityTypeSourceDatabase()
      .alias("a")
      .join(new EntityTypeSourceDatabaseJoin().joinTo("b").condition(":this to :that"));

    Map<String, Set<String>> deps = new HashMap<>();

    FromClauseUtils.resolveDatabaseJoins(new EntityType().addSourcesItem(source), deps);
    assertThat(source.getJoin().getCondition(), is("\"a\" to \"b\""));
    // A must come _after_ B
    assertThat(deps.keySet(), contains("a"));
    assertThat(deps.get("a"), contains("b"));
  }

  static EntityTypeSourceDatabase source(String alias) {
    if (alias.endsWith("*")) {
      return new EntityTypeSourceDatabase()
        .alias(alias.substring(0, alias.length() - 1))
        .join(new EntityTypeSourceDatabaseJoin());
    }
    return new EntityTypeSourceDatabase().alias(alias);
  }

  static EntityTypeColumn column(String name) {
    return new EntityTypeColumn().name(name).sourceAlias(name.split("\\.")[0]);
  }

  static NecessaryJoin join(String source1, String source2) {
    return new NecessaryJoin(column(source1), source(source1), column(source2), source(source2), null);
  }

  static List<Arguments> resolutionOrderTestCases() {
    return List.of(
      Arguments.of(List.of(join("a", "b")), List.of("a[base]", "b[a+b]")),
      Arguments.of(List.of(join("a", "b"), join("a", "c")), List.of("a[base]", "b[a+b]", "c[a+c]")),
      Arguments.of(List.of(join("a", "b"), join("c", "a")), List.of("c[base]", "a[c+a]", "b[a+b]")),
      Arguments.of(List.of(join("b", "a"), join("a", "c")), List.of("b[base]", "a[b+a]", "c[a+c]")),
      Arguments.of(List.of(join("b", "a"), join("c", "a")), List.of("b[base]", "a[b+a]", "c[a+c]")),
      // star denotes DB source that cannot be joined as it is already used in an explicit SQL join
      // a cannot be used, so we have to join b after
      Arguments.of(List.of(join("a*", "b")), List.of("a[base]", "b[a*+b]")),
      Arguments.of(List.of(join("b", "a*")), List.of("a[base]", "b[a*+b]")),
      Arguments.of(List.of(join("a*", "b"), join("a*", "c")), List.of("a[base]", "b[a*+b]", "c[a*+c]")),
      Arguments.of(List.of(join("a", "b*"), join("a", "c")), List.of("b[base]", "a[b*+a]", "c[a+c]")),
      Arguments.of(List.of(join("a", "b"), join("a", "c*")), List.of("c[base]", "a[c*+a]", "b[a+b]"))
    );
  }

  @ParameterizedTest
  @MethodSource("resolutionOrderTestCases")
  void testResolutionOrder(List<NecessaryJoin> joins, List<String> expected) {
    try (MockedStatic<FromClauseUtils> mocked = mockStatic(FromClauseUtils.class, Mockito.CALLS_REAL_METHODS)) {
      mocked
        .when(() -> FromClauseUtils.computeJoin(any(), any(), any(), any()))
        .thenAnswer(invocation ->
          new EntityTypeSourceDatabaseJoin()
            .condition(
              "%s+%s".formatted(
                  ((EntityTypeColumn) invocation.getArgument(1)).getName(),
                  ((EntityTypeColumn) invocation.getArgument(2)).getName()
                )
            )
        );

      // remove duplicates from joins since the pretty string-based creation of test sources
      // causes multiple instances with same alias (which causes problems downstream)
      Map<String, EntityTypeSourceDatabase> normalizedSources = new HashMap<>();
      joins.forEach(join -> {
        normalizedSources.putIfAbsent(join.sourceA().getAlias(), join.sourceA());
        normalizedSources.putIfAbsent(join.sourceB().getAlias(), join.sourceB());
      });

      List<NecessaryJoin> normalizedJoins = joins
        .stream()
        .map(join ->
          new NecessaryJoin(
            join.columnA(),
            normalizedSources.get(join.sourceA().getAlias()),
            join.columnB(),
            normalizedSources.get(join.sourceB().getAlias()),
            join.overrideJoinDirection()
          )
        )
        .distinct()
        .toList();

      List<EntityTypeSourceDatabase> result = FromClauseUtils.resolveJoins(
        new EntityType().sources(new ArrayList<>(normalizedSources.values())),
        new ArrayList<>(normalizedJoins)
      );

      List<String> resultSources = result
        .stream()
        .map(s ->
          "%s[%s]".formatted(
              s.getAlias(),
              Optional.ofNullable(s.getJoin()).map(EntityTypeSourceDatabaseJoin::getCondition).orElse("base")
            )
        )
        .toList();

      assertThat(resultSources, is(expected));
    }
  }
}
