package org.folio.fqm.migration.strategies;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.CheckForNull;
import org.folio.fql.service.FqlService;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS) // needed to have non-static Arguments
public abstract class TestTemplate {

  private final ObjectMapper objectMapper = new ObjectMapper();

  public abstract MigrationStrategy getStrategy();

  /** [Description, Source, Expected] */
  public abstract List<Arguments> getExpectedTransformations();

  public List<Arguments> getExpectedTransformationsWithAdditionalAssertions() {
    return getExpectedTransformations()
      .stream()
      .map(args -> {
        if (args.get().length == 3) {
          return Arguments.of(args.get()[0], args.get()[1], args.get()[2], null);
        } else {
          return args;
        }
      })
      .toList();
  }

  // Tests .applies automatically for all strategies, with an emphasis on weird FQL.
  // Note that .apply, where the work is done, will ONLY be tested here if there's a FQL query above
  // that matches the migration strategy. This is intentional, to encourage specific testing of the
  // migration logic with a relevant query, rather than just getting coverage.
  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("getExpectedTransformationsWithAdditionalAssertions")
  void testStrategy(
    String label,
    MigratableQueryInformation source,
    MigratableQueryInformation expected,
    @CheckForNull Consumer<MigratableQueryInformation> additionalAssertions
  ) throws JsonProcessingException {
    MigrationStrategy strategy = getStrategy();

    MigratableQueryInformation actual = strategy.apply(new FqlService(), source);

    assertThat("[ET ID] " + label, actual.entityTypeId(), is(expected.entityTypeId()));
    assertThat(
      "[FQL] " + label,
      objectMapper.readTree(actual.fqlQuery()),
      is(objectMapper.readTree(expected.fqlQuery()))
    );
    assertThat("[Fields] " + label, actual.fields(), is(expected.fields()));
    assertThat("[Warnings] " + label, actual.warnings(), is(expected.warnings()));

    if (additionalAssertions != null) {
      additionalAssertions.accept(actual);
    }
  }
}
