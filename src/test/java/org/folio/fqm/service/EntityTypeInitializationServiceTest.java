package org.folio.fqm.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.jooq.DSLContext;
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
}
