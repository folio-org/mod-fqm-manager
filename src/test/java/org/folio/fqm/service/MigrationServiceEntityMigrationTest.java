package org.folio.fqm.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.stream.StreamSupport;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.iterators.PermutationIterator;
import org.folio.fqm.config.MigrationConfiguration;
import org.folio.fqm.migration.MigrationStrategyRepository;
import org.folio.fqm.migration.strategies.MigrationStrategy;
import org.folio.fqm.repository.CustomEntityTypeMigrationMappingRepository;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.spring.FolioExecutionContext;
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

  @Mock
  private CustomEntityTypeMigrationMappingRepository customEntityTypeMigrationMappingRepository;

  @Mock
  private EntityTypeRepository entityTypeRepository;

  @Mock
  private FolioExecutionContext folioExecutionContext;

  @Spy
  private MigrationConfiguration migrationConfiguration;

  @Mock
  private MigrationStrategy testStrategy;

  @Mock
  private MigrationStrategyRepository migrationStrategyRepository;

  @Spy
  private ObjectMapper objectMapper;

  @InjectMocks
  private MigrationService migrationService;

  @BeforeEach
  void setup() {
    lenient().when(folioExecutionContext.getTenantId()).thenReturn("tenant");

    lenient().when(migrationStrategyRepository.getMigrationStrategies()).thenReturn(List.of(testStrategy));
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

  private static List<EntityType> getEntityList(String... ids) {
    return List.of(ids).stream().map(id -> new EntityType().id(id).putAdditionalProperty("isCustom", true)).toList();
  }

  private CustomEntityType migratable(String id) {
    return new CustomEntityType().id(id).version("0").deleted(false);
  }
}
