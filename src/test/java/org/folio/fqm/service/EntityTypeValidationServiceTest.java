package org.folio.fqm.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesRegex;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.folio.fqm.config.MigrationConfiguration;
import org.folio.fqm.exception.FqmException;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.CustomFieldMetadata;
import org.folio.querytool.domain.dto.CustomFieldType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabaseJoin;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.querytool.domain.dto.JoinDirection;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.BeforeEach;
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
class EntityTypeValidationServiceTest {

  private static final String TENANT_ID = "tenant_01";
  private static final UUID EXISTING_TARGET_ET_ID = UUID.fromString("937aa998-abd9-53ed-b94f-58f1b121d47f");

  private static final EntityType BASE_VALID_ENTITY_TYPE = new EntityType()
    .id("9e98c39d-0935-56ca-86d9-98bfab27188a")
    .name("test")
    ._private(false)
    .sources(
      List.of(
        new EntityTypeSourceEntityType().alias("source1").type("entity-type").targetId(EXISTING_TARGET_ET_ID),
        new EntityTypeSourceEntityType()
          .alias("source2")
          .type("entity-type")
          .targetId(EXISTING_TARGET_ET_ID)
          .sourceField("source")
          .targetField("target"),
        new EntityTypeSourceEntityType()
          .alias("source3")
          .type("entity-type")
          .targetId(EXISTING_TARGET_ET_ID)
          .sourceField("source2")
          .targetField("target2")
          .overrideJoinDirection(JoinDirection.LEFT)
      )
    )
    .columns(null);

  private static final CustomEntityType BASE_VALID_CUSTOM_ENTITY_TYPE = new CustomEntityType()
    .id("c1396aa4-c125-5235-b14a-84cf7b0d0c51")
    .owner(UUID.fromString("81dc8e91-c93b-5e86-b8de-dd5b7b4c75d2"))
    .name("test")
    ._private(false)
    .version("current")
    .isCustom(true)
    .sources(
      List.of(new EntityTypeSourceEntityType().alias("source1").type("entity-type").targetId(EXISTING_TARGET_ET_ID))
    )
    .columns(null);

  @Mock
  private EntityTypeRepository repo;

  @Mock
  private FolioExecutionContext executionContext;

  @Mock
  private MigrationConfiguration migrationConfiguration;

  @Spy
  @InjectMocks
  private EntityTypeValidationService entityTypeValidationService;

  @BeforeEach
  void baseMocks() {
    lenient().when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    lenient()
      .when(repo.getEntityTypeDefinition(EXISTING_TARGET_ET_ID, TENANT_ID))
      .thenReturn(Optional.of(new EntityType()));
    lenient().when(migrationConfiguration.getCurrentVersion()).thenReturn("current");
  }

  // shorthand to build a variant of the base CustomEntityType
  private static CustomEntityType customETFactory(UnaryOperator<CustomEntityType.CustomEntityTypeBuilder<?, ?>> op) {
    return op.apply(BASE_VALID_CUSTOM_ENTITY_TYPE.toBuilder()).build();
  }

  // shorthand to build a variant of the base EntityType
  private static EntityType entityTypeFactory(UnaryOperator<EntityType.EntityTypeBuilder<?, ?>> op) {
    return op.apply(BASE_VALID_ENTITY_TYPE.toBuilder()).build();
  }

  private static EntityType entityTypeWithSourcesFactory(EntityTypeSource... sources) {
    return entityTypeFactory(b -> b.sources(List.of(sources)));
  }

  private static EntityType entityTypeWithCustomFieldColumnFactory(CustomFieldMetadata metadata) {
    return entityTypeFactory(b ->
      b.columns(
        List.of(new EntityTypeColumn().name("col").dataType(new CustomFieldType().customFieldMetadata(metadata)))
      )
    );
  }

  @Test
  void testValidCustomEntityPasses() {
    UUID id = UUID.fromString(BASE_VALID_CUSTOM_ENTITY_TYPE.getId());
    assertDoesNotThrow(() -> entityTypeValidationService.validateCustomEntityType(id, BASE_VALID_CUSTOM_ENTITY_TYPE));
  }

  @Test
  void testValidCustomEntityWithEmptyColumnListPasses() {
    UUID id = UUID.fromString(BASE_VALID_CUSTOM_ENTITY_TYPE.getId());
    CustomEntityType entityWithEmptyColumnList = customETFactory(b -> b.columns(List.of()));
    assertDoesNotThrow(() -> entityTypeValidationService.validateCustomEntityType(id, entityWithEmptyColumnList));
  }

  @Test
  void testValidEntityPassesWithSourceFromRepositoryMock() {
    UUID id = UUID.fromString(BASE_VALID_ENTITY_TYPE.getId());
    assertDoesNotThrow(() -> entityTypeValidationService.validateEntityType(id, BASE_VALID_ENTITY_TYPE, null));
    verify(repo, atLeastOnce()).getEntityTypeDefinition(EXISTING_TARGET_ET_ID, TENANT_ID);
  }

  @Test
  void testValidEntityPassesWithSourceFromInput() {
    UUID entityTypeId = UUID.fromString(BASE_VALID_ENTITY_TYPE.getId());
    UUID targetId = UUID.fromString("145cfbfa-2948-5a33-accc-c1367c04e5dc");
    EntityType entity = entityTypeWithSourcesFactory(
      new EntityTypeSourceEntityType().alias("source1").type("entity-type").targetId(targetId)
    );

    assertDoesNotThrow(() -> entityTypeValidationService.validateEntityType(entityTypeId, entity, List.of(targetId)));
    verifyNoInteractions(repo);
  }

  // case without the parameter list is handled by parameterized entityTypeInvalidCases
  @Test
  void testEntityWithSourcePointingToUnknownTargetFromParameterFails() {
    UUID entityTypeId = UUID.fromString(BASE_VALID_ENTITY_TYPE.getId());
    UUID targetId = UUID.fromString("145cfbfa-2948-5a33-accc-c1367c04e5dc");
    EntityType entity = entityTypeWithSourcesFactory(
      new EntityTypeSourceEntityType().alias("source1").type("entity-type").targetId(targetId)
    );
    List<UUID> availableTargetIds = List.of();

    InvalidEntityTypeDefinitionException ex = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entity, availableTargetIds)
    );
    assertThat(
      ex.getMessage(),
      containsString("Source source1's target ID " + targetId + " refers to an unknown entity type")
    );

    verifyNoInteractions(repo);
  }

  @Test
  void testEntityWithValidCustomFieldMetadata() {
    UUID entityTypeId = UUID.fromString(BASE_VALID_ENTITY_TYPE.getId());
    EntityType entity = entityTypeWithCustomFieldColumnFactory(
      new CustomFieldMetadata().configurationView("view").dataExtractionPath("path")
    );

    assertDoesNotThrow(() -> entityTypeValidationService.validateEntityType(entityTypeId, entity, null));
  }

  static List<Arguments> customEntityTypeInvalidCases() {
    // input, expected exception message (supports regex)
    return List.of(
      Arguments.of(customETFactory(b -> b.version(null)), "Custom entity type must have _version=current"),
      Arguments.of(customETFactory(b -> b.version("old")), "Custom entity type must have _version=current"),
      Arguments.of(
        customETFactory(b -> b.sourceView("something")),
        "Custom entity types must not contain a sourceView property"
      ),
      Arguments.of(
        customETFactory(b -> b.sourceViewExtractor("something")),
        "Custom entity types must not contain a sourceViewExtractor property"
      ),
      Arguments.of(
        customETFactory(b -> b.customFieldEntityTypeId(BASE_VALID_CUSTOM_ENTITY_TYPE.getId())),
        "Custom field entity type ID must not be defined for custom entity types"
      ),
      Arguments.of(customETFactory(b -> b.owner(null)), "Custom entity type must have an owner"),
      Arguments.of(customETFactory(b -> b.isCustom(null)), "Entity type .+ is not a custom entity type"),
      Arguments.of(customETFactory(b -> b.isCustom(false)), "Entity type .+ is not a custom entity type"),
      Arguments.of(customETFactory(b -> b.shared(null)), "Custom entity type must have a shared property"),
      Arguments.of(
        customETFactory(b ->
          b.sources(
            List.of(
              BASE_VALID_CUSTOM_ENTITY_TYPE.getSources().get(0),
              new EntityTypeSourceDatabase().type("db").alias("source1").join(new EntityTypeSourceDatabaseJoin())
            )
          )
        ),
        "Custom entity types must contain only entity-type sources"
      ),
      Arguments.of(
        customETFactory(b -> b.columns(List.of(new EntityTypeColumn().name("col1")))),
        "Custom entity types must not contain columns"
      ),
      Arguments.of(
        customETFactory(b -> b.crossTenantQueriesEnabled(true)),
        "Custom entity must not have cross-tenant queries enabled"
      )
    );
  }

  @ParameterizedTest
  @MethodSource("customEntityTypeInvalidCases")
  void testCustomEntityTypeInvalidCases(CustomEntityType entity, String expectedExceptionPattern) {
    UUID entityId = UUID.fromString(entity.getId());

    FqmException exception = assertThrows(
      FqmException.class,
      () -> entityTypeValidationService.validateCustomEntityType(entityId, entity)
    );

    assertThat(exception.getMessage(), matchesRegex(expectedExceptionPattern));
  }

  static List<Arguments> entityTypeInvalidCases() {
    // input, expected exception message (supports regex)
    return List.of(
      Arguments.of(entityTypeFactory(b -> b.name(null)), "Entity type name cannot be null or blank"),
      Arguments.of(entityTypeFactory(b -> b.name("")), "Entity type name cannot be null or blank"),
      Arguments.of(entityTypeFactory(b -> b._private(null)), "Entity type must have private property set"),
      Arguments.of(entityTypeFactory(b -> b.sources(null)), "Entity types must have at least one source defined"),
      Arguments.of(entityTypeWithSourcesFactory(), "Entity types must have at least one source defined"),
      Arguments.of(
        entityTypeWithSourcesFactory(new EntityTypeSourceEntityType().alias(null)),
        "Source alias cannot be null or blank"
      ),
      Arguments.of(
        entityTypeWithSourcesFactory(new EntityTypeSourceEntityType().alias("")),
        "Source alias cannot be null or blank"
      ),
      Arguments.of(
        entityTypeWithSourcesFactory(new EntityTypeSourceEntityType().alias("foo.bar")),
        "Invalid source alias: .+ must not contain '\\.'"
      ),
      Arguments.of(
        entityTypeWithSourcesFactory(
          new EntityTypeSourceEntityType().alias("a").targetId(EXISTING_TARGET_ET_ID).type(null)
        ),
        "Source a's type cannot be null"
      ),
      Arguments.of(
        entityTypeWithSourcesFactory(new EntityTypeSourceDatabase().alias("a").type("bad")),
        "Source a's type must be 'db'"
      ),
      Arguments.of(
        entityTypeWithSourcesFactory(
          new EntityTypeSourceEntityType().alias("a").targetId(EXISTING_TARGET_ET_ID).type("bad")
        ),
        "Source a's type must be 'entity-type'"
      ),
      Arguments.of(
        entityTypeWithSourcesFactory(new EntityTypeSource() {}.alias("a").type("foo")),
        "Source a is not a known type"
      ),
      Arguments.of(
        entityTypeWithSourcesFactory(new EntityTypeSourceEntityType().alias("a").type("entity-type").targetId(null)),
        "Source a's target ID cannot be null"
      ),
      Arguments.of(
        entityTypeWithSourcesFactory(
          new EntityTypeSourceEntityType()
            .alias("a")
            .type("entity-type")
            .targetId(EXISTING_TARGET_ET_ID)
            .sourceField("foo")
        ),
        "Source a must contain both targetField and sourceField or neither"
      ),
      Arguments.of(
        entityTypeWithSourcesFactory(
          new EntityTypeSourceEntityType()
            .alias("a")
            .type("entity-type")
            .targetId(EXISTING_TARGET_ET_ID)
            .overrideJoinDirection(JoinDirection.LEFT)
        ),
        "Source a may only contain overrideJoinDirection if targetField and sourceField are also defined"
      ),
      // this checks against EntityTypeRepository via mock; see testEntityTypeWithSourcePointingToUnknownTargetFromParameterFails
      // for the version which checks against the parameter list
      Arguments.of(
        entityTypeWithSourcesFactory(
          new EntityTypeSourceEntityType()
            .alias("a")
            .type("entity-type")
            .targetId(UUID.fromString("95632cd9-2559-57ad-942c-90b145b6c8ea"))
        ),
        "Source a's target ID 95632cd9-2559-57ad-942c-90b145b6c8ea refers to an unknown entity type"
      ),
      Arguments.of(entityTypeWithSourcesFactory(), "Entity types must have at least one source defined"),
      Arguments.of(
        entityTypeWithSourcesFactory(
          // no base sources present to join against
          new EntityTypeSourceDatabase()
            .alias("a1")
            .type("db")
            .target("db")
            .join(EntityTypeSourceDatabaseJoin.builder().joinTo("other").build()),
          new EntityTypeSourceEntityType()
            .alias("a2")
            .type("entity-type")
            .sourceField("source3")
            .targetId(EXISTING_TARGET_ET_ID)
            .targetField("field2")
        ),
        "Entity types must have one base source without a join defined"
      ),
      Arguments.of(
        entityTypeWithSourcesFactory(
          new EntityTypeSourceDatabase().alias("a1").type("db").target("db"),
          new EntityTypeSourceEntityType().alias("a2").type("entity-type").targetId(EXISTING_TARGET_ET_ID)
        ),
        "Entity types can have only one base source; all others must define a join"
      ),
      Arguments.of(
        entityTypeWithCustomFieldColumnFactory(
          new CustomFieldMetadata().configurationView(null).dataExtractionPath("path")
        ),
        "Custom field metadata must have a configuration view defined"
      ),
      Arguments.of(
        entityTypeWithCustomFieldColumnFactory(
          new CustomFieldMetadata().configurationView("").dataExtractionPath("path")
        ),
        "Custom field metadata must have a configuration view defined"
      ),
      Arguments.of(
        entityTypeWithCustomFieldColumnFactory(
          new CustomFieldMetadata().configurationView("view").dataExtractionPath(null)
        ),
        "Custom field metadata must have a data extraction path defined"
      ),
      Arguments.of(
        entityTypeWithCustomFieldColumnFactory(
          new CustomFieldMetadata().configurationView("view").dataExtractionPath("")
        ),
        "Custom field metadata must have a data extraction path defined"
      )
    );
  }

  @ParameterizedTest
  @MethodSource("entityTypeInvalidCases")
  void testEntityTypeInvalidCases(EntityType entity, String expectedExceptionPattern) {
    UUID entityId = UUID.fromString(entity.getId());

    FqmException exception = assertThrows(
      FqmException.class,
      () -> entityTypeValidationService.validateEntityType(entityId, entity, null)
    );

    assertThat(exception.getMessage(), matchesRegex(expectedExceptionPattern));
  }

  static List<Arguments> entityTypeIdCases() {
    UUID validId = UUID.fromString(BASE_VALID_ENTITY_TYPE.getId());

    // entity type ID parameter, entity type, expected exception message (supports regex)
    return List.of(
      Arguments.of(validId, entityTypeFactory(b -> b.id(null)), "Entity type ID cannot be null"),
      Arguments.of(validId, entityTypeFactory(b -> b.id("")), "Invalid string provided for entity type ID"),
      Arguments.of(validId, entityTypeFactory(b -> b.id("invalid")), "Invalid string provided for entity type ID"),
      Arguments.of(null, BASE_VALID_ENTITY_TYPE, "Entity type ID cannot be null"),
      Arguments.of(
        UUID.fromString("a6dd246c-f8c7-579d-9a07-bee592631a80"),
        entityTypeFactory(b -> b.id("b2ad53c2-d4a3-53e0-b04f-f3446cab4b11")),
        "Entity type ID in the request body does not match.+"
      )
    );
  }

  @ParameterizedTest
  @MethodSource("entityTypeIdCases")
  void testEntityTypeIdCases(UUID idParameter, EntityType entity, String expectedExceptionPattern) {
    FqmException exception = assertThrows(
      FqmException.class,
      () -> entityTypeValidationService.validateEntityType(idParameter, entity, null)
    );

    assertThat(exception.getMessage(), matchesRegex(expectedExceptionPattern));
  }

  static List<Arguments> customEntityTypeSourceOrderCases() {
    List<Integer> listOfSingleNull = new ArrayList<>(); // Arrays.asList complains about varargs with single null
    listOfSingleNull.add(null);

    // input source order, expected source order after validation
    return List.of(
      Arguments.of(listOfSingleNull, Arrays.asList(100)),
      Arguments.of(Arrays.asList(1), Arrays.asList(1)),
      Arguments.of(Arrays.asList(null, 1), Arrays.asList(100, 1)),
      Arguments.of(Arrays.asList(1, null), Arrays.asList(1, 200)),
      Arguments.of(Arrays.asList(null, null, null), Arrays.asList(100, 200, 300)),
      Arguments.of(Arrays.asList(null, 10000, null), Arrays.asList(100, 10000, 300))
    );
  }

  @ParameterizedTest
  @MethodSource("customEntityTypeSourceOrderCases")
  void testCustomEntityTypeSourceOrderCases(List<Integer> inputOrder, List<Integer> expectedOrder) {
    CustomEntityType entity = customETFactory(b -> {
      List<EntityTypeSource> sources = new ArrayList<>();
      for (int i = 0; i < inputOrder.size(); i++) {
        Integer order = inputOrder.get(i);
        EntityTypeSourceEntityType source = new EntityTypeSourceEntityType()
          .alias("source" + i)
          .type("entity-type")
          .targetId(EXISTING_TARGET_ET_ID);
        if (order != null) {
          source.setOrder(order);
        }
        if (i > 0) {
          source.setSourceField("source" + (i - 1));
          source.setTargetField("target" + (i - 1));
        }
        sources.add(source);
      }
      return b.sources(sources);
    });

    UUID entityId = UUID.fromString(entity.getId());
    assertDoesNotThrow(() -> entityTypeValidationService.validateCustomEntityType(entityId, entity));

    assertThat(entity.getSources().stream().map(EntityTypeSource::getOrder).toList(), is(expectedOrder));
  }
}
