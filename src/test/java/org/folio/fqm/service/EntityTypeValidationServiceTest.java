package org.folio.fqm.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.CustomFieldMetadata;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class EntityTypeValidationServiceTest {

  private static final String TENANT_ID = "tenant_01";

  @Mock
  private EntityTypeRepository repo;

  @Mock
  private FolioExecutionContext executionContext;

  @Spy
  @InjectMocks
  private EntityTypeValidationService entityTypeValidationService;

  @Test
  void validateCustomEntityType_shouldThrowException_whenSourceViewIsNotNull() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(true)
      .sourceView("some_source_view");

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when sourceView is not null");

    assertEquals("Custom entity types must not contain a sourceView property", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenSourceViewExtractorIsNotNull() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(true)
      .sourceViewExtractor("some_source_view_extractor");

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when sourceViewExtractor is not null");

    assertEquals("Custom entity types must not contain a sourceViewExtractor property", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenCustomFieldEntityTypeIdIsPresent() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(true)
      .customFieldEntityTypeId(entityTypeId.toString());

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when customFieldEntityTypeId refers to itself");

    assertEquals("Custom field entity type id must not be defined for custom entity types", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenOwnerIsNull() {
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .name("test")
      ._private(false)
      .isCustom(true);

    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when owner is null");

    assertEquals("Custom entity type must have an owner", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenIsCustomIsNull() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(null); // isCustom is null

    // Act & Assert
    EntityTypeNotFoundException exception = assertThrows(EntityTypeNotFoundException.class,
      () -> entityTypeValidationService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw EntityTypeNotFoundException when isCustom is null");

    assertEquals("Entity type " + entityTypeId + " is not a custom entity type", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenSharedIsNull() {
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(true)
      .shared(null);

    InvalidEntityTypeDefinitionException exception = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateCustomEntityType(entityTypeId, customEntityType)
    );
    assertEquals("Custom entity type must have a shared property", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenIsCustomIsFalse() {
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(false)
      .shared(true);

    EntityTypeNotFoundException exception = assertThrows(
      EntityTypeNotFoundException.class,
      () -> entityTypeValidationService.validateCustomEntityType(entityTypeId, customEntityType)
    );

    assertEquals("Entity type " + entityTypeId + " is not a custom entity type", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenSourcesAreNotEntityTypeSourceEntityType() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(true)
      .sources(List.of(new EntityTypeSourceDatabase().type("db").alias("source1"))); // Not an EntityTypeSourceEntityType

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when sources are not all EntityTypeSourceEntityType");

    assertEquals("Custom entity types must contain only entity-type sources", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenColumnsAreNotEmpty() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(true)
      .columns(List.of(new EntityTypeColumn().name("test_column"))); // Non-empty columns

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when columns are not empty");

    assertEquals("Custom entity types must not contain columns", exception.getMessage());
  }

  @Test
  void validateCustomEntityType_shouldThrowException_whenCrossTenantQueriesEnabled() {
    // Arrange
    UUID entityTypeId = UUID.randomUUID();
    CustomEntityType customEntityType = new CustomEntityType()
      .id(entityTypeId.toString())
      .owner(UUID.randomUUID())
      .name("test")
      ._private(false)
      .isCustom(true)
      .crossTenantQueriesEnabled(true); // Cross-tenant queries enabled

    // Act & Assert
    InvalidEntityTypeDefinitionException exception = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateCustomEntityType(entityTypeId, customEntityType),
      "Should throw InvalidEntityTypeDefinitionException when crossTenantQueriesEnabled is true");

    assertEquals("Custom entity must not have cross-tenant queries enabled", exception.getMessage());
  }

  @Test
  void validateEntityType_shouldThrowException_whenIdIsNull() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id(null)
      .name("Test")
      ._private(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));

    InvalidEntityTypeDefinitionException ex = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex.getMessage().contains("Entity type ID cannot be null"));

    EntityType entityTypeWithId = entityType.id(entityTypeId.toString());
    ex = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(null, entityTypeWithId, null));
    assertTrue(ex.getMessage().contains("Entity type ID cannot be null"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenIdDoesNotMatch() {
    UUID entityTypeId = UUID.randomUUID();
    UUID differentId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id(differentId.toString())
      .name("Test")
      ._private(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));

    InvalidEntityTypeDefinitionException ex = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex.getMessage().contains("Entity type ID in the request body does not match"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenIdIsInvalidUUID() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id("not-a-uuid")
      .name("Test")
      ._private(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));

    InvalidEntityTypeDefinitionException ex = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex.getMessage().contains("Invalid string provided for entity type ID"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenNameIsNullOrBlank() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name(null)
      ._private(true)
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));

    InvalidEntityTypeDefinitionException ex1 = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex1.getMessage().contains("Entity type name cannot be null or blank"));

    entityType.name("");
    InvalidEntityTypeDefinitionException ex2 = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex2.getMessage().contains("Entity type name cannot be null or blank"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenPrivateIsNull() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(null)
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));

    InvalidEntityTypeDefinitionException ex = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex.getMessage().contains("Entity type must have private property set"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenSourceAliasIsInvalid() {
    UUID entityTypeId = UUID.randomUUID();
    EntityTypeSourceEntityType source = EntityTypeSourceEntityType.builder().alias(null).type("entity-type").targetId(entityTypeId).build();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .sources(List.of(source));

    InvalidEntityTypeDefinitionException ex1 = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex1.getMessage().contains("Source alias cannot be null or blank"));

    source.alias("");
    InvalidEntityTypeDefinitionException ex2 = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex2.getMessage().contains("Source alias cannot be null or blank"));

    source.alias("dot.alias");
    InvalidEntityTypeDefinitionException ex3 = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex3.getMessage().contains("must not contain '.'"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenSourceTypeIsNull() {
    UUID entityTypeId = UUID.randomUUID();
    EntityTypeSourceEntityType source = EntityTypeSourceEntityType.builder().alias("alias").type(null).targetId(entityTypeId).build();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .sources(List.of(source));

    InvalidEntityTypeDefinitionException ex = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex.getMessage().contains("Source type cannot be null"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenSourceTypeIsInvalid() {
    UUID entityTypeId = UUID.randomUUID();
    EntityTypeSourceEntityType source = EntityTypeSourceEntityType.builder()
      .alias("alias")
      .type("invalid-type")
      .targetId(entityTypeId)
      .build();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .sources(List.of(source));

    InvalidEntityTypeDefinitionException ex = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, null)
    );
    assertTrue(ex.getMessage().contains("Source type must be either 'db' or 'entity-type'"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenSourceEntityTypeIdIsNull() {
    UUID entityTypeId = UUID.randomUUID();
    EntityTypeSourceEntityType source = EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(null).build();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .sources(List.of(source));

    InvalidEntityTypeDefinitionException ex = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex.getMessage().contains("Source entity type ID cannot be null for entity-type sources"));
  }

  @Test
  void validateEntityType_shouldThrowException_whenSourceEntityTypeIdDoesNotExist() {
    UUID entityTypeId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    EntityTypeSourceEntityType source = EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(targetId).build();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .sources(List.of(source));

    when(repo.getEntityTypeDefinition(targetId, null)).thenReturn(Optional.empty());

    InvalidEntityTypeDefinitionException ex = assertThrows(InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, null));
    assertTrue(ex.getMessage().contains("Source with target ID " + targetId + " does not correspond to a valid entity type"));
  }

  @Test
  void validateEntityType_shouldNotThrowException_whenSourceEntityTypeIdIsValid() {
    UUID entityTypeId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    EntityTypeSourceEntityType source = EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(targetId).build();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .sources(List.of(source));

    when(repo.getEntityTypeDefinition(targetId, null)).thenReturn(Optional.of(new EntityType()));
    assertDoesNotThrow(() -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, null));
  }

  @Test
  void validateEntityType_shouldThrowException_whenCustomFieldMetadataConfigurationViewIsNullOrBlank() {
    UUID entityTypeId = UUID.randomUUID();
    CustomFieldMetadata metadataWithNullView = new CustomFieldMetadata().configurationView(null).dataExtractionPath("path");
    CustomFieldMetadata metadataWithBlankView = new CustomFieldMetadata().configurationView("").dataExtractionPath("path");
    EntityTypeColumn columnWithNullView = new EntityTypeColumn()
      .name("cf1")
      .dataType(new org.folio.querytool.domain.dto.CustomFieldType().customFieldMetadata(metadataWithNullView));
    EntityTypeColumn columnWithBlankView = new EntityTypeColumn()
      .name("cf2")
      .dataType(new org.folio.querytool.domain.dto.CustomFieldType().customFieldMetadata(metadataWithBlankView));
    EntityType entityTypeNull = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .columns(List.of(columnWithNullView))
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));
    EntityType entityTypeBlank = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .columns(List.of(columnWithBlankView))
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));

    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(repo.getEntityTypeDefinition(entityTypeId, TENANT_ID)).thenReturn(Optional.of(new EntityType()));

    InvalidEntityTypeDefinitionException ex1 = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityTypeNull, null)
    );
    assertEquals("Custom field metadata must have a configuration view defined", ex1.getMessage());

    InvalidEntityTypeDefinitionException ex2 = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityTypeBlank, null)
    );
    assertEquals("Custom field metadata must have a configuration view defined", ex2.getMessage());
  }

  @Test
  void validateEntityType_shouldThrowException_whenCustomFieldMetadataDataExtractionPathIsNullOrBlank() {
    UUID entityTypeId = UUID.randomUUID();
    CustomFieldMetadata metadataWithNullPath = new CustomFieldMetadata().configurationView("view").dataExtractionPath(null);
    CustomFieldMetadata metadataWithBlankPath = new CustomFieldMetadata().configurationView("view").dataExtractionPath("");
    EntityTypeColumn columnWithNullPath = new EntityTypeColumn()
      .name("cf1")
      .dataType(new org.folio.querytool.domain.dto.CustomFieldType().customFieldMetadata(metadataWithNullPath));
    EntityTypeColumn columnWithBlankPath = new EntityTypeColumn()
      .name("cf2")
      .dataType(new org.folio.querytool.domain.dto.CustomFieldType().customFieldMetadata(metadataWithBlankPath));
    EntityType entityTypeNull = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .columns(List.of(columnWithNullPath))
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));
    EntityType entityTypeBlank = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .columns(List.of(columnWithBlankPath))
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));

    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(repo.getEntityTypeDefinition(entityTypeId, TENANT_ID)).thenReturn(Optional.of(new EntityType()));

    InvalidEntityTypeDefinitionException ex1 = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityTypeNull, null)
    );
    assertEquals("Custom field metadata must have a data extraction path defined", ex1.getMessage());

    InvalidEntityTypeDefinitionException ex2 = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityTypeBlank, null)
    );
    assertEquals("Custom field metadata must have a data extraction path defined", ex2.getMessage());
  }

  @Test
  void validateEntityType_shouldNotThrow_whenCustomFieldMetadataIsValid() {
    UUID entityTypeId = UUID.randomUUID();
    CustomFieldMetadata validMetadata = new CustomFieldMetadata().configurationView("view").dataExtractionPath("path");
    EntityTypeColumn validColumn = new EntityTypeColumn()
      .name("cf1")
      .dataType(new org.folio.querytool.domain.dto.CustomFieldType().customFieldMetadata(validMetadata));
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test")
      ._private(true)
      .columns(List.of(validColumn))
      .sources(List.of(EntityTypeSourceEntityType.builder().alias("alias").type("entity-type").targetId(entityTypeId).build()));

    when(executionContext.getTenantId()).thenReturn(TENANT_ID);
    when(repo.getEntityTypeDefinition(entityTypeId, TENANT_ID)).thenReturn(Optional.of(new EntityType()));

    assertDoesNotThrow(() -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, null));
  }

  @Test
  void validateEntityType_shouldThrowException_whenSourceIdNotInValidSources() {
    UUID entityTypeId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    List<UUID> validSources = List.of(new UUID(1, 0), new UUID(1, 1));

    EntityTypeSourceEntityType source = new EntityTypeSourceEntityType();
    source.setAlias("sourceAlias");
    source.setType("entity-type");
    source.setTargetId(sourceId);

    EntityType entityType = new EntityType();
    entityType.setId(entityTypeId.toString());
    entityType.setName("Test Entity");
    entityType.setPrivate(false);
    entityType.setSources(List.of(source));
    entityType.setColumns(List.of());

    InvalidEntityTypeDefinitionException ex = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, validSources)
    );
    assertTrue(ex.getMessage().contains("Source with target ID " + sourceId + " does not correspond to a valid entity type"));
  }

  @Test
  void validateEntityType_shouldNotThrow_whenSourceIdIsInValidEntityTypeIds() {
    UUID entityTypeId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    List<UUID> validEntityTypeIds = List.of(sourceId, new UUID(0, 1));

    EntityTypeSourceEntityType source = EntityTypeSourceEntityType.builder()
      .alias("sourceAlias")
      .type("entity-type")
      .targetId(sourceId)
      .build();

    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test Entity")
      ._private(false)
      .sources(List.of(source))
      .columns(List.of());

    assertDoesNotThrow(() -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, validEntityTypeIds));
  }

  @Test
  void validateEntityType_shouldThrowException_whenSourcesIsNull() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .name("Test Entity")
      ._private(true)
      .sources(null);

    InvalidEntityTypeDefinitionException ex = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> entityTypeValidationService.validateEntityType(entityTypeId, entityType, null)
    );
    assertEquals("Entity type must have at least one source defined", ex.getMessage());
  }
}