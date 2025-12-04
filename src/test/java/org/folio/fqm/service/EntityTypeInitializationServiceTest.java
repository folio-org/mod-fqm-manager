package org.folio.fqm.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.util.PSQLException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

@ExtendWith(MockitoExtension.class)
class EntityTypeInitializationServiceTest {

  private Map<UUID, Boolean> validEntityMap;
  private Map<String, Boolean> validViewMap;

  @Mock
  private EntityTypeRepository entityTypeRepository;

  @Mock
  private EntityTypeService entityTypeService;

  @Mock
  private FolioExecutionContext folioExecutionContext;

  @Mock
  private ResourcePatternResolver resourceResolver;

  @Mock
  private SourceViewService sourceViewService;

  @Mock
  private CrossTenantQueryService crossTenantQueryService;

  @Mock
  private FolioSpringLiquibase folioSpringLiquibase;

  @InjectMocks
  private EntityTypeInitializationService entityTypeInitializationService;

  @BeforeEach
  void setup() {
    lenient().when(folioExecutionContext.getTenantId()).thenReturn("tenant");

    validEntityMap = new HashMap<>();
    validEntityMap.put(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), true);
    validEntityMap.put(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), false);

    validViewMap = new HashMap<>();
    validViewMap.put("valid_view", true);
    validViewMap.put("invalid_view", false);
  }

  static List<Arguments> centralTenantResolutionCases() {
    return List.of(
      // passed in central tenant ID; response from CTQS; expected safe central tenant ID; expected final central tenant ID
      // not ECS
      Arguments.of(null, null, "tenant", "${central_tenant_id}"),
      // ECS, but not passed in
      Arguments.of(null, "central", "central", "central"),
      // ECS, passed in
      Arguments.of("central", null, "central", "central")
    );
  }

  @ParameterizedTest
  @MethodSource("centralTenantResolutionCases")
  void testCentralTenantResolution(
    String providedId,
    String ctqsResponse,
    String expectedSafeId,
    String expectedFinalId
  ) {
    lenient().when(crossTenantQueryService.getCentralTenantId()).thenReturn(ctqsResponse);

    Pair<String, String> result = entityTypeInitializationService.getCentralTenantIdSafely(providedId);

    assertThat(result.getLeft(), is(expectedSafeId));
    assertThat(result.getRight(), is(expectedFinalId));
  }

  @ParameterizedTest
  @ValueSource(booleans = { true, false })
  void testEntityTypeAvailabilityCacheIsUsed(boolean testValue) {
    Map<UUID, Boolean> map = spy(new HashMap<>());
    map.put(UUID.fromString("11111111-1111-1111-1111-111111111111"), testValue);

    lenient().doThrow(new RuntimeException("Cache should not be modified")).when(map).put(any(), any());

    assertThat(
      entityTypeInitializationService.checkEntityTypeIsAvailableWithCache(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        null,
        map,
        null
      ),
      is(testValue)
    );
  }

  @Test
  void testEntityTypeAvailabilityFailsIfUnknownId() {
    assertThat(
      entityTypeInitializationService.checkEntityTypeIsAvailableWithCache(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        Map.of(),
        new HashMap<>(),
        null
      ),
      is(false)
    );
  }

  static List<Arguments> entityTypeAvailabilityCases() {
    return List.of(
      // null source list
      Arguments.of(new EntityType(), true),
      // empty source list
      Arguments.of(new EntityType().sources(List.of()), true),
      // unknown source type
      Arguments.of(new EntityType().addSourcesItem(new EntityTypeSource()), false),
      // valid sources
      Arguments.of(
        new EntityType()
          .addSourcesItem(
            new EntityTypeSourceEntityType().targetId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
          )
          .addSourcesItem(new EntityTypeSourceDatabase().target("valid_view")),
        true
      ),
      // invalid entity type source
      Arguments.of(
        new EntityType()
          .addSourcesItem(
            new EntityTypeSourceEntityType().targetId(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
          )
          .addSourcesItem(new EntityTypeSourceDatabase().target("valid_view")),
        false
      ),
      // invalid DB source
      Arguments.of(new EntityType().addSourcesItem(new EntityTypeSourceDatabase().target("invalid_view")), false)
    );
  }

  @ParameterizedTest
  @MethodSource("entityTypeAvailabilityCases")
  void testEntityTypeAvailability(EntityType entityType, boolean expectedAvailability) {
    assertThat(
      entityTypeInitializationService.checkEntityTypeIsAvailableWithCache(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        Map.of(UUID.fromString("11111111-1111-1111-1111-111111111111"), entityType),
        validEntityMap,
        validViewMap
      ),
      is(expectedAvailability)
    );
    assertThat(validEntityMap.get(UUID.fromString("11111111-1111-1111-1111-111111111111")), is(expectedAvailability));
  }

  @Test
  void testRunWithRecoveryNoFailure() {
    assertThat(entityTypeInitializationService.runWithRecovery(null, () -> "success"), is("success"));

    verifyNoInteractions(sourceViewService);
  }

  @Test
  void testRunWithRecoveryExternalFailure() {
    RuntimeException expected = new RuntimeException("simulated failure not from entity types");

    RuntimeException actual = assertThrows(
      RuntimeException.class,
      () ->
        entityTypeInitializationService.runWithRecovery(
          null,
          () -> {
            throw expected;
          })
    );
    assertThat(actual, is(expected));

    verifyNoInteractions(sourceViewService);
  }

  @Test
  void testRunWithRecoveryMiscDBFailure() {
    DataAccessException exception = mock(DataAccessException.class);
    when(exception.getCause(PSQLException.class)).thenReturn(null);

    RuntimeException actual = assertThrows(
      RuntimeException.class,
      () ->
        entityTypeInitializationService.runWithRecovery(
          null,
          () -> {
            throw exception;
          })
    );
    assertThat(actual, is(exception));

    verifyNoInteractions(sourceViewService);
  }

  @Test
  void testRunWithRecoveryMiscPostgresFailure() {
    DataAccessException exception = mock(DataAccessException.class);
    PSQLException innerEx = mock(PSQLException.class);
    when(innerEx.getSQLState()).thenReturn("ZZZZZ"); // undefined_table
    when(exception.getCause(PSQLException.class)).thenReturn(innerEx);

    RuntimeException actual = assertThrows(
      RuntimeException.class,
      () ->
        entityTypeInitializationService.runWithRecovery(
          null,
          () -> {
            throw exception;
          })
    );
    assertThat(actual, is(exception));

    verifyNoInteractions(sourceViewService);
  }

  @Test
  void testRunWithRecoveryEntityFailure() throws IOException {
    DataAccessException ex = mock(DataAccessException.class);
    PSQLException innerEx = mock(PSQLException.class);
    when(innerEx.getSQLState()).thenReturn("42P01"); // undefined_table
    when(ex.getCause(PSQLException.class)).thenReturn(innerEx);

    when(resourceResolver.getResources(anyString())).thenReturn(new Resource[0]);

    AtomicBoolean hasThrown = new AtomicBoolean(false);

    assertThat(
      entityTypeInitializationService.runWithRecovery(
        new EntityType().sources(List.of()),
        () -> {
          if (hasThrown.compareAndSet(false, true)) {
            throw ex;
          } else {
            return "recovered";
          }
        }),
      is("recovered")
    );
  }

  @Test
  void testRunWithRecoveryIrrecoverableEntityFailure() throws IOException {
    DataAccessException ex = mock(DataAccessException.class);
    PSQLException innerEx = mock(PSQLException.class);
    when(innerEx.getSQLState()).thenReturn("42P01"); // undefined_table
    when(ex.getCause(PSQLException.class)).thenReturn(innerEx);

    when(resourceResolver.getResources(anyString())).thenThrow(new IOException());

    AtomicBoolean hasThrown = new AtomicBoolean(false);

    assertThat(
      entityTypeInitializationService.runWithRecovery(
        new EntityType().sources(List.of()),
        () -> {
          if (hasThrown.compareAndSet(false, true)) {
            throw ex;
          } else {
            return "recovered";
          }
        }),
      is("recovered")
    );
  }
}
