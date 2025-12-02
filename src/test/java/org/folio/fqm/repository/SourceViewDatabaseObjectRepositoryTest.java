package org.folio.fqm.repository;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.fqm.domain.SourceViewDefinition;
import org.folio.fqm.domain.SourceViewDefinition.SourceViewDependency;
import org.folio.fqm.domain.SourceViewRecord;
import org.folio.spring.FolioExecutionContext;
import org.jooq.Condition;
import org.jooq.Configuration;
import org.jooq.CreateViewAsStep;
import org.jooq.CreateViewFinalStep;
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
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({ "unchecked", "removal" })
@ExtendWith(MockitoExtension.class)
class SourceViewDatabaseObjectRepositoryTest {

  @Mock
  private SourceViewRecordRepository sourceViewRecordRepository;

  @Mock
  private FolioExecutionContext folioExecutionContext;

  @Mock
  private DSLContext jooqContext;

  @InjectMocks
  private SourceViewDatabaseObjectRepository sourceViewDatabaseObjectRepository;

  @BeforeEach
  void setup() {
    lenient().when(folioExecutionContext.getTenantId()).thenReturn("tenantId");
  }

  @ParameterizedTest
  @ValueSource(booleans = { true, false })
  void testDoesSourceViewExistInDatabase(boolean shouldExist) {
    when(jooqContext.selectOne()).thenAnswer(i -> mockFetchOne(shouldExist));

    assertThat(sourceViewDatabaseObjectRepository.doesSourceViewExistInDatabase("some_view"), is(shouldExist));

    verifyNoInteractions(sourceViewRecordRepository);
  }

  @Test
  void testInstallSingleSourceView() {
    SourceViewDefinition definition = new SourceViewDefinition("view", List.of(), "sql", "source");

    sourceViewDatabaseObjectRepository.installSingleSourceView(definition);

    ArgumentCaptor<SourceViewRecord> recordCaptor = ArgumentCaptor.forClass(SourceViewRecord.class);
    verify(sourceViewRecordRepository).save(recordCaptor.capture());
    SourceViewRecord savedRecord = recordCaptor.getValue();
    assertThat(savedRecord.getName(), is("view"));
    assertThat(savedRecord.getDefinition(), is("sql"));
    assertThat(savedRecord.getSourceFile(), is("source"));
    assertThat(savedRecord.getLastUpdated().toEpochMilli() - Instant.now().toEpochMilli(), is(lessThan(5000L)));
    verifyNoMoreInteractions(sourceViewRecordRepository);

    Pair<DSLContext, Runnable> transactionPair = verifyTransaction();
    DSLContext dsl = transactionPair.getLeft();
    Runnable runnable = transactionPair.getRight();

    CreateViewAsStep<Record> createViewAsStep = mock(CreateViewAsStep.class);
    when(dsl.createOrReplaceView("view")).thenReturn(createViewAsStep);
    when(createViewAsStep.as("sql")).thenReturn(mock(CreateViewFinalStep.class));

    runnable.run();

    verify(dsl).createOrReplaceView("view");
    verify(createViewAsStep).as("sql");
    verifyNoMoreInteractions(dsl, createViewAsStep);
  }

  @Test
  void testPersistViews() {
    Map<String, SourceViewDefinition> definitions = Map.ofEntries(
      Map.entry("view1", new SourceViewDefinition("view1", List.of(), "sql1", "source1")),
      Map.entry("view2", new SourceViewDefinition("view2", List.of(), "sql2", "source2")),
      Map.entry("view3", new SourceViewDefinition("view3", List.of(), "sql3", "source3"))
    );
    List<String> toInstall = List.of("view1");
    List<String> toUpdate = List.of("view2");
    List<String> toRemove = List.of("view3");

    sourceViewDatabaseObjectRepository.persistSourceViews(definitions, toInstall, toUpdate, toRemove);

    // verify transaction operations
    Pair<DSLContext, Runnable> transactionPair = verifyTransaction();
    DSLContext dsl = transactionPair.getLeft();
    Runnable runnable = transactionPair.getRight();

    when(dsl.dropViewIfExists(anyString())).thenReturn(mock(DropViewFinalStep.class));
    CreateViewAsStep<Record> createViewAsStep1 = mock(CreateViewAsStep.class);
    CreateViewAsStep<Record> createViewAsStep2 = mock(CreateViewAsStep.class);
    when(dsl.createOrReplaceView("view1")).thenReturn(createViewAsStep1);
    when(dsl.createOrReplaceView("view2")).thenReturn(createViewAsStep2);
    when(createViewAsStep1.as(anyString())).thenReturn(mock(CreateViewFinalStep.class));
    when(createViewAsStep2.as(anyString())).thenReturn(mock(CreateViewFinalStep.class));

    runnable.run();

    verify(dsl).dropViewIfExists("view2");
    verify(dsl).dropViewIfExists("view3");
    verify(dsl).createOrReplaceView("view1");
    verify(dsl).createOrReplaceView("view2");
    verify(createViewAsStep1).as("sql1");
    verify(createViewAsStep2).as("sql2");
    verifyNoMoreInteractions(dsl, createViewAsStep1, createViewAsStep2);

    // verify record repository interactions
    ArgumentCaptor<Collection<String>> deletedRecordsCaptor = ArgumentCaptor.forClass(Collection.class);
    ArgumentCaptor<Collection<SourceViewRecord>> installedRecordsCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(sourceViewRecordRepository, times(2)).deleteAllById(deletedRecordsCaptor.capture());
    verify(sourceViewRecordRepository).saveAll(installedRecordsCaptor.capture());
    assertThat(
      deletedRecordsCaptor.getAllValues().stream().flatMap(Collection::stream).toList(),
      containsInAnyOrder("view2", "view3")
    );
    assertThat(
      installedRecordsCaptor.getValue().stream().map(SourceViewRecord::getName).toList(),
      containsInAnyOrder("view1", "view2")
    );
    verifyNoMoreInteractions(sourceViewRecordRepository);
  }

  @Test
  void testGetAvailableSourceViewDependencies() {
    when(jooqContext.select(any(List.class)))
      .thenAnswer(i ->
        mockFetchAvailableDependencies(List.of(Pair.of("schema1", "table1"), Pair.of("schema2", "table2")))
      );

    assertThat(
      sourceViewDatabaseObjectRepository.getAvailableSourceViewDependencies(),
      containsInAnyOrder(new SourceViewDependency("schema1", "table1"), new SourceViewDependency("schema2", "table2"))
    );
  }

  @Test
  void testPurgeMaterializedViewsWhenNoneArePresent() {
    when(jooqContext.select(any(Field.class))).thenAnswer(i -> mockFetchInstalledMaterializedViews(List.of()));

    sourceViewDatabaseObjectRepository.purgeMaterializedViewsIfPresent();

    verify(jooqContext).select(any(Field.class));
    verifyNoMoreInteractions(jooqContext);
  }

  @Test
  void testPurgeMaterializedViewsWhenPresent() {
    when(jooqContext.select(any(Field.class)))
      .thenAnswer(i -> mockFetchInstalledMaterializedViews(List.of("view1", "view2")));

    sourceViewDatabaseObjectRepository.purgeMaterializedViewsIfPresent();

    Pair<DSLContext, Runnable> transactionPair = verifyTransaction();
    DSLContext dsl = transactionPair.getLeft();
    Runnable runnable = transactionPair.getRight();

    when(dsl.dropMaterializedView(anyString())).thenReturn(mock(DropViewFinalStep.class));
    runnable.run();
    verify(dsl).dropMaterializedView("view1");
    verify(dsl).dropMaterializedView("view2");

    verify(jooqContext).select(any(Field.class));
    verifyNoMoreInteractions(jooqContext, dsl);
  }

  @Test
  void testVerifySourceViewMatchesDatabaseWhenAllMatch() throws IOException {
    when(jooqContext.select(any(Field.class))).thenAnswer(i -> mockFetchInstalledViews(Set.of("view1", "view2")));
    when(sourceViewRecordRepository.findAll())
      .thenReturn(
        List.of(
          new SourceViewRecord("view1", "def1", "src1", Instant.now()),
          new SourceViewRecord("view2", "def2", "src2", Instant.now())
        )
      );

    sourceViewDatabaseObjectRepository.verifySourceViewRecordsMatchesDatabase();

    verify(jooqContext).select(any(Field.class));
    verify(sourceViewRecordRepository).findAll();
    verifyNoMoreInteractions(jooqContext, sourceViewRecordRepository);
  }

  @Test
  void testVerifySourceViewMatchesDatabaseWhenSomeMissing() throws IOException {
    when(jooqContext.select(any(Field.class))).thenAnswer(i -> mockFetchInstalledViews(Set.of("view1", "view2")));
    when(sourceViewRecordRepository.findAll())
      .thenReturn(
        List.of(
          new SourceViewRecord("view1", "def1", "src1", Instant.now()),
          new SourceViewRecord("view2", "def2", "src2", Instant.now()),
          new SourceViewRecord("view3", "def2", "src2", Instant.now())
        )
      );

    sourceViewDatabaseObjectRepository.verifySourceViewRecordsMatchesDatabase();

    verify(jooqContext).select(any(Field.class));
    verify(sourceViewRecordRepository).findAll();
    verify(sourceViewRecordRepository).deleteAllById(argThat(containsInAnyOrder("view3")));
    verifyNoMoreInteractions(jooqContext, sourceViewRecordRepository);
  }

  @Test
  void testVerifySourceViewMatchesDatabaseWhenSomeUnexpected() throws IOException {
    when(jooqContext.select(any(Field.class)))
      .thenAnswer(i -> mockFetchInstalledViews(Set.of("view1", "view2", "view3")));
    when(sourceViewRecordRepository.findAll())
      .thenReturn(
        List.of(
          new SourceViewRecord("view1", "def1", "src1", Instant.now()),
          new SourceViewRecord("view2", "def2", "src2", Instant.now())
        )
      );

    sourceViewDatabaseObjectRepository.verifySourceViewRecordsMatchesDatabase();

    verify(jooqContext).select(any(Field.class));
    verify(sourceViewRecordRepository).findAll();

    Pair<DSLContext, Runnable> transactionPair = verifyTransaction();
    DSLContext dsl = transactionPair.getLeft();
    Runnable runnable = transactionPair.getRight();
    when(dsl.dropView(anyString())).thenReturn(mock(DropViewFinalStep.class));
    runnable.run();
    verify(dsl).dropView("view3");

    verifyNoMoreInteractions(jooqContext, sourceViewRecordRepository);
  }

  // creates a mocked DSLContext and captures the TransactionalRunnable
  private Pair<DSLContext, Runnable> verifyTransaction() {
    Configuration configuration = mock(Configuration.class);
    DSLContext dsl = mock(DSLContext.class);
    when(configuration.dsl()).thenReturn(dsl);

    ArgumentCaptor<TransactionalRunnable> transactionCaptor = ArgumentCaptor.forClass(TransactionalRunnable.class);
    verify(jooqContext).transaction(transactionCaptor.capture());
    TransactionalRunnable runnable = transactionCaptor.getValue();

    return Pair.of(
      dsl,
      () -> {
        try {
          runnable.run(configuration);
        } catch (Throwable e) {
          throw new RuntimeException(e);
        }
      }
    );
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
            Record r = mock(Record.class);
            when(r.get(any(Field.class)))
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
            return r;
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
}
