package org.folio.fqm.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.stream.StreamSupport;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.iterators.PermutationIterator;
import org.folio.fqm.config.MigrationConfiguration;
import org.folio.fqm.migration.MigrationStrategyRepository;
import org.folio.fqm.migration.strategies.AbstractSimpleMigrationStrategy;
import org.folio.fqm.migration.warnings.FieldWarningFactory;
import org.folio.fqm.migration.warnings.RemovedFieldWarning;
import org.folio.fqm.migration.warnings.Warning;
import org.folio.fqm.repository.CustomEntityTypeMigrationMappingRepository;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeDefaultSort;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.i18n.service.TranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@Log4j2
@ExtendWith(MockitoExtension.class)
class MigrationServiceEntityMigrationTest {

  private static final UUID SIMPLE_MIGRATION_ENTITY_ID1 = UUID.fromString("18f63a52-324e-5cd6-91bc-c792bfd92d85");
  private static final UUID SIMPLE_MIGRATION_ENTITY_ID2 = UUID.fromString("23fb1748-529c-5ef8-8ef5-4a1b4d8a89eb");
  private static final UUID SIMPLE_MIGRATION_ENTITY_ID3 = UUID.fromString("38f2aeab-0ddf-5409-9244-b91eddb29da2");
  private static final CustomEntityType CUSTOM_MIGRATION_ENTITY_TYPE = new CustomEntityType()
    .id("50a45b97-bb75-5b0c-bab6-3a49d430d5b1")
    .sources(List.of(new EntityTypeSourceEntityType().alias("inner").targetId(SIMPLE_MIGRATION_ENTITY_ID1)));
  private static final Map<UUID, Map<String, UUID>> CUSTOM_MIGRATION_MAPPINGS = Map.of(
    UUID.fromString(CUSTOM_MIGRATION_ENTITY_TYPE.getId()),
    Map.of("inner", SIMPLE_MIGRATION_ENTITY_ID1)
  );

  @Mock
  private CustomEntityTypeMigrationMappingRepository customEntityTypeMigrationMappingRepository;

  @Mock
  private EntityTypeRepository entityTypeRepository;

  @Mock
  private FolioExecutionContext folioExecutionContext;

  @Spy
  private MigrationConfiguration migrationConfiguration;

  @Mock
  private MigrationStrategyRepository migrationStrategyRepository;

  @Spy
  private ObjectMapper objectMapper;

  @Mock
  private TranslationService translationService;

  @InjectMocks
  private MigrationService migrationService;

  @BeforeEach
  void setup() {
    lenient().when(folioExecutionContext.getTenantId()).thenReturn("tenant");

    lenient()
      .when(migrationStrategyRepository.getMigrationStrategies())
      .thenReturn(List.of(new TestMigrationStrategy()));
  }

  @Test
  void testMigratesOnlyApplicableEntities() {
    List<EntityType> entityTypes = List.of(
      new EntityType().id("93ef130b-08fd-57a7-848e-5ed6032656b0"),
      // will be updated
      new EntityType().id("88846550-6832-5617-83a1-4a3510088490").putAdditionalProperty("isCustom", true),
      // will be deleted
      new EntityType().id("f34cb853-41ea-5069-9ad2-82459f607931").putAdditionalProperty("isCustom", true),
      // will be migrated
      new EntityType().id("abb155b8-1ed0-59a2-afb4-bb8e7c16e673").putAdditionalProperty("isCustom", true)
    );
    when(entityTypeRepository.getEntityTypeDefinitions(null, "tenant")).thenAnswer(i -> entityTypes.stream());

    when(entityTypeRepository.getCustomEntityType(any(UUID.class)))
      .thenAnswer(i ->
        switch (i.getArgument(0, UUID.class).toString()) {
          case "88846550-6832-5617-83a1-4a3510088490" -> new CustomEntityType()
            .version(migrationConfiguration.getCurrentVersion());
          case "f34cb853-41ea-5069-9ad2-82459f607931" -> new CustomEntityType().version("0").deleted(true);
          case "abb155b8-1ed0-59a2-afb4-bb8e7c16e673" -> migratable("abb155b8-1ed0-59a2-afb4-bb8e7c16e673");
          default -> fail();
        }
      );

    migrationService.migrateCustomEntityTypes();

    // only one update, and it was for the desired ET
    verify(entityTypeRepository, times(1)).updateEntityType(any());
    verify(entityTypeRepository, times(1))
      .updateEntityType(argThat(e -> "abb155b8-1ed0-59a2-afb4-bb8e7c16e673".equals(e.getId())));
  }

  @Test
  void testMigratesFailsWithDuplicateIds() {
    List<EntityType> entityTypes = getEntityList(
      "abb155b8-1ed0-59a2-afb4-bb8e7c16e673",
      "abb155b8-1ed0-59a2-afb4-bb8e7c16e673"
    );
    when(entityTypeRepository.getEntityTypeDefinitions(null, "tenant")).thenAnswer(i -> entityTypes.stream());

    when(entityTypeRepository.getCustomEntityType(any()))
      .thenReturn(migratable("abb155b8-1ed0-59a2-afb4-bb8e7c16e673"));

    assertThrows(IllegalStateException.class, migrationService::migrateCustomEntityTypes);
  }

  static List<Arguments> migrationOrderTestCases() {
    // ensure we try every possible case here (only 24). for relationships between these,
    // see comments in consuming test.
    Iterator<List<String>> permutations = new PermutationIterator<>(
      List.of(
        "aaaaaaaa-fb77-5995-8ead-8b1efd81fd10",
        "bbbbbbbb-7b59-522f-9b85-7208291def17",
        "cccccccc-56d1-573d-9639-3b006ed8f953",
        "dddddddd-5523-5c2b-8372-3c3787ed9c23"
      )
    );

    return StreamSupport
      .stream(Spliterators.spliteratorUnknownSize(permutations, Spliterator.ORDERED), false)
      .map(Arguments::of)
      .toList();
  }

  @MethodSource("migrationOrderTestCases")
  @ParameterizedTest
  void testMigrationOrder(List<String> startingOrder) {
    List<EntityType> entityTypes = getEntityList(startingOrder.toArray(new String[0]));
    when(entityTypeRepository.getEntityTypeDefinitions(null, "tenant")).thenAnswer(i -> entityTypes.stream());

    when(entityTypeRepository.getCustomEntityType(any(UUID.class)))
      .thenAnswer(i ->
        switch (i.getArgument(0, UUID.class).toString()) {
          // no dependencies in any direction, not even a source list
          case "aaaaaaaa-fb77-5995-8ead-8b1efd81fd10" -> migratable("aaaaaaaa-fb77-5995-8ead-8b1efd81fd10")
            .sources(null);
          // parent (consumer) of C
          case "bbbbbbbb-7b59-522f-9b85-7208291def17" -> migratable("bbbbbbbb-7b59-522f-9b85-7208291def17")
            .sources(
              List.of(
                new EntityTypeSourceEntityType()
                  .alias("cccc")
                  .targetId(UUID.fromString("cccccccc-56d1-573d-9639-3b006ed8f953"))
              )
            );
          // parent (consumer) of D, consumed by B
          case "cccccccc-56d1-573d-9639-3b006ed8f953" -> migratable("cccccccc-56d1-573d-9639-3b006ed8f953")
            .sources(
              List.of(
                new EntityTypeSourceEntityType()
                  .alias("dddd")
                  .targetId(UUID.fromString("dddddddd-5523-5c2b-8372-3c3787ed9c23"))
              )
            );
          // consumed by B and C
          case "dddddddd-5523-5c2b-8372-3c3787ed9c23" -> migratable("dddddddd-5523-5c2b-8372-3c3787ed9c23");
          default -> fail();
        }
      );

    when(migrationStrategyRepository.getMigrationStrategies()).thenReturn(List.of());

    doAnswer(i -> {
        log.info("Updating entity type with ID {}", i.getArgument(0, EntityType.class).getId());
        return null;
      })
      .when(entityTypeRepository)
      .updateEntityType(any());

    migrationService.migrateCustomEntityTypes();

    InOrder orderVerifier = inOrder(entityTypeRepository);
    orderVerifier
      .verify(entityTypeRepository)
      .updateEntityType(argThat(e -> e.getId().equals("bbbbbbbb-7b59-522f-9b85-7208291def17")));
    orderVerifier
      .verify(entityTypeRepository)
      .updateEntityType(argThat(e -> e.getId().equals("cccccccc-56d1-573d-9639-3b006ed8f953")));
    orderVerifier
      .verify(entityTypeRepository)
      .updateEntityType(argThat(e -> e.getId().equals("dddddddd-5523-5c2b-8372-3c3787ed9c23")));
    // can be anywhere
    verify(entityTypeRepository)
      .updateEntityType(argThat(e -> e.getId().equals("aaaaaaaa-fb77-5995-8ead-8b1efd81fd10")));
  }

  @Test
  void testMigrateDefaultSortNull() {
    EntityType entityType = CUSTOM_MIGRATION_ENTITY_TYPE.toBuilder().build().defaultSort(null);
    List<Warning> warnings = new ArrayList<>();

    migrationService.migrateEntityDefaultSort(entityType, new TestMigrationStrategy(), Map.of(), warnings);

    assertThat(entityType.getDefaultSort(), is(nullValue()));
    assertThat(warnings, is(empty()));
  }

  @Test
  void testMigrateDefaultSortChanged() {
    EntityType entityType = CUSTOM_MIGRATION_ENTITY_TYPE
      .toBuilder()
      .build()
      .defaultSort(List.of(new EntityTypeDefaultSort("inner.old", EntityTypeDefaultSort.DirectionEnum.ASC)));
    List<Warning> warnings = new ArrayList<>();

    migrationService.migrateEntityDefaultSort(
      entityType,
      new TestMigrationStrategy(),
      CUSTOM_MIGRATION_MAPPINGS,
      warnings
    );

    assertThat(
      entityType.getDefaultSort(),
      is(List.of(new EntityTypeDefaultSort("inner.new", EntityTypeDefaultSort.DirectionEnum.ASC)))
    );
    assertThat(warnings, is(empty()));
  }

  @Test
  void testMigrateDefaultSortRemoved() {
    EntityType entityType = CUSTOM_MIGRATION_ENTITY_TYPE
      .toBuilder()
      .build()
      .defaultSort(
        List.of(
          new EntityTypeDefaultSort("inner.removed", EntityTypeDefaultSort.DirectionEnum.ASC),
          new EntityTypeDefaultSort("inner.unchanged", EntityTypeDefaultSort.DirectionEnum.DESC)
        )
      );
    List<Warning> warnings = new ArrayList<>();

    migrationService.migrateEntityDefaultSort(
      entityType,
      new TestMigrationStrategy(),
      CUSTOM_MIGRATION_MAPPINGS,
      warnings
    );

    assertThat(
      entityType.getDefaultSort(),
      is(List.of(new EntityTypeDefaultSort("inner.unchanged", EntityTypeDefaultSort.DirectionEnum.DESC)))
    );
    assertThat(warnings, hasItem(RemovedFieldWarning.builder().field("inner.removed").build()));
  }

  @Test
  void testMigrateGroupByFieldsNull() {
    EntityType entityType = CUSTOM_MIGRATION_ENTITY_TYPE.toBuilder().build().groupByFields(null);
    List<Warning> warnings = new ArrayList<>();

    migrationService.migrateEntityGroupByFields(entityType, new TestMigrationStrategy(), Map.of(), warnings);

    assertThat(entityType.getGroupByFields(), is(nullValue()));
    assertThat(warnings, is(empty()));
  }

  @Test
  void testMigrateGroupByFieldsChanged() {
    EntityType entityType = CUSTOM_MIGRATION_ENTITY_TYPE
      .toBuilder()
      .build()
      .groupByFields(List.of("inner.old", "inner.unchanged", "inner.removed"));
    List<Warning> warnings = new ArrayList<>();

    migrationService.migrateEntityGroupByFields(
      entityType,
      new TestMigrationStrategy(),
      CUSTOM_MIGRATION_MAPPINGS,
      warnings
    );

    assertThat(entityType.getGroupByFields(), containsInAnyOrder("inner.new", "inner.unchanged"));
    assertThat(
      warnings.stream().distinct().toList(),
      contains(RemovedFieldWarning.builder().field("inner.removed").build())
    );
  }

  @Test
  void testMigrateSourcesNull() {
    EntityType entityType = CUSTOM_MIGRATION_ENTITY_TYPE.toBuilder().build().sources(null);
    List<Warning> warnings = new ArrayList<>();

    migrationService.migrateEntitySources(entityType, new TestMigrationStrategy(), Map.of(), warnings);
    assertThat(entityType.getSources(), is(nullValue()));
    assertThat(warnings, is(empty()));
  }

  @Test
  void testMigrateSourceFieldsAndEntityChanged() {
    EntityType entityType = CUSTOM_MIGRATION_ENTITY_TYPE
      .toBuilder()
      .build()
      .sources(
        List.of(
          new EntityTypeSourceEntityType().alias("source1").targetId(SIMPLE_MIGRATION_ENTITY_ID1),
          new EntityTypeSourceEntityType()
            .alias("source2")
            .targetId(SIMPLE_MIGRATION_ENTITY_ID2)
            .targetField("antiquated")
            .sourceField("source1.old")
        )
      );
    List<Warning> warnings = new ArrayList<>();

    migrationService.migrateEntitySources(
      entityType,
      new TestMigrationStrategy(),
      Map.of(UUID.fromString(entityType.getId()), EntityTypeUtils.getEntityTypeSourceAliasMap(entityType)),
      warnings
    );

    assertThat(
      entityType.getSources(),
      containsInAnyOrder(
        new EntityTypeSourceEntityType().alias("source1").targetId(SIMPLE_MIGRATION_ENTITY_ID1),
        new EntityTypeSourceEntityType()
          .alias("source2")
          .targetId(SIMPLE_MIGRATION_ENTITY_ID3)
          .targetField("modern")
          .sourceField("source1.new")
      )
    );
    assertThat(warnings, is(empty()));
  }

  @Test
  void testMigrateSourceFieldsRemoved() {
    EntityType entityType = CUSTOM_MIGRATION_ENTITY_TYPE
      .toBuilder()
      .build()
      .sources(
        List.of(
          new EntityTypeSourceEntityType().alias("source1").targetId(SIMPLE_MIGRATION_ENTITY_ID1),
          new EntityTypeSourceEntityType()
            .alias("source2")
            .targetId(SIMPLE_MIGRATION_ENTITY_ID2)
            .targetField("banished")
            .sourceField("source1.removed")
        )
      );
    List<Warning> warnings = new ArrayList<>();

    migrationService.migrateEntitySources(
      entityType,
      new TestMigrationStrategy(),
      Map.of(UUID.fromString(entityType.getId()), EntityTypeUtils.getEntityTypeSourceAliasMap(entityType)),
      warnings
    );

    // we want to leave these fields in place even though they are removed,
    // as we don't want to remove/break the source entirely (there are fallbacks for that already)
    assertThat(
      entityType.getSources(),
      containsInAnyOrder(
        new EntityTypeSourceEntityType().alias("source1").targetId(SIMPLE_MIGRATION_ENTITY_ID1),
        new EntityTypeSourceEntityType()
          .alias("source2")
          .targetId(SIMPLE_MIGRATION_ENTITY_ID3)
          .targetField("banished")
          .sourceField("source1.removed")
      )
    );
    assertThat(
      warnings.stream().distinct().toList(),
      containsInAnyOrder(
        RemovedFieldWarning.builder().field("source1.removed").build(),
        RemovedFieldWarning.builder().field("source2.banished").build()
      )
    );
  }

  @Test
  void testMigrateWarnings() {
    CustomEntityType entityType = CUSTOM_MIGRATION_ENTITY_TYPE
      .toBuilder()
      .build()
      .groupByFields(List.of("inner.removed"));

    when(translationService.format(anyString(), any(Object[].class))).thenAnswer(i -> i.getArgument(0, String.class));

    migrationService.migrateCustomEntityType(entityType, CUSTOM_MIGRATION_MAPPINGS);

    assertThat(entityType.getDescription(), matchesPattern(".+warning-header(.|\n).+REMOVED_FIELD.+"));
  }

  @Test
  void testMigrateNoWarnings() {
    CustomEntityType entityType = CUSTOM_MIGRATION_ENTITY_TYPE.toBuilder().build();

    migrationService.migrateCustomEntityType(entityType, CUSTOM_MIGRATION_MAPPINGS);

    assertThat(entityType.getDescription(), is(nullValue()));
    verifyNoInteractions(translationService);
  }

  private static class TestMigrationStrategy extends AbstractSimpleMigrationStrategy {

    @Override
    public String getLabel() {
      return "";
    }

    @Override
    public String getMaximumApplicableVersion() {
      return "9999999";
    }

    @Override
    public Map<UUID, Map<String, String>> getFieldChanges() {
      return Map.of(
        SIMPLE_MIGRATION_ENTITY_ID1,
        Map.of("old", "new"),
        SIMPLE_MIGRATION_ENTITY_ID2,
        Map.of("antiquated", "modern")
      );
    }

    @Override
    public Map<UUID, Map<String, FieldWarningFactory>> getFieldWarnings() {
      return Map.of(
        SIMPLE_MIGRATION_ENTITY_ID1,
        Map.of("removed", RemovedFieldWarning.withoutAlternative()),
        SIMPLE_MIGRATION_ENTITY_ID2,
        Map.of("banished", RemovedFieldWarning.withoutAlternative())
      );
    }

    @Override
    public Map<UUID, UUID> getEntityTypeChanges() {
      return Map.of(SIMPLE_MIGRATION_ENTITY_ID2, SIMPLE_MIGRATION_ENTITY_ID3);
    }
  }

  private static List<EntityType> getEntityList(String... ids) {
    return List.of(ids).stream().map(id -> new EntityType().id(id).putAdditionalProperty("isCustom", true)).toList();
  }

  private CustomEntityType migratable(String id) {
    return new CustomEntityType().id(id).version("0").deleted(false);
  }
}
