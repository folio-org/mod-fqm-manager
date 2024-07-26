package org.folio.fqm.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.util.List;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.fql.service.FqlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Log4j2
@TestInstance(Lifecycle.PER_CLASS) // needed to have non-static Arguments
class MigrationStrategyRepositoryTest {

  FqlService fqlService = new FqlService();
  MigrationStrategyRepository migrationStrategyRepository = new MigrationStrategyRepository();

  @Test
  void testHasStrategies() {
    assertThat(migrationStrategyRepository.getMigrationStrategies(), is(not(empty())));
  }

  List<Arguments> migrationStrategiesAndQueries() {
    List<MigrationStrategy> strategies = migrationStrategyRepository.getMigrationStrategies();
    List<Pair<String, MigratableQueryInformation>> queries = List.of(
      Pair.of("null FQL", new MigratableQueryInformation(new UUID(0, 0), null, List.of())),
      Pair.of("empty FQL", new MigratableQueryInformation(new UUID(0, 0), "{}", List.of())),
      Pair.of("invalid FQL", new MigratableQueryInformation(new UUID(0, 0), "This is Jason, not JSON", List.of())),
      Pair.of(
        "FQL without version",
        new MigratableQueryInformation(new UUID(0, 0), "{\"test\":{\"$eq\":\"foo\"}}", List.of())
      ),
      Pair.of(
        "FQL with invalid operator",
        new MigratableQueryInformation(new UUID(0, 0), "{\"test\":{\"$i_am_invalid\":[]}}", List.of())
      ),
      Pair.of(
        "FQL with version=1 only",
        new MigratableQueryInformation(new UUID(0, 0), "{\"_version\":\"1\"}", List.of())
      ),
      Pair.of(
        "FQL with weird version=-1",
        new MigratableQueryInformation(new UUID(0, 0), "{\"_version\":\"-1\"}", List.of())
      )
    );

    return strategies
      .stream()
      .flatMap(strategy ->
        queries
          .stream()
          .map(query ->
            Arguments.of("%s: %s".formatted(query.getLeft(), strategy.getLabel()), strategy, query.getValue())
          )
      )
      .toList();
  }

  // Tests .applies automatically for all strategies, with an emphasis on weird FQL.
  // Note that .apply, where the work is done, will ONLY be tested here if there's a FQL query above
  // that matches the migration strategy. This is intentional, to encourage specific testing of the
  // migration logic with a relevant query, rather than just getting coverage.
  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("migrationStrategiesAndQueries")
  void testStrategies(String label, MigrationStrategy strategy, MigratableQueryInformation query) {
    boolean applies = strategy.applies(fqlService, query);

    log.info("{} applies={}", label, applies);

    if (applies) {
      strategy.apply(fqlService, query);
    }
  }
}
