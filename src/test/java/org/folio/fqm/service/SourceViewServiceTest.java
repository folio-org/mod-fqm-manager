package org.folio.fqm.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyCollectionOf;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.folio.fqm.domain.SourceViewDefinition;
import org.folio.fqm.domain.SourceViewDefinition.SourceViewDependency;
import org.folio.fqm.domain.SourceViewRecord;
import org.folio.fqm.repository.SourceViewDatabaseObjectRepository;
import org.folio.fqm.repository.SourceViewRecordRepository;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

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
  private SourceViewDatabaseObjectRepository sourceViewDatabaseObjectRepository;

  @Mock
  private SourceViewRecordRepository sourceViewRecordRepository;

  @Mock
  private FolioExecutionContext folioExecutionContext;

  @Mock
  private ResourcePatternResolver resourceResolver;

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
    verifyNoInteractions(sourceViewRecordRepository);
  }

  @ParameterizedTest
  @ValueSource(booleans = { true, false })
  void testIsModFinanceInstalled(boolean expected) {
    when(sourceViewRecordRepository.existsById(SourceViewService.MOD_FINANCE_AVAILABILITY_INDICATOR_VIEW))
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
  @SuppressWarnings("unchecked")
  void testInstallAvailableSourceViewsMultipleIterations() throws IOException {
    mockDefinitions(List.of(DEFINITION_A, DEFINITION_A_A));

    when(sourceViewDatabaseObjectRepository.getAvailableSourceViewDependencies())
      .thenReturn(
        Set.of(new SourceViewDependency("a", "a")),
        Set.of(new SourceViewDependency("a", "a"), new SourceViewDependency("mod_fqm_manager", "view_a"))
      );
    when(sourceViewRecordRepository.findAll()).thenReturn(List.of(), List.of(RECORD_A));
    sourceViewService.installAvailableSourceViews(false);

    verify(sourceViewDatabaseObjectRepository, atLeastOnce()).getAvailableSourceViewDependencies();
    verify(sourceViewDatabaseObjectRepository)
      .persistSourceViews(
        any(),
        (Collection<String>) argThat(contains("view_a")),
        argThat(is(emptyCollectionOf(String.class))),
        argThat(is(emptyCollectionOf(String.class)))
      );
    verify(sourceViewDatabaseObjectRepository)
      .persistSourceViews(any(), (Collection<String>) argThat(contains("view_a_a")), eq(List.of()), eq(List.of()));
    verifyNoMoreInteractions(sourceViewDatabaseObjectRepository);
  }

  @Test
  void testVerifyAll() throws IOException {
    mockDefinitions(List.of());

    sourceViewService.verifyAll();

    verify(sourceViewDatabaseObjectRepository).purgeMaterializedViewsIfPresent();
    verify(sourceViewDatabaseObjectRepository).verifySourceViewRecordsMatchesDatabase();
    verify(sourceViewDatabaseObjectRepository)
      .persistSourceViews(
        any(),
        argThat(is(emptyCollectionOf(String.class))),
        argThat(is(emptyCollectionOf(String.class))),
        argThat(is(emptyCollectionOf(String.class)))
      );
  }

  @Test
  void testAttemptToHealSourceViewComplexExpression() {
    assertTrue(sourceViewService.attemptToHealSourceView("(select 1)"));

    verifyNoInteractions(sourceViewRecordRepository, sourceViewDatabaseObjectRepository);
  }

  @Test
  void testAttemptToHealSourceViewExistsInDatabaseButNoRecord() throws IOException {
    mockDefinitions(List.of());

    when(sourceViewRecordRepository.existsById("view")).thenReturn(false);
    when(sourceViewDatabaseObjectRepository.doesSourceViewExistInDatabase("view")).thenReturn(true);

    sourceViewService.attemptToHealSourceView("view");

    verify(sourceViewDatabaseObjectRepository, never()).persistSingleSourceView(any());
    verify(sourceViewDatabaseObjectRepository)
      .persistSourceViews(
        any(),
        argThat(is(emptyCollectionOf(String.class))),
        argThat(is(emptyCollectionOf(String.class))),
        argThat(is(emptyCollectionOf(String.class)))
      );
  }

  @Test
  void testAttemptToHealSourceViewExistsInDatabaseAndRecord() {
    when(sourceViewRecordRepository.existsById("view")).thenReturn(true);
    when(sourceViewDatabaseObjectRepository.doesSourceViewExistInDatabase("view")).thenReturn(true);

    assertTrue(sourceViewService.attemptToHealSourceView("view"));

    verify(sourceViewRecordRepository).existsById("view");
    verify(sourceViewDatabaseObjectRepository).doesSourceViewExistInDatabase("view");
    verifyNoMoreInteractions(sourceViewRecordRepository, sourceViewDatabaseObjectRepository);
  }

  @Test
  void testAttemptToHealSourceViewDoesNotExist() throws IOException {
    when(sourceViewRecordRepository.existsById("view")).thenReturn(false);
    when(sourceViewDatabaseObjectRepository.doesSourceViewExistInDatabase("view")).thenReturn(false);
    mockDefinitions(List.of());

    assertFalse(sourceViewService.attemptToHealSourceView("view"));

    verify(sourceViewRecordRepository).existsById("view");
    verify(sourceViewDatabaseObjectRepository).doesSourceViewExistInDatabase("view");
    verifyNoMoreInteractions(sourceViewRecordRepository, sourceViewDatabaseObjectRepository);
  }

  @Test
  void testAttemptToHealSourceViewHasUnavailableDependencies() throws IOException {
    when(sourceViewRecordRepository.existsById("view_a")).thenReturn(false);
    when(sourceViewDatabaseObjectRepository.doesSourceViewExistInDatabase("view_a")).thenReturn(false);
    mockDefinitions(List.of(DEFINITION_A));
    when(sourceViewDatabaseObjectRepository.getAvailableSourceViewDependencies()).thenReturn(Set.of());

    assertFalse(sourceViewService.attemptToHealSourceView("view_a"));

    verify(sourceViewRecordRepository).existsById("view_a");
    verify(sourceViewDatabaseObjectRepository).doesSourceViewExistInDatabase("view_a");
    verify(sourceViewDatabaseObjectRepository).getAvailableSourceViewDependencies();
    verifyNoMoreInteractions(sourceViewRecordRepository, sourceViewDatabaseObjectRepository);
  }

  @Test
  void testAttemptToHealSourceViewCreatesViewIfPossible() throws Throwable {
    when(sourceViewRecordRepository.existsById("view_a")).thenReturn(false);
    when(sourceViewDatabaseObjectRepository.doesSourceViewExistInDatabase("view_a")).thenReturn(false);
    mockDefinitions(List.of(DEFINITION_A));
    when(sourceViewDatabaseObjectRepository.getAvailableSourceViewDependencies())
      .thenReturn(Set.of(new SourceViewDependency("a", "a")));

    assertTrue(sourceViewService.attemptToHealSourceView("view_a"));

    verify(sourceViewRecordRepository).existsById("view_a");
    verify(sourceViewDatabaseObjectRepository).doesSourceViewExistInDatabase("view_a");
    verify(sourceViewDatabaseObjectRepository).getAvailableSourceViewDependencies();
    verify(sourceViewDatabaseObjectRepository).persistSingleSourceView(DEFINITION_A);
    verifyNoMoreInteractions(sourceViewRecordRepository, sourceViewDatabaseObjectRepository);
  }

  @Test
  void testAttemptToHealSourceViewException() throws IOException {
    when(sourceViewRecordRepository.existsById("view_a")).thenReturn(false);
    when(sourceViewDatabaseObjectRepository.doesSourceViewExistInDatabase("view_a")).thenReturn(false);

    // easy example that would trigger an IOException
    Resource resource = mock(Resource.class);
    when(resource.isReadable()).thenReturn(true);
    when(resource.getContentAsString(any())).thenThrow(new IOException());
    when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] { resource });

    assertFalse(sourceViewService.attemptToHealSourceView("view_a"));

    verify(sourceViewRecordRepository).existsById("view_a");
    verify(sourceViewDatabaseObjectRepository).doesSourceViewExistInDatabase("view_a");
    verifyNoMoreInteractions(sourceViewRecordRepository, sourceViewDatabaseObjectRepository);
  }

  private void mockDefinitions(List<SourceViewDefinition> definitions) throws IOException {
    Resource[] mockResources = definitions
      .stream()
      .map(d -> {
        Resource r = mock(Resource.class);
        when(r.isReadable()).thenReturn(true);
        try {
          URI uri = mock(URI.class);
          when(uri.toString()).thenReturn(d.sourceFilePath());
          when(r.getURI()).thenReturn(uri);
          when(r.getContentAsString(any())).thenReturn(objectMapper.writeValueAsString(d));
        } catch (IOException e) {
          fail();
        }
        return r;
      })
      .toArray(Resource[]::new);

    when(resourceResolver.getResources("classpath:/db/source-views/**/*.json")).thenReturn(new Resource[] {});
    when(resourceResolver.getResources("classpath:/db/source-views/**/*.json5")).thenReturn(mockResources);
  }
}
