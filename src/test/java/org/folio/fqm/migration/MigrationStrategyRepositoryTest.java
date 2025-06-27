package org.folio.fqm.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.fql.service.FqlService;
import org.folio.fqm.config.MigrationConfiguration;
import org.folio.fqm.service.MigrationService;
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
  MigrationStrategyRepository migrationStrategyRepository = new MigrationStrategyRepository(
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null
  );
  MigrationService migrationService = new MigrationService(
    null,
    new MigrationConfiguration(),
    null,
    new ObjectMapper()
  );

  @Test
  void testHasStrategies() {
    assertThat(migrationStrategyRepository.getMigrationStrategies(), is(not(empty())));
  }

  List<Arguments> migrationStrategiesAndQueries() {
    List<MigrationStrategy> strategies = migrationStrategyRepository.getMigrationStrategies();
    List<Pair<String, MigratableQueryInformation>> queries = List.of(
      Pair.of("null FQL", new MigratableQueryInformation(new UUID(0, 0), "{}", List.of())),
      Pair.of("empty FQL", new MigratableQueryInformation(new UUID(0, 0), "{}", List.of())),
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
    boolean applies = strategy.applies(query);

    log.info("{} applies={}", label, applies);

    if (applies) {
      strategy.apply(fqlService, query);
    }

    // migration application is thoroughly tested for this shared logic
    // these test the maps/etc all are set up correctly
    if (strategy instanceof AbstractSimpleMigrationStrategy abstractStrategy) {
      abstractStrategy.getEntityTypeChanges();
      abstractStrategy.getFieldChanges();
      assertThat(abstractStrategy.getLabel(), is(notNullValue()));
      assertThat(abstractStrategy.getMaximumApplicableVersion(), is(notNullValue()));
      abstractStrategy
        .getEntityTypeWarnings()
        .forEach((k, v) -> {
          assertThat(v.apply("{}"), is(notNullValue()));
        });
      abstractStrategy
        .getFieldWarnings()
        .forEach((k, v) ->
          v.forEach((k2, v2) -> {
            assertThat(v2.apply("field", "{}"), is(notNullValue()));
          })
        );
    }
  }

  @Test
  void testMigrationStrategiesAreOrdered() {
    // It'd be easy to accidentally miss something and not have strategies in the right order. This test is a safety net to prevent that.
    // We could probably just sort these in MigrationService, but we have to have them listed in the repository anyway, so we might as
    // well just insist that they are properly ordered there instead
    List<MigrationStrategy> strategies = migrationStrategyRepository.getMigrationStrategies();
    List<MigrationStrategy> sortedStrategies = strategies.stream()
      .sorted(Comparator.comparing(MigrationStrategy::getMaximumApplicableVersion, MigrationUtils::compareVersions))
      .toList();
    assertEquals(sortedStrategies, strategies);
  }
}
