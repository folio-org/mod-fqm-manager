package org.folio.fqm.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.fql.service.FqlService;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationStrategyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MigrationServiceTest {

  @Mock
  private FqlService fqlService;

  @Mock
  private MigrationStrategyRepository migrationStrategyRepository;

  @Spy
  private ObjectMapper objectMapper;

  @InjectMocks
  private MigrationService migrationService;

  static List<Arguments> queriesWithExpectedVersions() {
    return List.of(
      Arguments.of(null, "0"),
      Arguments.of("{}", "0"),
      Arguments.of("This is Jason, not JSON", "0"),
      Arguments.of("{\"test\":{\"$eq\":\"foo\"}}", "0"),
      Arguments.of("{\"test\":{\"$i_am_invalid\":[]}}", "0"),
      Arguments.of("{\"_version\":\"0\"}", "0"),
      Arguments.of("{\"_version\":\"-1\"}", "-1"),
      Arguments.of("{\"_version\":\"sauce\"}", "sauce"),
      Arguments.of("{\"_version\":\"source-2\"}", "source-2"),
      Arguments.of("{\"_version\":\"target\"}", "target"),
      Arguments.of("{\"_version\":\"source\"}", "source"),
      Arguments.of("{\"_version\":\"source\",\"test\":{\"$eq\":\"foo\"}}", "source")
    );
  }

  @ParameterizedTest(name = "{0} has version={1}")
  @MethodSource("queriesWithExpectedVersions")
  void testAppliesToMatchingVersions(String fql, String expectedVersion) {
    assertThat(migrationService.getVersion(fql), is(expectedVersion));
  }

  @ParameterizedTest(name = "{0} with version={1} may need migration")
  @MethodSource("queriesWithExpectedVersions")
  void testIsMigrationNeeded(String fql, String version) {
    assertThat(
      migrationService.isMigrationNeeded(MigratableQueryInformation.builder().fqlQuery(fql).build()),
      is(not(version.equals(migrationService.getLatestVersion())))
    );
  }

  @ParameterizedTest(name = "{0} with version={1} may need migration")
  @MethodSource("queriesWithExpectedVersions")
  void testMigrateDoesNothingForUpToDate(String fql, String version) {
    if (version.equals(migrationService.getLatestVersion())) {
      assertThat(
        migrationService.migrate(MigratableQueryInformation.builder().fqlQuery(fql).build()).fqlQuery(),
        is(fql)
      );
      verifyNoInteractions(migrationStrategyRepository);
    }
  }

  @Test
  void testMigrationWorks() {
    String fql = "{\"_version\":\"source\",\"test\":{\"$eq\":\"foo\"}}";

    MigrationStrategy migrationStrategy = spy(new TestMigrationStrategy(true, 1));

    when(migrationStrategyRepository.getMigrationStrategies()).thenReturn(List.of(migrationStrategy));

    assertThat(
      migrationService.migrate(MigratableQueryInformation.builder().fqlQuery(fql).build()).fqlQuery(),
      is(
        MigratableQueryInformation
          .builder()
          .fqlQuery(fql.replace("source", migrationService.getLatestVersion()))
          .build()
          .fqlQuery()
      )
    );
    verify(migrationStrategy, times(1)).apply(fqlService, MigratableQueryInformation.builder().fqlQuery(fql).build());
  }

  @Test
  void testMigrationWorksWithMultipleIterations() {
    String fql = "{\"_version\":\"source\",\"test\":{\"$eq\":\"foo\"}}";

    MigrationStrategy migrationStrategy = spy(new TestMigrationStrategy(true, 3));

    when(migrationStrategyRepository.getMigrationStrategies()).thenReturn(List.of(migrationStrategy));

    assertThat(
      migrationService.migrate(MigratableQueryInformation.builder().fqlQuery(fql).build()).fqlQuery(),
      is(
        MigratableQueryInformation
          .builder()
          .fqlQuery(fql.replace("source", migrationService.getLatestVersion()))
          .build()
          .fqlQuery()
      )
    );
    verify(migrationStrategy, times(3)).apply(fqlService, MigratableQueryInformation.builder().fqlQuery(fql).build());
  }

  @Test
  void testMigrationOnlyAppliesApplicable() {
    String fql = "{\"_version\":\"source\",\"test\":{\"$eq\":\"foo\"}}";

    MigrationStrategy migrationStrategyApplicable = spy(new TestMigrationStrategy(true, 1));
    MigrationStrategy migrationStrategyInapplicable = spy(new TestMigrationStrategy(false, 0));

    when(migrationStrategyRepository.getMigrationStrategies())
      .thenReturn(List.of(migrationStrategyInapplicable, migrationStrategyApplicable, migrationStrategyInapplicable));

    assertThat(
      migrationService.migrate(MigratableQueryInformation.builder().fqlQuery(fql).build()).fqlQuery(),
      is(
        MigratableQueryInformation
          .builder()
          .fqlQuery(fql.replace("source", migrationService.getLatestVersion()))
          .build()
          .fqlQuery()
      )
    );
    verify(migrationStrategyApplicable, times(1))
      .apply(fqlService, MigratableQueryInformation.builder().fqlQuery(fql).build());
    verify(migrationStrategyApplicable, times(1)).getLabel();
    verify(migrationStrategyApplicable, times(1)).applies(anyString());
    verify(migrationStrategyInapplicable, times(2)).applies(anyString());

    verifyNoMoreInteractions(migrationStrategyApplicable, migrationStrategyInapplicable);
  }

  @RequiredArgsConstructor
  private class TestMigrationStrategy implements MigrationStrategy {

    final boolean applies;
    final int requiredCount;
    int count = 0;

    @Override
    public boolean applies(String version) {
      return applies;
    }

    @Override
    public MigratableQueryInformation apply(
      FqlService fqlService,
      MigratableQueryInformation migratableQueryInformation
    ) {
      if (++count != requiredCount) {
        return migratableQueryInformation;
      }
      return MigratableQueryInformation
        .builder()
        .fqlQuery(migratableQueryInformation.fqlQuery().replace("source", migrationService.getLatestVersion()))
        .build();
    }

    @Override
    public String getLabel() {
      return "Test";
    }
  }
}
