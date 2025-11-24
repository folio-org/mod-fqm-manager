package org.folio.fqm.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import liquibase.exception.LiquibaseException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.SelectSelectStep;
import org.jooq.Table;
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
  private ObjectMapper objectMapper;

  @Mock
  private ResourcePatternResolver resourceResolver;

  @Mock
  private CrossTenantQueryService crossTenantQueryService;

  @Mock
  private DSLContext readerJooqContext;

  @Mock
  private FolioSpringLiquibase folioSpringLiquibase;

  @InjectMocks
  private EntityTypeInitializationService entityTypeInitializationService;

  @BeforeEach
  void setup() {
    lenient().when(folioExecutionContext.getTenantId()).thenReturn("tenantId");

    validEntityMap = new HashMap<>();
    validEntityMap.put(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), true);
    validEntityMap.put(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), false);

    validViewMap = new HashMap<>();
    validViewMap.put("valid_view", true);
    validViewMap.put("invalid_view", false);
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
        null,
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
        null,
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
        validViewMap,
        null
      ),
      is(expectedAvailability)
    );
    assertThat(validEntityMap.get(UUID.fromString("11111111-1111-1111-1111-111111111111")), is(expectedAvailability));
  }

  @ParameterizedTest
  @ValueSource(booleans = { true, false })
  void testSourceViewAvailabilityCacheIsUsed(boolean testValue) {
    Map<String, Boolean> map = spy(new HashMap<>());
    map.put("view", testValue);

    lenient().doThrow(new RuntimeException("Cache should not be modified")).when(map).put(any(), any());

    assertThat(entityTypeInitializationService.checkSourceViewIsAvailableWithCache("view", map, null), is(testValue));
  }

  @Test
  void testSourceViewAvailabilityIgnoresComplexExpressions() {
    assertThat(entityTypeInitializationService.checkSourceViewIsAvailable("(SELECT 1)", null), is(true));
  }

  @Test
  void testSourceViewAvailabilityWithSuccessfulQuery() {
    mockFetchOne(true);

    assertThat(entityTypeInitializationService.checkSourceViewIsAvailable("view", null), is(true));

    verify(readerJooqContext, times(1)).selectOne();
  }

  @Test
  void testSourceViewAvailabilityWithFailingQueryNoLiquibase() {
    mockFetchOne(false);

    assertThat(entityTypeInitializationService.checkSourceViewIsAvailable("view", new AtomicBoolean(true)), is(false));

    verify(readerJooqContext, times(1)).selectOne();
  }

  @Test
  void testSourceViewAvailabilityWithFailingAndLiquibaseRunAndSuccess() throws LiquibaseException {
    mockFetchOne(false);

    doAnswer(i -> {
        mockFetchOne(true);
        return null;
      })
      .when(folioSpringLiquibase)
      .performLiquibaseUpdate();

    AtomicBoolean bool = new AtomicBoolean(false);
    assertThat(entityTypeInitializationService.checkSourceViewIsAvailable("view", bool), is(true));
    assertThat(bool.get(), is(true));

    verify(folioSpringLiquibase, times(1)).performLiquibaseUpdate();
    verify(readerJooqContext, times(2)).selectOne();
  }

  @Test
  void testSourceViewAvailabilityWithFailingAndLiquibaseRunAndStillFailing() throws LiquibaseException {
    mockFetchOne(false);

    AtomicBoolean bool = new AtomicBoolean(false);
    assertThat(entityTypeInitializationService.checkSourceViewIsAvailable("view", bool), is(false));
    assertThat(bool.get(), is(true));

    verify(folioSpringLiquibase, times(1)).performLiquibaseUpdate();
    verify(readerJooqContext, times(2)).selectOne();
  }

  @ParameterizedTest
  @ValueSource(booleans = { true, false })
  void testIsModFinanceInstalled(boolean expected) {
    mockFetchOne(expected);

    assertThat(entityTypeInitializationService.isModFinanceInstalled(), is(expected));

    verify(readerJooqContext, times(1)).selectOne();
  }

  @SuppressWarnings("unchecked")
  private void mockFetchOne(boolean shouldHaveResult) {
    SelectSelectStep<Record1<Integer>> selection = mock(SelectSelectStep.class);
    when(readerJooqContext.selectOne()).thenReturn(selection);
    SelectJoinStep<Record1<Integer>> selection2 = mock(SelectJoinStep.class);
    when(selection.from(any(Table.class))).thenReturn(selection2);
    SelectConditionStep<Record1<Integer>> selection3 = mock(SelectConditionStep.class);
    when(selection2.where(any(List.class))).thenReturn(selection3);

    if (shouldHaveResult) {
      when(selection3.fetchOne()).thenReturn(mock(Record1.class));
    } else {
      when(selection3.fetchOne()).thenReturn(null);
    }
  }
}
