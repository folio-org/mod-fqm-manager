package org.folio.fqm.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.fqm.domain.SourceViewDefinition;
import org.folio.fqm.domain.SourceViewDefinition.SourceViewDependency;
import org.folio.fqm.domain.SourceViewRecord;
import org.folio.fqm.repository.SourceViewRepository;
import org.folio.spring.FolioExecutionContext;
import org.jooq.Condition;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.DropViewFinalStep;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.SelectSelectStep;
import org.jooq.Table;
import org.jooq.TransactionalRunnable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class SourceViewServiceTest {

  private static final SourceViewDefinition DEFINITION_A = new SourceViewDefinition(
    "view_a",
    List.of(new SourceViewDependency("a", "a")),
    "SELECT 'a'",
    "a.json5"
  );
  private static final SourceViewDefinition DEFINITION_A_A = new SourceViewDefinition(
    "view_a_a",
    List.of(new SourceViewDependency("mod_fqm_manager", "view_a")),
    "SELECT 'a_a'",
    "a_a.json5"
  );
  private static final SourceViewDefinition DEFINITION_B = new SourceViewDefinition(
    "view_b",
    List.of(new SourceViewDependency("b", "b")),
    "SELECT 'b'",
    "b.json5"
  );

  private static final SourceViewRecord RECORD_A = new SourceViewRecord(
    "view_a",
    "SELECT 'a'",
    "a.json5",
    Instant.now()
  );
  private static final SourceViewRecord RECORD_B = new SourceViewRecord(
    "view_b",
    "SELECT 'b'",
    "b.json5",
    Instant.now()
  );

  @Mock
  private SourceViewRepository sourceViewRepository;

  @Mock
  private FolioExecutionContext folioExecutionContext;

  @Mock
  private ResourcePatternResolver resourceResolver;

  @Mock
  private DSLContext jooqContext;

  @Spy
  @InjectMocks
  private SourceViewService sourceViewService;

  private ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setup() {
    lenient().when(folioExecutionContext.getTenantId()).thenReturn("tenantId");
  }

  @ParameterizedTest
  @ValueSource(
    strings = {
      "(select 1)",
      "jsonb_array_elements(\"invoice_line_fund_distribution.invoice_line.invoice_lines\".jsonb -> \'fundDistributions\')",
    }
  )
  void testDoesSourceViewExistComplexExpressions(String expression) {
    assertTrue(sourceViewService.doesSourceViewExist(expression));
    verifyNoInteractions(sourceViewRepository);
  }

  @ParameterizedTest
  @ValueSource(booleans = { true, false })
  void testIsModFinanceInstalled(boolean expected) {
    when(sourceViewRepository.existsById(SourceViewService.MOD_FINANCE_AVAILABILITY_INDICATOR_VIEW))
      .thenReturn(expected);

    assertThat(sourceViewService.isModFinanceInstalled(), is(expected));
  }

  @Test
  void testFailureDeserializingSourceViewDefinition() throws IOException {
    Resource resource = mock(Resource.class);
    when(resource.isReadable()).thenReturn(true);
    when(resource.getContentAsString(any())).thenThrow(new IOException());
    when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] { resource });

    assertThrows(UncheckedIOException.class, () -> sourceViewService.getAvailableDefinitions());
  }

  @Test
  void testInstallAvailableSourceViewsMultipleIterations() throws IOException {
    mockDefinitions(List.of(DEFINITION_A, DEFINITION_A_A));

    SelectSelectStep<Record> initialAvailable = mockFetchAvailableDependencies(List.of(Pair.of("a", "a")));
    SelectSelectStep<Record> secondAvailable = mockFetchAvailableDependencies(
      List.of(Pair.of("a", "a"), Pair.of("mod_fqm_manager", "view_a"))
    );
    when(jooqContext.select(any(List.class))).thenReturn(initialAvailable, secondAvailable);

    sourceViewService.installAvailableSourceViews(false);

    verify(sourceViewService, times(2)).installSourceViews(any(), eq(false));
  }

  @ParameterizedTest
  @ValueSource(booleans = { true, false })
  void testDoesSourceViewExistInDatabase(boolean shouldExist) {
    when(jooqContext.selectOne()).thenAnswer(i -> mockFetchOne(shouldExist));

    assertThat(sourceViewService.doesSourceViewExistInDatabase("some_view"), is(shouldExist));
  }

  // only A is installed, but we think A and B are there. verify should remove B from our known list
  @Test
  void testVerifyWhenViewsAreMissingAfterInstall() throws IOException {
    mockDefinitions(List.of(DEFINITION_A, DEFINITION_B));
    when(jooqContext.select(any(List.class))).thenAnswer(i -> mockFetchAvailableDependencies(List.of()));

    when(jooqContext.select(any(Field.class)))
      .thenAnswer(i -> {
        Field<?> field = i.getArgument(0);
        if (field.getName().equals("table_name")) {
          return mockFetchInstalledViews(Set.of("view_a"));
        } else {
          return mockFetchInstalledMaterializedViews(List.of());
        }
      });
    when(sourceViewRepository.findAll()).thenReturn(List.of(RECORD_A, RECORD_B));

    sourceViewService.verifyAll();

    ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
    verify(sourceViewRepository, atLeastOnce()).deleteAllById(captor.capture());
    assertThat(captor.getAllValues().get(0), containsInAnyOrder("view_b"));

    verify(jooqContext, atLeastOnce()).select(any(Field.class));
    verifyNoMoreInteractions(jooqContext); // no transactions (used for dropping views/matviews)
  }

  // A and B are installed, but we only think A is there. verify should remove B from DB
  @Test
  void testVerifyWhenExtraViewsAreInstalled() throws Throwable {
    mockDefinitions(List.of(DEFINITION_A, DEFINITION_B));
    when(jooqContext.select(any(List.class))).thenAnswer(i -> mockFetchAvailableDependencies(List.of()));

    when(jooqContext.select(any(Field.class)))
      .thenAnswer(i -> {
        Field<?> field = i.getArgument(0);
        if (field.getName().equals("table_name")) {
          return mockFetchInstalledViews(Set.of("view_a", "view_b"));
        } else {
          return mockFetchInstalledMaterializedViews(List.of());
        }
      });
    when(sourceViewRepository.findAll()).thenReturn(List.of(RECORD_A));

    sourceViewService.verifyAll();

    ArgumentCaptor<TransactionalRunnable> transactionCaptor = ArgumentCaptor.forClass(TransactionalRunnable.class);
    verify(jooqContext, times(1)).transaction(transactionCaptor.capture());

    Configuration transaction = mock(Configuration.class);
    DSLContext dsl = mock(DSLContext.class);
    when(transaction.dsl()).thenReturn(dsl);
    when(dsl.dropView(anyString())).thenReturn(mock(DropViewFinalStep.class));

    transactionCaptor.getValue().run(transaction);

    ArgumentCaptor<String> dropViewCaptor = ArgumentCaptor.forClass(String.class);
    verify(dsl, atLeastOnce()).dropView(dropViewCaptor.capture());
    assertThat(dropViewCaptor.getAllValues(), containsInAnyOrder("view_b"));
  }

  @Test
  void testVerifyCleansUpAllMaterializedViews() throws Throwable {
    mockDefinitions(List.of());
    when(jooqContext.select(any(List.class))).thenAnswer(i -> mockFetchAvailableDependencies(List.of()));

    when(jooqContext.select(any(Field.class)))
      .thenAnswer(i -> {
        Field<?> field = i.getArgument(0);
        if (field.getName().equals("table_name")) {
          return mockFetchInstalledViews(Set.of());
        } else {
          return mockFetchInstalledMaterializedViews(List.of("view_mat"));
        }
      });
    when(sourceViewRepository.findAll()).thenReturn(List.of());

    sourceViewService.verifyAll();

    ArgumentCaptor<TransactionalRunnable> transactionCaptor = ArgumentCaptor.forClass(TransactionalRunnable.class);
    verify(jooqContext, times(1)).transaction(transactionCaptor.capture());

    Configuration transaction = mock(Configuration.class);
    DSLContext dsl = mock(DSLContext.class);
    when(transaction.dsl()).thenReturn(dsl);
    when(dsl.dropMaterializedView(anyString())).thenReturn(mock(DropViewFinalStep.class));

    transactionCaptor.getValue().run(transaction);

    ArgumentCaptor<String> dropViewCaptor = ArgumentCaptor.forClass(String.class);
    verify(dsl, atLeastOnce()).dropMaterializedView(dropViewCaptor.capture());
    assertThat(dropViewCaptor.getAllValues(), containsInAnyOrder("view_mat"));
  }

  @Test
  void testAttemptToHealSourceViewComplexExpression() {
    assertTrue(sourceViewService.attemptToHealSourceView("(select 1)"));

    verifyNoInteractions(sourceViewRepository, jooqContext);
  }

  @Test
  void testAttemptToHealSourceViewExistsInDatabaseButNoRecord() throws IOException {
    mockDefinitions(List.of());
    when(jooqContext.select(any(List.class))).thenAnswer(i -> mockFetchAvailableDependencies(List.of()));

    when(sourceViewRepository.existsById("view")).thenReturn(false);
    when(jooqContext.selectOne()).thenAnswer(i -> mockFetchOne(true));
    when(jooqContext.select(any(Field.class)))
      .thenAnswer(i -> {
        Field<?> field = i.getArgument(0);
        if (field.getName().equals("table_name")) {
          return mockFetchInstalledViews(Set.of());
        } else {
          return mockFetchInstalledMaterializedViews(List.of());
        }
      });

    sourceViewService.attemptToHealSourceView("view");

    verify(sourceViewRepository, never()).save(any());
    verify(jooqContext, never()).transaction(any(TransactionalRunnable.class));
  }

  @Test
  void testAttemptToHealSourceViewExistsInDatabaseAndRecord() throws IOException {
    when(sourceViewRepository.existsById("view")).thenReturn(true);
    when(jooqContext.selectOne()).thenAnswer(i -> mockFetchOne(true));

    assertTrue(sourceViewService.attemptToHealSourceView("view"));

    verify(sourceViewRepository).existsById("view");
    verify(jooqContext).selectOne();
    verifyNoMoreInteractions(sourceViewRepository, jooqContext);
  }

  @Test
  void testAttemptToHealSourceViewDoesNotExist() throws IOException {
    when(sourceViewRepository.existsById("view")).thenReturn(false);
    when(jooqContext.selectOne()).thenAnswer(i -> mockFetchOne(false));
    mockDefinitions(List.of());

    assertFalse(sourceViewService.attemptToHealSourceView("view"));

    verify(sourceViewRepository).existsById("view");
    verify(jooqContext).selectOne();
    verifyNoMoreInteractions(sourceViewRepository, jooqContext);
  }

  @Test
  void testAttemptToHealSourceViewHasUnavailableDependencies() throws IOException {
    when(sourceViewRepository.existsById("view_a")).thenReturn(false);
    when(jooqContext.selectOne()).thenAnswer(i -> mockFetchOne(false));
    mockDefinitions(List.of(DEFINITION_A));
    when(jooqContext.select(any(List.class))).thenAnswer(i -> mockFetchAvailableDependencies(List.of()));

    assertFalse(sourceViewService.attemptToHealSourceView("view_a"));

    verify(sourceViewRepository).existsById("view_a");
    verify(jooqContext).selectOne();
    verify(jooqContext).select(any(List.class));
    verifyNoMoreInteractions(sourceViewRepository, jooqContext);
  }

  @Test
  void testAttemptToHealSourceViewCreatesViewIfPossible() throws IOException {
    when(sourceViewRepository.existsById("view_a")).thenReturn(false);
    when(jooqContext.selectOne()).thenAnswer(i -> mockFetchOne(false));
    mockDefinitions(List.of(DEFINITION_A));
    when(jooqContext.select(any(List.class)))
      .thenAnswer(i -> mockFetchAvailableDependencies(List.of(Pair.of("a", "a"))));

    assertTrue(sourceViewService.attemptToHealSourceView("view_a"));

    verify(sourceViewRepository).existsById("view_a");
    verify(sourceViewRepository).save(any());
    verify(jooqContext).selectOne();
    verify(jooqContext).select(any(List.class));
    verify(jooqContext).transaction(any(TransactionalRunnable.class));
    verifyNoMoreInteractions(sourceViewRepository, jooqContext);
  }

  @Test
  void testAttemptToHealSourceViewException() throws IOException {
    when(sourceViewRepository.existsById("view_a")).thenReturn(false);
    when(jooqContext.selectOne()).thenAnswer(i -> mockFetchOne(false));

    // easy example that would trigger an IOException
    Resource resource = mock(Resource.class);
    when(resource.isReadable()).thenReturn(true);
    when(resource.getContentAsString(any())).thenThrow(new IOException());
    when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] { resource });

    assertFalse(sourceViewService.attemptToHealSourceView("view_a"));

    verify(sourceViewRepository).existsById("view_a");
    verify(jooqContext).selectOne();
    verifyNoMoreInteractions(sourceViewRepository, jooqContext);
  }

  private SelectSelectStep<Record1<Integer>> mockFetchOne(boolean shouldHaveResult) {
    SelectSelectStep<Record1<Integer>> selection = mock(SelectSelectStep.class);
    SelectJoinStep<Record1<Integer>> selection2 = mock(SelectJoinStep.class);
    when(selection.from(any(Table.class))).thenReturn(selection2);
    SelectConditionStep<Record1<Integer>> selection3 = mock(SelectConditionStep.class);
    when(selection2.where(any(List.class))).thenReturn(selection3);

    if (shouldHaveResult) {
      when(selection3.fetchOne()).thenReturn(mock(Record1.class));
    } else {
      when(selection3.fetchOne()).thenReturn(null);
    }

    return selection;
  }

  private SelectSelectStep<Record> mockFetchAvailableDependencies(List<Pair<String, String>> values) {
    SelectSelectStep<Record> selection = mock(SelectSelectStep.class);
    SelectJoinStep<Record> selection2 = mock(SelectJoinStep.class);
    when(selection.from(any(Table.class))).thenReturn(selection2);
    SelectConditionStep<Record> selection3 = mock(SelectConditionStep.class);
    when(selection2.where(any(Condition.class))).thenReturn(selection3);
    Result<Record> result = mock(Result.class);
    when(selection3.fetch()).thenReturn(result);

    when(result.stream())
      .thenAnswer(i ->
        values
          .stream()
          .map(v -> {
            Record record = mock(Record.class);
            when(record.get(any(Field.class)))
              .thenAnswer(invocation -> {
                Field<?> field = invocation.getArgument(0);
                if (field.getName().equals("table_schema")) {
                  return v.getLeft();
                } else if (field.getName().equals("table_name")) {
                  return v.getRight();
                } else {
                  fail();
                  return null;
                }
              });
            return record;
          })
      );

    return selection;
  }

  private SelectSelectStep<Record1<String>> mockFetchInstalledViews(Set<String> values) {
    SelectSelectStep<Record1<String>> selection = mock(SelectSelectStep.class);
    SelectJoinStep<Record1<String>> selection2 = mock(SelectJoinStep.class);
    when(selection.from(any(Table.class))).thenReturn(selection2);
    SelectConditionStep<Record1<String>> selection3 = mock(SelectConditionStep.class);
    when(selection2.where(any(List.class))).thenReturn(selection3);
    when(selection3.fetchSet(any(Field.class))).thenReturn(values);

    return selection;
  }

  private SelectSelectStep<Record1<String>> mockFetchInstalledMaterializedViews(List<String> values) {
    SelectSelectStep<Record1<String>> selection = mock(SelectSelectStep.class);
    SelectJoinStep<Record1<String>> selection2 = mock(SelectJoinStep.class);
    when(selection.from(any(Table.class))).thenReturn(selection2);
    SelectConditionStep<Record1<String>> selection3 = mock(SelectConditionStep.class);
    when(selection2.where(any(Condition.class))).thenReturn(selection3);
    when(selection3.fetch(any(Field.class))).thenReturn(values);

    return selection;
  }

  private void mockDefinitions(List<SourceViewDefinition> definitions) throws IOException {
    Resource[] mockResources = definitions
      .stream()
      .map(d -> {
        try {
          return objectMapper.writeValueAsString(d);
        } catch (JsonProcessingException e) {
          fail();
          return null;
        }
      })
      .map(s -> {
        Resource r = mock(Resource.class);
        when(r.isReadable()).thenReturn(true);
        try {
          when(r.getURI()).thenReturn(new URI(""));
          when(r.getContentAsString(any())).thenReturn(s);
        } catch (IOException | URISyntaxException e) {
          fail();
        }
        return r;
      })
      .toArray(Resource[]::new);

    when(resourceResolver.getResources("classpath:/db/source-views/**/*.json")).thenReturn(new Resource[] {});
    when(resourceResolver.getResources("classpath:/db/source-views/**/*.json5")).thenReturn(mockResources);
  }
}
