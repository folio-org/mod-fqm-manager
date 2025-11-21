package org.folio.fqm.service;

import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.CustomFieldMetadata;
import org.folio.querytool.domain.dto.CustomFieldType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class EntityTypeValidationService {

  private final EntityTypeRepository entityTypeRepository;
  private final FolioExecutionContext folioExecutionContext;

  public void validateCustomEntityType(UUID entityTypeId, CustomEntityType customEntityType) {
    validateEntityType(entityTypeId, customEntityType, null);
    if (customEntityType.getOwner() == null) {
      throw new InvalidEntityTypeDefinitionException("Custom entity type must have an owner", customEntityType);
    }
    if (customEntityType.getShared() == null) {
      throw new InvalidEntityTypeDefinitionException("Custom entity type must have a shared property", customEntityType);
    }
    if (!Boolean.TRUE.equals(customEntityType.getIsCustom())) {
      throw new EntityTypeNotFoundException(entityTypeId,
          String.format("Entity type %s is not a custom entity type", entityTypeId));
    }
    if (customEntityType.getSources() != null && !customEntityType.getSources()
      .stream()
      .allMatch(EntityTypeSourceEntityType.class::isInstance)) {
      throw new InvalidEntityTypeDefinitionException("Custom entity types must contain only entity-type sources", customEntityType);
    }
    if (customEntityType.getColumns() != null && !customEntityType.getColumns()
      .isEmpty()) {
      throw new InvalidEntityTypeDefinitionException("Custom entity types must not contain columns", customEntityType);
    }
    if (customEntityType.getCustomFieldEntityTypeId() != null) {
      throw new InvalidEntityTypeDefinitionException("Custom field entity type id must not be defined for custom entity types",
          customEntityType);
    }
    if (customEntityType.getSourceView() != null) {
      throw new InvalidEntityTypeDefinitionException("Custom entity types must not contain a sourceView property",
          customEntityType);
    }
    if (customEntityType.getSourceViewExtractor() != null) {
      throw new InvalidEntityTypeDefinitionException("Custom entity types must not contain a sourceViewExtractor property",
          customEntityType);
    }
    if (Boolean.TRUE.equals(customEntityType.getCrossTenantQueriesEnabled())) {
      throw new InvalidEntityTypeDefinitionException("Custom entity must not have cross-tenant queries enabled", customEntityType);
    }
    if (customEntityType.getPrivate() == null) {
      throw new InvalidEntityTypeDefinitionException("The \"private\" property must be set", customEntityType);
    }
  }

  /**
   * Validates the structure and integrity of an {@link EntityType} definition.
   * <p>
   * This method checks that the entity type has a valid UUID, a non-null and non-blank name, the
   * private property is set, and all sources and columns are valid. For sources of type
   * {@link EntityTypeSourceEntityType}, it ensures the referenced entity type exists (unless a list
   * of valid entity type IDs is provided, in which case it checks against that list). For columns of
   * type {@link CustomFieldType}, it ensures that required custom field metadata properties are
   * present and non-blank.
   * </p>
   *
   * @param entityTypeId   the expected UUID of the entity type (should match entityType.getId())
   * @param entityType     the {@link EntityType} to validate
   * @param validTargetIds optional list of valid entity type IDs (as strings) to check source
   *                       references against; if null, will check existence in the repository
   * @throws InvalidEntityTypeDefinitionException if any validation check fails
   */
  @SuppressWarnings({ "java:S2589", "java:S2583" }) // Suppress incorrect warnings about null check always returning false
  public void validateEntityType(UUID entityTypeId, EntityType entityType, List<UUID> validTargetIds) {
    if (entityType.getId() == null || entityTypeId == null) {
      throw new InvalidEntityTypeDefinitionException("Entity type ID cannot be null", entityTypeId);
    }
    try {
      UUID.fromString(entityType.getId());
    } catch (IllegalArgumentException e) {
      throw new InvalidEntityTypeDefinitionException("Invalid string provided for entity type ID", entityTypeId);
    }
    if (!entityTypeId.toString()
      .equals(entityType.getId())) {
      throw new InvalidEntityTypeDefinitionException(
          "Entity type ID in the request body does not match the entity type ID in the URL", entityTypeId);
    }
    if (entityType.getName() == null || entityType.getName()
      .isBlank()) {
      throw new InvalidEntityTypeDefinitionException("Entity type name cannot be null or blank", entityTypeId);
    }
    if (entityType.getPrivate() == null) {
      throw new InvalidEntityTypeDefinitionException("Entity type must have private property set", entityTypeId);
    }

    validateSources(entityType, validTargetIds);
    validateColumns(entityType);
  }

  @SuppressWarnings({ "java:S2589", "java:S2583" }) // Suppress incorrect warnings about null check always returning false
  private void validateSources(EntityType entityType, List<UUID> validTargetIds) {
    if (entityType.getSources() == null) {
      throw new InvalidEntityTypeDefinitionException("Entity type must have at least one source defined", entityType);
    }
    for (EntityTypeSource source : entityType.getSources()) {
      if (source.getAlias() == null || source.getAlias()
        .isBlank()) {
        throw new InvalidEntityTypeDefinitionException("Source alias cannot be null or blank", entityType);
      }
      if (source.getAlias()
        .contains(".")) {
        throw new InvalidEntityTypeDefinitionException(
            String.format("Invalid source alias: '%s'. Source aliases must not contain '.'", source.getAlias()), entityType);
      }
      if (source.getType() == null) {
        throw new InvalidEntityTypeDefinitionException("Source type cannot be null", entityType);
      }
      if (!source.getType()
        .equals("db")
          && !source.getType()
            .equals("entity-type")) {
        throw new InvalidEntityTypeDefinitionException("Source type must be either 'db' or 'entity-type'", entityType);
      }
      if (source instanceof EntityTypeSourceEntityType entityTypeSource) {
        validateEntityTypeSource(entityType, entityTypeSource.getTargetId(), validTargetIds);
      }
    }
  }

  private void validateEntityTypeSource(EntityType entityType, UUID targetId, List<UUID> validTargetIds) {
    if (targetId == null) {
      throw new InvalidEntityTypeDefinitionException("Source entity type ID cannot be null for entity-type sources", entityType);
    }
    if (validTargetIds == null) {
      if (entityTypeRepository.getEntityTypeDefinition(targetId, folioExecutionContext.getTenantId())
        .isEmpty()) {
        throw new InvalidEntityTypeDefinitionException(
            "Source with target ID " + targetId + " does not correspond to a valid entity type", entityType);
      }
    } else if (!validTargetIds.contains(UUID.fromString(targetId.toString()))) {
      throw new InvalidEntityTypeDefinitionException(
          "Source with target ID " + targetId + " does not correspond to a valid entity type", entityType);
    }
  }

  @SuppressWarnings({ "java:S2589" }) // Suppress incorrect warnings about null check always returning false
  private static void validateColumns(EntityType entityType) {
    if (entityType.getColumns() != null) {
      for (EntityTypeColumn column : entityType.getColumns()) {
        if (column.getDataType() instanceof CustomFieldType customFieldType) {
          CustomFieldMetadata customFieldMetadata = customFieldType.getCustomFieldMetadata();
          if (customFieldMetadata.getConfigurationView() == null || customFieldMetadata.getConfigurationView()
            .isBlank()) {
            throw new InvalidEntityTypeDefinitionException("Custom field metadata must have a configuration view defined",
                UUID.fromString(entityType.getId()));
          }
          if (customFieldMetadata.getDataExtractionPath() == null || customFieldMetadata.getDataExtractionPath()
            .isBlank()) {
            throw new InvalidEntityTypeDefinitionException("Custom field metadata must have a data extraction path defined",
                UUID.fromString(entityType.getId()));
          }
        }
      }
    }
  }
}
