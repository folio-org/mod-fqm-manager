package org.folio.fqm.service;

import org.folio.fqm.repository.EntityTypeCacheRepository;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.AvailableJoinsResponse;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.querytool.domain.dto.Join;
import org.folio.querytool.domain.dto.JoinDirection;
import org.folio.querytool.domain.dto.JoinFieldPair;
import org.folio.querytool.domain.dto.LabeledValue;
import org.folio.querytool.domain.dto.LabeledValueWithDescription;
import org.folio.querytool.domain.dto.StringType;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntityTypeServiceAvailableJoinsTest {

  @Mock
  private EntityTypeRepository repo;

  @Mock
  private EntityTypeCacheRepository cacheRepository;

  @Mock
  private LocalizationService localizationService;

  @Mock
  private UserTenantService userTenantService;

  @Mock
  private EntityTypeFlatteningService entityTypeFlatteningService;

  @Mock
  private FolioExecutionContext executionContext;

  @Mock
  private PermissionsService permissionsService;

  @Spy
  @InjectMocks
  private EntityTypeService entityTypeService;

  // Set of ET definitions for testing
  // These ETs have relationships between them through joins, which look like this:
  // et1.col1 -> et2.colA -> et3.colX -> et1.col1; et3.colY -> et1.col1
  // Also, 1 composite entity type that combines et1 and et2
  private static final Map<UUID, EntityType> entityTypes = Stream.of(
    new EntityType()
      .id("00000000-0000-0000-0000-000000000001")
      .name("et1")
      .labelAlias("Test Entity 1")
      .columns(List.of(
        new EntityTypeColumn()
          .name("col1")
          .labelAlias("Column 1")
          .dataType(new StringType("stringType"))
          .valueGetter("")
          .joinsTo(List.of(new Join().targetId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
            .targetField("colA")
            .type("equality-cast-uuid")
            .direction(JoinDirection.LEFT))),
        new EntityTypeColumn()
          .name("col2")
          .labelAlias("Column 2")
          .dataType(new StringType("stringType"))
          .valueGetter("")
      )),
    new EntityType()
      .id("00000000-0000-0000-0000-000000000002")
      .name("et2")
      .labelAlias("Test Entity 2")
      .columns(List.of(
        new EntityTypeColumn()
          .name("colA")
          .labelAlias("Column A")
          .dataType(new StringType("stringType"))
          .valueGetter("")
          .joinsTo(List.of(new Join().targetId(UUID.fromString("00000000-0000-0000-0000-000000000003"))
            .targetField("colX")
            .type("equality-cast-uuid")
            .direction(JoinDirection.INNER))),
        new EntityTypeColumn()
          .name("colB")
          .labelAlias("Column B")
          .dataType(new StringType("stringType"))
          .valueGetter("")
      )),
    new EntityType()
      .id("00000000-0000-0000-0000-000000000003")
      .name("et3")
      .labelAlias("Test Entity 3")
      .columns(List.of(
        new EntityTypeColumn()
          .name("colX")
          .valueGetter("")
          .dataType(new StringType("stringType"))
          .labelAlias("Column X"),
        new EntityTypeColumn()
          .name("colY")
          .labelAlias("Column Y")
          .dataType(new StringType("stringType"))
          .valueGetter("")
          .joinsTo(List.of(new Join().targetId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            .targetField("col1")
            .type("equality-cast-uuid")
            .direction(JoinDirection.LEFT)))
      )),
    new EntityType()
      .id("00000000-0000-0000-0000-000000000004")
      .name("et4")
      .labelAlias("Composite Entity 1-2")
      .sources(List.of(
        new EntityTypeSourceEntityType()
          .type("entity-type")
          .alias("et1")
          .order(100)
          .targetId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
        new EntityTypeSourceEntityType()
          .type("entity-type")
          .alias("et2")
          .order(100)
          .sourceField("et1.colA")
          .targetId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
          .targetField("col1")
      ))
  ).collect(Collectors.toMap((EntityType entityType) -> UUID.fromString(entityType.getId()), Function.identity()));

  @BeforeEach
  void setup() {
    reset();
    lenient().when(localizationService.localizeEntityType(any(EntityType.class), anyList())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    lenient().when(localizationService.localizeEntityTypeColumn(any(), anyList(), any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(2));
    lenient().when(userTenantService.getUserTenantsResponse(any())).thenReturn("{\"totalRecords\": 0}");
    lenient().when(repo.getEntityTypeDefinition(any(), any())).thenAnswer(invocationOnMock -> Optional.of(entityTypes.get(invocationOnMock.getArgument(0, UUID.class))));

    EntityTypeFlatteningService flatteningService = new EntityTypeFlatteningService(repo, cacheRepository, localizationService, executionContext, userTenantService);
    lenient().doAnswer(invocationOnMock -> entityTypes.values().stream()
      .map(et -> flatteningService.getFlattenedEntityType(et, "", true))
      .collect(Collectors.toMap(et -> UUID.fromString(et.getId()), et -> et, (a, b) -> a))
    ).when(entityTypeService).getAccessibleEntityTypesById();
  }

  @Test
  void getAvailableJoins_shouldReturnTargetEntityTypesWhenCustomEntityTypeIsNull() {
    // When there's a request for available joins without a custom entity type
    AvailableJoinsResponse result = entityTypeService.getAvailableJoins(null, null);

    // Then it should return all available target entity types
    AvailableJoinsResponse expected = new AvailableJoinsResponse()
      .availableTargetIds(entityTypeService.getAccessibleEntityTypesById().values().stream()
        .map(et -> new LabeledValueWithDescription(et.getLabelAlias()).value(et.getId()))
        .sorted(comparing(LabeledValueWithDescription::getLabel, String.CASE_INSENSITIVE_ORDER))
        .toList())
      .availableJoinConditions(null);
    assertEquals(expected, result);
  }

  @Test
  void getAvailableJoins_shouldReturnFieldPairsWhenSourcesAndTargetAreProvided() {
    // Given a custom entity type with a join field and an arbitrary other ET
    UUID customEtId = UUID.randomUUID();
    UUID targetEtId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    List<EntityTypeSourceEntityType> sources = List.of(
      new EntityTypeSourceEntityType()
        .type("entity-type")
        .alias("superCoolAlias")
        .targetId(targetEtId)
    );
    EntityType customEt = new EntityType().id(customEtId.toString())
      .columns(List.of(
        new EntityTypeColumn()
          .name("superCoolAlias.joinField")
          .labelAlias("Join Field")
          .joinsTo(List.of(new Join().targetId(targetEtId).targetField("colA")))
      ));
    EntityType targetEt = entityTypeService.getAccessibleEntityTypesById().get(targetEtId);

    doReturn(Map.of(targetEtId, targetEt)).when(entityTypeService).getAccessibleEntityTypesById();
    doReturn(customEt).when(entityTypeFlatteningService).getFlattenedEntityType(any(CustomEntityType.class), any(), eq(true));

    // When there's a request for available joins with the custom entity type and no specific fields
    AvailableJoinsResponse result = entityTypeService.getAvailableJoins(sources, targetEtId);

    // Then it should return the fields from both entity types that can be used in a join between the two
    assertNotNull(result.getAvailableJoinConditions());
    assertEquals(1, result.getAvailableJoinConditions().size());
    assertEquals(new JoinFieldPair(new LabeledValue("Join Field").value("superCoolAlias.joinField"), new LabeledValue("Column A").value("colA")),
      result.getAvailableJoinConditions().getFirst());
  }

  @Test
  void getAvailableJoins_shouldReturnAllAccessibleEntityTypesWhenCustomEntityTypeIsNull() {
    // When there's a request for target entity types without a custom entity type set
    AvailableJoinsResponse result = entityTypeService.getAvailableJoins(null, null);

    // Then it should return all accessible entity types
    assertNotNull(result.getAvailableTargetIds());
    assertEquals(entityTypeService.getAccessibleEntityTypesById().size(), result.getAvailableTargetIds().size());
    assertTrue(result.getAvailableTargetIds().stream().anyMatch(lv -> "Test Entity 1".equals(lv.getLabel())));
    assertTrue(result.getAvailableTargetIds().stream().anyMatch(lv -> "Test Entity 2".equals(lv.getLabel())));
    assertTrue(result.getAvailableTargetIds().stream().anyMatch(lv -> "Test Entity 3".equals(lv.getLabel())));
    assertTrue(result.getAvailableTargetIds().stream().anyMatch(lv -> "Composite Entity 1-2".equals(lv.getLabel())));
  }

  @Test
  void getAvailableJoins_shouldReturnEmptyWhenNoJoinableFields() {
    // Given a custom entity type with no joinable fields and an arbitrary target entity type
    UUID customEtId = UUID.randomUUID();
    UUID targetEtId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    List<EntityTypeSourceEntityType> sources = List.of(
      new EntityTypeSourceEntityType()
        .type("entity-type")
        .alias("superCoolAlias")
        .targetId(targetEtId)
    );
    EntityType customEt = new EntityType().id(customEtId.toString()).columns(List.of());
    EntityType targetEt = new EntityType().id(targetEtId.toString()).columns(List.of());

    Map<UUID, EntityType> accessible = Map.of(targetEtId, targetEt);
    doReturn(accessible).when(entityTypeService).getAccessibleEntityTypesById();
    doReturn(customEt).when(entityTypeFlatteningService).getFlattenedEntityType(any(CustomEntityType.class), any(), eq(true));

    // When there's a request for target entity type fields with the custom entity type and target entity type
    AvailableJoinsResponse result = entityTypeService.getAvailableJoins(sources, targetEtId);

    // Then it should return an empty list since there are no joinable fields
    assertNotNull(result.getAvailableJoinConditions());
    assertTrue(result.getAvailableJoinConditions().isEmpty());
  }

  @Test
  void getAvailableJoins_shouldHandleReverseJoins() {
    // Given a custom entity type and a target entity type with a column that joins to the custom entity type
    UUID customEtId = UUID.randomUUID();
    UUID targetEtId = UUID.randomUUID();

    List<EntityTypeSourceEntityType> sources = List.of(
      new EntityTypeSourceEntityType()
        .type("entity-type")
        .alias("superCoolAlias")
        .targetId(targetEtId)
    );

    // Custom entity type with a column that can be joined to
    EntityTypeColumn customColumn = new EntityTypeColumn()
      .name("customField")
      .labelAlias("Custom Field")
      .originalEntityTypeId(customEtId);

    EntityType customEt = new EntityType()
      .id(customEtId.toString())
      .columns(List.of(customColumn));

    // Target entity type with a column that joins to the custom entity type
    EntityTypeColumn targetColumn = new EntityTypeColumn()
      .name("targetField")
      .labelAlias("Target Field")
      .originalEntityTypeId(targetEtId) // Important: set the originalEntityTypeId
      .joinsTo(List.of(new Join()
        .targetId(customEtId)
        .targetField("customField")));

    EntityType targetEt = new EntityType()
      .id(targetEtId.toString())
      .labelAlias("Target Entity Type")
      .columns(List.of(targetColumn));

    Map<UUID, EntityType> accessible = Map.of(targetEtId, targetEt);
    doReturn(accessible).when(entityTypeService).getAccessibleEntityTypesById();
    doReturn(customEt).when(entityTypeFlatteningService).getFlattenedEntityType(any(CustomEntityType.class), any(), eq(true));

    // When there's a request for available joins with the custom entity type
    AvailableJoinsResponse result = entityTypeService.getAvailableJoins(sources, null);

    // Then it should include the target entity type in the available target entity types
    assertNotNull(result.getAvailableTargetIds());
    assertEquals(1, result.getAvailableTargetIds().size());
    assertEquals(targetEtId.toString(), result.getAvailableTargetIds().getFirst().getValue());
  }

  @Test
  void getAvailableJoins_shouldHandleDirectJoins() {
    // Use static entityTypes from the repo for custom and target entity types
    UUID customEtId = UUID.fromString("00000000-0000-0000-0000-000000000001"); // et1
    UUID targetEtId1 = UUID.fromString("00000000-0000-0000-0000-000000000002"); // et2
    UUID targetEtId2 = UUID.fromString("00000000-0000-0000-0000-000000000003"); // et3

    List<EntityTypeSourceEntityType> sources = List.of(
      new EntityTypeSourceEntityType()
        .type("entity-type")
        .alias("superCoolAlias")
        .targetId(targetEtId1)
    );
    EntityType customEt = entityTypes.get(customEtId);
    EntityType targetEt1 = entityTypes.get(targetEtId1);
    EntityType targetEt2 = entityTypes.get(targetEtId2);

    Map<UUID, EntityType> accessible = Map.of(
      targetEtId1, targetEt1,
      targetEtId2, targetEt2
    );

    doReturn(accessible).when(entityTypeService).getAccessibleEntityTypesById();
    doReturn(customEt).when(entityTypeFlatteningService).getFlattenedEntityType(any(CustomEntityType.class), any(), eq(true));

    // When there's a request for available joins with the custom entity type and no specific field
    AvailableJoinsResponse result = entityTypeService.getAvailableJoins(sources, targetEtId1);

    // Then it should not include any target IDs
    assertNull(result.getAvailableTargetIds());

    // And it should include the custom entity type field that can be used for joins
    assertNotNull(result.getAvailableJoinConditions());
    assertEquals(1, result.getAvailableJoinConditions().size());

    // Verify field names are present
    Set<String> customFieldNames = result.getAvailableJoinConditions().stream()
      .map(JoinFieldPair::getSourceField)
      .map(LabeledValue::getValue)
      .collect(Collectors.toSet());
    assertTrue(customFieldNames.contains("col1"));
  }

  @Test
  void getAvailableJoins_shouldOnlyReturnTargetIdsWhenItIsNotProvided() {
    // Use static entityTypes from the repo for custom and target entity types
    UUID customEtId = UUID.fromString("00000000-0000-0000-0000-000000000001"); // et1
    UUID targetEtId1 = UUID.fromString("00000000-0000-0000-0000-000000000002"); // et2
    UUID targetEtId2 = UUID.fromString("00000000-0000-0000-0000-000000000003"); // et3

    List<EntityTypeSourceEntityType> sources = List.of(
      new EntityTypeSourceEntityType()
        .type("entity-type")
        .alias("superCoolAlias")
        .targetId(targetEtId1)
    );
    EntityType customEt = entityTypes.get(customEtId);
    EntityType targetEt1 = entityTypes.get(targetEtId1);
    EntityType targetEt2 = entityTypes.get(targetEtId2);

    Map<UUID, EntityType> accessible = Map.of(
      targetEtId1, targetEt1,
      targetEtId2, targetEt2
    );

    doReturn(accessible).when(entityTypeService).getAccessibleEntityTypesById();
    doReturn(customEt).when(entityTypeFlatteningService).getFlattenedEntityType(any(CustomEntityType.class), any(), eq(true));

    // When there's a request for available joins with the custom entity type and no specific field
    AvailableJoinsResponse result = entityTypeService.getAvailableJoins(sources, null);

    // Then it should include both target entity types in the available target entity types
    assertNotNull(result.getAvailableTargetIds());

    // And it should not include the custom entity type fields or target fields
    assertNull(result.getAvailableJoinConditions());
  }

  @Test
  void getAvailableJoins_shouldHandleMultipleDirectJoinsToSameTarget() {
    // Given a custom entity type with multiple columns that join to the same target entity type
    UUID customEtId = UUID.randomUUID();
    UUID targetEtId = UUID.randomUUID();

    List<EntityTypeSourceEntityType> sources = List.of(
      new EntityTypeSourceEntityType()
        .type("entity-type")
        .alias("superCoolAlias")
        .targetId(targetEtId)
    );

    // Custom entity type with multiple columns that join to the same target entity type
    EntityTypeColumn customColumn1 = new EntityTypeColumn()
      .name("customField1")
      .labelAlias("Custom Field 1")
      .originalEntityTypeId(customEtId)
      .joinsTo(List.of(new Join()
        .targetId(targetEtId)
        .targetField("targetField1")));

    EntityTypeColumn customColumn2 = new EntityTypeColumn()
      .name("customField2")
      .labelAlias("Custom Field 2")
      .originalEntityTypeId(customEtId)
      .joinsTo(List.of(new Join()
        .targetId(targetEtId)
        .targetField("targetField2")));

    EntityType customEt = new EntityType()
      .id(customEtId.toString())
      .labelAlias("Custom Entity Type")
      .columns(List.of(customColumn1, customColumn2));

    // Target entity type with multiple fields that can be joined to
    EntityTypeColumn targetColumn1 = new EntityTypeColumn()
      .name("targetField1")
      .labelAlias("Target Field 1")
      .originalEntityTypeId(targetEtId);

    EntityTypeColumn targetColumn2 = new EntityTypeColumn()
      .name("targetField2")
      .labelAlias("Target Field 2")
      .originalEntityTypeId(targetEtId);

    EntityType targetEt = new EntityType()
      .id(targetEtId.toString())
      .labelAlias("Target Entity")
      .columns(List.of(targetColumn1, targetColumn2));

    Map<UUID, EntityType> accessible = Map.of(targetEtId, targetEt);

    doReturn(accessible).when(entityTypeService).getAccessibleEntityTypesById();
    doReturn(customEt).when(entityTypeFlatteningService).getFlattenedEntityType(any(CustomEntityType.class), any(), eq(true));

    // When there's a request for available joins with the custom entity type and target entity type
    AvailableJoinsResponse result = entityTypeService.getAvailableJoins(sources, targetEtId);

    // Then it should include both custom entity type fields that can be used for joins
    assertNotNull(result.getAvailableJoinConditions());
    assertEquals(2, result.getAvailableJoinConditions().size());

    // Verify field names are present
    Set<String> customFieldNames = result.getAvailableJoinConditions().stream()
      .map(JoinFieldPair::getSourceField)
      .map(LabeledValue::getValue)
      .collect(Collectors.toSet());
    assertTrue(customFieldNames.contains("customField1"));
    assertTrue(customFieldNames.contains("customField2"));
  }

  @Test
  void getAvailableJoins_shouldHandleSpecificFieldMatches() {
    // Given a custom entity type with a specific field and a target entity type
    UUID customEtId = UUID.fromString("00000000-0000-0000-0000-000000000002"); // et2
    UUID targetEtId = UUID.fromString("00000000-0000-0000-0000-000000000003"); // et3

    List<EntityTypeSourceEntityType> sources = List.of(
      new EntityTypeSourceEntityType()
        .type("entity-type")
        .alias("superCoolAlias")
        .targetId(targetEtId)
    );

    EntityType customEt = entityTypes.get(customEtId);
    EntityType targetEt = entityTypes.get(targetEtId);

    Map<UUID, EntityType> accessible = Map.of(targetEtId, targetEt);
    doReturn(accessible).when(entityTypeService).getAccessibleEntityTypesById();
    doReturn(customEt).when(entityTypeFlatteningService).getFlattenedEntityType(any(CustomEntityType.class), any(), eq(true));

    // When there's a request for available joins with the custom entity type and specific field
    AvailableJoinsResponse result = entityTypeService.getAvailableJoins(
      sources,
      targetEtId
    );

    // Then it should include the target field in the available target entity type fields
    assertNotNull(result.getAvailableJoinConditions());
    assertEquals(1, result.getAvailableJoinConditions().size());
    assertEquals("colA", result.getAvailableJoinConditions().get(0).getSourceField().getValue());
    assertEquals("colX", result.getAvailableJoinConditions().get(0).getTargetField().getValue());
  }

  @Test
  void getAvailableJoins_shouldHandleAllFieldMatches() {
    // Given a custom entity type and a target entity type with multiple joinable fields
    UUID customEtId = UUID.fromString("00000000-0000-0000-0000-000000000003"); // et3
    UUID targetEtId = UUID.fromString("00000000-0000-0000-0000-000000000001"); // et1

    List<EntityTypeSourceEntityType> sources = List.of(
      new EntityTypeSourceEntityType()
        .type("entity-type")
        .alias("superCoolAlias")
        .targetId(targetEtId)
    );

    EntityType customEt = entityTypes.get(customEtId);
    EntityType targetEt = entityTypes.get(targetEtId);

    Map<UUID, EntityType> accessible = Map.of(targetEtId, targetEt);
    doReturn(accessible).when(entityTypeService).getAccessibleEntityTypesById();
    doReturn(customEt).when(entityTypeFlatteningService).getFlattenedEntityType(any(CustomEntityType.class), any(), eq(true));

    // When there's a request for available joins with the custom entity type and target entity type
    AvailableJoinsResponse result = entityTypeService.getAvailableJoins(sources, targetEtId);

    // Then it should include both custom fields in the custom entity type fields
    assertNotNull(result.getAvailableJoinConditions());
    assertEquals(1, result.getAvailableJoinConditions().size());

    // Verify field names are present
    Set<String> customFieldNames = result.getAvailableJoinConditions().stream()
      .map(JoinFieldPair::getSourceField)
      .map(LabeledValue::getValue)
      .collect(Collectors.toSet());
    assertTrue(customFieldNames.contains("colY"));
  }

  @Test
  void getAccessibleEntityTypesById_shouldReturnOnlyAccessibleAndFlattenedEntityTypes() {
    // Setup: two entity types, one accessible, one not
    UUID etId1 = UUID.randomUUID();
    UUID etId2 = UUID.randomUUID();
    EntityType et1 = new EntityType().id(etId1.toString()).name("et1").labelAlias("ET1");
    EntityType et2 = new EntityType().id(etId2.toString()).name("et2").labelAlias("ET2");
    Set<String> userPerms = Set.of("perm1");
    when(repo.getEntityTypeDefinitions(Set.of(), null)).thenReturn(Stream.of(et1, et2));
    when(permissionsService.getUserPermissions()).thenReturn(userPerms);
    when(permissionsService.getRequiredPermissions(et1)).thenReturn(Set.of("perm1"));
    when(permissionsService.getRequiredPermissions(et2)).thenReturn(Set.of("perm2"));
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(etId1), any(), eq(true))).thenReturn(et1);
    when(entityTypeFlatteningService.getFlattenedEntityType(eq(etId2), any(), eq(true))).thenReturn(et2);

    Map<UUID, EntityType> result = new EntityTypeService(repo, entityTypeFlatteningService, null, null, null, null, permissionsService, null, null, executionContext, null, null)
      .getAccessibleEntityTypesById();

    assertEquals(1, result.size());
    assertTrue(result.containsKey(etId1));
    assertFalse(result.containsKey(etId2));
  }

  @Test
  void getAccessibleEntityTypesById_shouldExcludeCustomEntityTypesIfNotAccessible() {
    UUID etId = UUID.randomUUID();
    EntityType et = new EntityType().id(etId.toString()).name("et").labelAlias("ET");
    et.putAdditionalProperty("isCustom", true);
    when(repo.getEntityTypeDefinitions(Set.of(), null)).thenReturn(Stream.of(et));
    when(permissionsService.getUserPermissions()).thenReturn(Set.of());

    EntityTypeService svc = spy(new EntityTypeService(repo, entityTypeFlatteningService, null, null, null, null, permissionsService, null, null, executionContext, null, null));
    doReturn(false).when(svc).currentUserCanAccessCustomEntityType(etId.toString());

    Map<UUID, EntityType> result = svc.getAccessibleEntityTypesById();
    assertTrue(result.isEmpty());
  }

  @Test
  void getAvailableJoins_shouldReturnEmptyListsWhenNoColumns() {
    UUID customEtId = UUID.randomUUID();
    UUID targetEtId = UUID.randomUUID();
    List<EntityTypeSourceEntityType> sources = List.of(
      new EntityTypeSourceEntityType()
        .type("entity-type")
        .alias("superCoolAlias")
        .targetId(targetEtId)
    );
    EntityType customEt = new EntityType().id(customEtId.toString()).columns(List.of());
    EntityType targetEt = new EntityType().id(targetEtId.toString()).columns(List.of());
    Map<UUID, EntityType> accessible = Map.of(targetEtId, targetEt);

    doReturn(accessible).when(entityTypeService).getAccessibleEntityTypesById();
    doReturn(customEt).when(entityTypeFlatteningService).getFlattenedEntityType(any(CustomEntityType.class), any(), eq(true));

    AvailableJoinsResponse result = entityTypeService.getAvailableJoins(sources, targetEtId);
    assertTrue(result.getAvailableJoinConditions().isEmpty());
  }
}
