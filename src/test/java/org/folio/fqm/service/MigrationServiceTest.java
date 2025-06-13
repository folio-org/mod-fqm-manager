package org.folio.fqm.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import org.folio.fql.service.FqlService;
import org.folio.fqm.config.MigrationConfiguration;
import org.folio.fqm.exception.MigrationQueryChangedException;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationStrategyRepository;
import org.folio.fqm.migration.warnings.Warning;
import org.folio.spring.i18n.service.TranslationService;
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

  @Spy
  private MigrationConfiguration migrationConfiguration;

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

  // Common test FQL query
  private static final String TEST_FQL = "{\"_version\":\"source\",\"test\":{\"$eq\":\"foo\"}}";

  @Test
  void testThrowExceptionIfQueryNeedsMigrationOnlyVersionChanges() {
    // Use the default strategy that only changes the version
    MigrationStrategy migrationStrategy = spy(new TestMigrationStrategy(true, 1));
    when(migrationStrategyRepository.getMigrationStrategies()).thenReturn(List.of(migrationStrategy));

    // This should not throw an exception because only the version changes
    migrationService.throwExceptionIfQueryNeedsMigration(MigratableQueryInformation.builder().fqlQuery(TEST_FQL).build());

    verify(migrationStrategy, times(1)).apply(fqlService, MigratableQueryInformation.builder().fqlQuery(TEST_FQL).build());
  }

  @Test
  void testThrowExceptionIfQueryNeedsMigrationThrowsExceptionWhenEntityTypeIdChanges() {
    // Test when entityTypeId changes
    testMigrationException(
      // Strategy that changes entityTypeId
      (fqlService, info) -> MigratableQueryInformation.builder()
        .fqlQuery(info.fqlQuery().replace("source", migrationService.getLatestVersion()))
        .entityTypeId(UUID.randomUUID())
        .build(),
      // Input query
      MigratableQueryInformation.builder().fqlQuery(TEST_FQL).build(),
      // Verification
      exception -> assertThat(exception.getMigratedQueryInformation().fqlQuery().contains(migrationService.getLatestVersion()), is(true))
    );
  }

  @Test
  void testThrowExceptionIfQueryNeedsMigrationThrowsExceptionWhenFieldsChange() {
    // Test when fields change
    testMigrationException(
      // Strategy that changes fields
      (fqlService, info) -> MigratableQueryInformation.builder()
        .fqlQuery(info.fqlQuery().replace("source", migrationService.getLatestVersion()))
        .fields(List.of("newField1", "newField2"))
        .build(),
      // Input query with fields
      MigratableQueryInformation.builder()
        .fqlQuery(TEST_FQL)
        .fields(List.of("field1", "field2"))
        .build(),
      // Verification
      exception -> {
        assertThat(exception.getMigratedQueryInformation().fqlQuery().contains(migrationService.getLatestVersion()), is(true));
        assertThat(exception.getMigratedQueryInformation().fields(), is(List.of("newField1", "newField2")));
      }
    );
  }

  @Test
  void testThrowExceptionIfQueryNeedsMigrationThrowsExceptionWhenWarningsChange() {
    // Test when warnings change
    testMigrationException(
      // Strategy that adds a warning
      (fqlService, info) -> MigratableQueryInformation.builder()
        .fqlQuery(info.fqlQuery().replace("source", migrationService.getLatestVersion()))
        .warning(new Warning() {
          @Override
          public WarningType getType() {
            return WarningType.DEPRECATED_FIELD;
          }

          @Override
          public String getDescription(TranslationService translationService) {
            return "Test warning";
          }
        })
        .build(),
      // Input query
      MigratableQueryInformation.builder().fqlQuery(TEST_FQL).build(),
      // Verification
      exception -> {
        assertThat(exception.getMigratedQueryInformation().fqlQuery().contains(migrationService.getLatestVersion()), is(true));
        assertThat(exception.getMigratedQueryInformation().warnings().size(), is(1));
        assertThat(exception.getMigratedQueryInformation().warnings().get(0).getType(), is(Warning.WarningType.DEPRECATED_FIELD));
      }
    );
  }

  @Test
  void testThrowExceptionIfQueryIsUpToDateThrowsExceptionWhenFqlQueryContentChanges() {
    // Test when FQL query content changes
    testMigrationException(
      // Strategy that changes FQL query content
      (fqlService, info) -> MigratableQueryInformation.builder()
        .fqlQuery("{\"_version\":\"" + migrationService.getLatestVersion() + "\",\"newTest\":{\"$eq\":\"bar\"}}")
        .build(),
      // Input query
      MigratableQueryInformation.builder().fqlQuery(TEST_FQL).build(),
      // Verification
      exception -> {
        assertThat(exception.getMigratedQueryInformation().fqlQuery().contains(migrationService.getLatestVersion()), is(true));
        assertThat(exception.getMigratedQueryInformation().fqlQuery().contains("newTest"), is(true));
        assertThat(exception.getMigratedQueryInformation().fqlQuery().contains("bar"), is(true));
      }
    );
  }

  @Test
  void testOnlyVersionChangedWithNullFqlQueries() {
    // Create a strategy that doesn't change the FQL query (keeps it null)
    MigrationStrategy migrationStrategy = spy(new TestMigrationStrategy(true, 1) {
      @Override
      public MigratableQueryInformation apply(
        FqlService fqlService,
        MigratableQueryInformation migratableQueryInformation
      ) {
        // Return a new object with the same null FQL query
        return MigratableQueryInformation.builder()
          .entityTypeId(migratableQueryInformation.entityTypeId())
          .fields(migratableQueryInformation.fields())
          .fqlQuery("{\"_version\":\"" + migrationService.getLatestVersion() + "\"}")
          .build();
      }
    });

    when(migrationStrategyRepository.getMigrationStrategies()).thenReturn(List.of(migrationStrategy));

    // Create input with null FQL query
    MigratableQueryInformation input = MigratableQueryInformation.builder()
      .entityTypeId(UUID.randomUUID())
      .fields(List.of("field1", "field2"))
      .fqlQuery(null)
      .build();

    // This should not throw an exception because both FQL queries are null
    assertThrows(MigrationQueryChangedException.class, () -> migrationService.throwExceptionIfQueryNeedsMigration(input));
  }

  @Test
  void testOnlyVersionChangedWithOneNullFqlQuery() {
    // Test with one null FQL query and one non-null FQL query
    testMigrationException(
      // Strategy that changes a null FQL query to a non-null one
      (fqlService, info) -> MigratableQueryInformation.builder()
        .entityTypeId(info.entityTypeId())
        .fields(info.fields())
        .fqlQuery("{\"_version\":\"" + migrationService.getLatestVersion() + "\",\"test\":{\"$eq\":\"foo\"}}")
        .build(),
      // Input query with null FQL query
      MigratableQueryInformation.builder()
        .entityTypeId(UUID.randomUUID())
        .fields(List.of("field1", "field2"))
        .fqlQuery(null)
        .build(),
      // Verification
      exception -> {
        assertThat(exception.getMigratedQueryInformation().fqlQuery(), is(not(nullValue())));
        assertThat(exception.getMigratedQueryInformation().fqlQuery().contains(migrationService.getLatestVersion()), is(true));
      }
    );
  }

  /**
   * Helper method to test migration exceptions.
   *
   * @param strategyApply The function to apply in the migration strategy
   * @param inputQuery The input query to migrate
   * @param exceptionVerifier A consumer that verifies the exception
   */
  private void testMigrationException(
    BiFunction<FqlService, MigratableQueryInformation, MigratableQueryInformation> strategyApply,
    MigratableQueryInformation inputQuery,
    Consumer<MigrationQueryChangedException> exceptionVerifier
  ) {
    // Create a strategy with the provided apply function
    MigrationStrategy migrationStrategy = spy(new TestMigrationStrategy(true, 1) {
      @Override
      public MigratableQueryInformation apply(
        FqlService fqlService,
        MigratableQueryInformation migratableQueryInformation
      ) {
        return strategyApply.apply(fqlService, migratableQueryInformation);
      }
    });

    when(migrationStrategyRepository.getMigrationStrategies()).thenReturn(List.of(migrationStrategy));

    // This should throw an exception because more than just the version changes
    MigrationQueryChangedException exception = assertThrows(
      MigrationQueryChangedException.class,
      () -> migrationService.throwExceptionIfQueryNeedsMigration(inputQuery)
    );

    // Verify the exception using the provided verifier
    exceptionVerifier.accept(exception);
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
