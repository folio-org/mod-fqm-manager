package org.folio.fqm.service;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.fqm.client.CrossTenantHttpClient;
import org.folio.fqm.client.LanguageClient;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.AvailableJoins;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.querytool.domain.dto.Join;
import org.folio.querytool.domain.dto.JoinDirection;
import org.folio.querytool.domain.dto.LabeledValue;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
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
  private LocalizationService localizationService;

  @Mock
  private UserTenantService userTenantService;

  @Mock
  private EntityTypeFlatteningService entityTypeFlatteningService;

  @Mock
  private FolioExecutionContext executionContext;

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
          .valueGetter("")
          .joinsTo(List.of(new Join().targetId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
            .targetField("colA")
            .type("equality-cast-uuid")
            .direction(JoinDirection.LEFT))),
        new EntityTypeColumn()
          .name("col2")
          .labelAlias("Column 2")
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
          .valueGetter("")
          .joinsTo(List.of(new Join().targetId(UUID.fromString("00000000-0000-0000-0000-000000000003"))
            .targetField("colX")
            .type("equality-cast-uuid")
            .direction(JoinDirection.INNER))),
        new EntityTypeColumn()
          .name("colB")
          .labelAlias("Column B")
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
          .labelAlias("Column X"),
        new EntityTypeColumn()
          .name("colY")
          .labelAlias("Column Y")
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
          .targetId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
        new EntityTypeSourceEntityType()
          .type("entity-type")
          .alias("et2")
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

    EntityTypeFlatteningService flatteningService = new EntityTypeFlatteningService(repo, localizationService, executionContext, userTenantService, 0L);
    lenient().doAnswer(invocationOnMock -> entityTypes.values().stream()
      .map(et -> flatteningService.getFlattenedEntityType(et, "", true))
      .collect(Collectors.toMap(et -> UUID.fromString(et.getId()), et -> et, (a, b) -> a))
    ).when(entityTypeService).getAccessibleEntityTypesById();
  }

  @Test
  void getAvailableJoins_shouldReturnEmptyWhenAllParamsProvided() {
    // Use entityTypes for customEntityType and targetEntityType
    CustomEntityType customEntityType = new CustomEntityType();
    String customEntityTypeField = "col1";
    UUID targetEntityTypeId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    String targetEntityTypeField = "colA";

    // When there's a request for available joins with those components
    AvailableJoins result = entityTypeService.getAvailableJoins(customEntityType, customEntityTypeField, targetEntityTypeId, targetEntityTypeField);

    // Then the available options are all empty or null, since there are no unknowns
    assertNotNull(result);
    assertTrue(CollectionUtils.isEmpty(result.getTargetEntityTypes()));
    assertTrue(CollectionUtils.isEmpty(result.getCustomEntityTypeFields()));
    assertTrue(CollectionUtils.isEmpty(result.getAvailableTargetEntityTypeFields()));
  }

  @Test
  void getAvailableJoins_shouldReturnTargetEntityTypesWhenCustomEntityTypeIsNull() {
    // When there's a request for available joins without a custom entity type
    AvailableJoins result = entityTypeService.getAvailableJoins(null, null, null, null);

    // Then it should return all available target entity types
    AvailableJoins expected = new AvailableJoins()
      .targetEntityTypes(entityTypeService.getAccessibleEntityTypesById().values().stream()
        .map(et -> new LabeledValue(et.getLabelAlias()).value(et.getId()))
        .sorted(comparing(LabeledValue::getLabel, String.CASE_INSENSITIVE_ORDER))
        .toList())
      .availableTargetEntityTypeFields(null)
      .customEntityTypeFields(null);
    assertEquals(expected, result);
  }

  @Test
  void getAvailableJoins_shouldReturnCustomEntityTypeFieldsWhenCustomEntityTypeFieldIsNull() {
    // Given a custom entity type with a join field and an arbitrary other ET
    UUID customEtId = UUID.randomUUID();
    UUID targetEtId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    EntityType customEt = new EntityType().id(customEtId.toString())
      .columns(List.of(
        new EntityTypeColumn()
          .name("joinField")
          .labelAlias("Join Field")
          .joinsTo(List.of(new Join().targetId(targetEtId).targetField("colA")))
      ));
    EntityType targetEt = entityTypeService.getAccessibleEntityTypesById().get(targetEtId);

    doReturn(Map.of(targetEtId, targetEt)).when(entityTypeService).getAccessibleEntityTypesById();
    doReturn(customEt).when(entityTypeFlatteningService).getFlattenedEntityType(any(CustomEntityType.class), any(), eq(true));

    // When there's a request for available joins with the custom entity type and no specific fields
    AvailableJoins result = entityTypeService.getAvailableJoins(new CustomEntityType().id(customEtId.toString()), null, targetEtId, null);

    // Then it should return the fields from both entity types that can be used in a join between the two
    assertEquals(1, result.getCustomEntityTypeFields().size());
    assertEquals("Join Field", result.getCustomEntityTypeFields().get(0).getLabel());
    assertEquals(List.of(), result.getAvailableTargetEntityTypeFields());
  }

  @Test
  void getAvailableJoins_shouldReturnTargetEntityTypeFieldsWhenTargetEntityTypeFieldIsNull() {
    // Given a custom entity type with a join field and an arbitrary other ET with a joinable field
    UUID customEtId = UUID.randomUUID();
    UUID targetEtId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    EntityType customEt = new EntityType().id(customEtId.toString())
      .columns(List.of(
        new EntityTypeColumn()
          .name("customField")
          .labelAlias("Custom Field")
          .originalEntityTypeId(customEtId)
          .joinsTo(List.of(new Join().targetId(targetEtId).targetField("colX"))))
      );
    EntityType targetEt = entityTypeService.getAccessibleEntityTypesById().get(targetEtId);

    Map<UUID, EntityType> accessible = Map.of(targetEtId, targetEt);

    doReturn(accessible).when(entityTypeService).getAccessibleEntityTypesById();
    doReturn(customEt).when(entityTypeFlatteningService).getFlattenedEntityType(any(CustomEntityType.class), any(), eq(true));

    // When there's a request for available joins with the custom entity type and no specified target field
    AvailableJoins result = entityTypeService.getAvailableJoins(new CustomEntityType().id(customEtId.toString()), "customField", targetEtId, null);

    // Then it should return the fields from the target entity type that can be used in a join with the custom entity type
    assertNotNull(result.getAvailableTargetEntityTypeFields());
    assertEquals(1, result.getAvailableTargetEntityTypeFields().size());
    assertEquals("Column X", result.getAvailableTargetEntityTypeFields().get(0).getLabel());
  }

  @Test
  void getAvailableJoins_shouldReturnAllAccessibleEntityTypesWhenCustomEntityTypeIsNull() {
    // When there's a request for target entity types without a custom entity type set
    AvailableJoins result = entityTypeService.getAvailableJoins(null, null, null, null);

    // Then it should return all accessible entity types
    assertEquals(entityTypeService.getAccessibleEntityTypesById().size(), result.getTargetEntityTypes().size());
    assertTrue(result.getTargetEntityTypes().stream().anyMatch(lv -> "Test Entity 1".equals(lv.getLabel())));
    assertTrue(result.getTargetEntityTypes().stream().anyMatch(lv -> "Test Entity 2".equals(lv.getLabel())));
    assertTrue(result.getTargetEntityTypes().stream().anyMatch(lv -> "Test Entity 3".equals(lv.getLabel())));
    assertTrue(result.getTargetEntityTypes().stream().anyMatch(lv -> "Composite Entity 1-2".equals(lv.getLabel())));
  }

  @Test
  void getAvailableJoins_shouldReturnCustomEntityTypeFieldsForJoinableFields() {
    // Given a custom entity type with a join field pointed at a target entity type
    UUID customEtId = UUID.randomUUID();
    UUID targetEtId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    EntityType customEt = new EntityType().id(customEtId.toString()).columns(List.of(
      new EntityTypeColumn()
        .name("joinField")
        .labelAlias("Join Field")
        .joinsTo(List.of(new Join().targetId(targetEtId)))
    ));
    EntityType targetEt = entityTypeService.getAccessibleEntityTypesById().get(targetEtId);
    Map<UUID, EntityType> accessible = Map.of(targetEtId, targetEt);

    doReturn(accessible).when(entityTypeService).getAccessibleEntityTypesById();
    doReturn(customEt).when(entityTypeFlatteningService).getFlattenedEntityType(any(CustomEntityType.class), any(), eq(true));

    // When there's a request for custom entity type fields with the custom entity type and target entity type ID
    AvailableJoins result = entityTypeService.getAvailableJoins(new CustomEntityType().id(customEtId.toString()), null, targetEtId, null);

    // Then it should return the field from the custom entity type
    assertEquals(1, result.getCustomEntityTypeFields().size());
    assertEquals("Join Field", result.getCustomEntityTypeFields().get(0).getLabel());
  }

  @Test
  void getAvailableJoins_shouldReturnTargetEntityTypeFieldsForJoinableFields() {
    // Given a custom entity type with a join field and an arbitrary target entity type with a join field
    UUID customEtId = UUID.randomUUID();
    UUID targetEtId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    EntityType customEt = new EntityType().id(customEtId.toString()).columns(List.of(
      new EntityTypeColumn()
        .name("customField")
        .labelAlias("Custom Field")
        .originalEntityTypeId(customEtId)
        .joinsTo(List.of(new Join().targetId(targetEtId).targetField("colX")))
    ));
    EntityType targetEt = entityTypeService.getAccessibleEntityTypesById().get(targetEtId);

    Map<UUID, EntityType> accessible = Map.of(targetEtId, targetEt);
    doReturn(accessible).when(entityTypeService).getAccessibleEntityTypesById();
    doReturn(customEt).when(entityTypeFlatteningService).getFlattenedEntityType(any(CustomEntityType.class), any(), eq(true));

    // When there's a request for target entity type fields with the custom entity type and target entity type
    AvailableJoins result = entityTypeService.getAvailableJoins(new CustomEntityType().id(customEtId.toString()), "customField", targetEtId, null);

    // Then it should return the field from the target entity type that can be used in a join
    assertEquals(1, result.getAvailableTargetEntityTypeFields().size());
    assertEquals("Column X", result.getAvailableTargetEntityTypeFields().get(0).getLabel());
  }

  @Test
  void getAvailableJoins_shouldReturnEmptyWhenNoJoinableFields() {
    // Given a custom entity type with no joinable fields and an arbitrary target entity type
    UUID customEtId = UUID.randomUUID();
    UUID targetEtId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    EntityType customEt = new EntityType().id(customEtId.toString()).columns(List.of());
    EntityType targetEt = new EntityType().id(targetEtId.toString()).columns(List.of());

    Map<UUID, EntityType> accessible = Map.of(targetEtId, targetEt);
    doReturn(accessible).when(entityTypeService).getAccessibleEntityTypesById();
    doReturn(customEt).when(entityTypeFlatteningService).getFlattenedEntityType(any(CustomEntityType.class), any(), eq(true));

    // When there's a request for target entity type fields with the custom entity type and target entity type
    AvailableJoins result = entityTypeService.getAvailableJoins(new CustomEntityType().id(customEtId.toString()), null, targetEtId, null);

    // Then it should return an empty list since there are no joinable fields
    assertTrue(result.getAvailableTargetEntityTypeFields().isEmpty());
  }
}
