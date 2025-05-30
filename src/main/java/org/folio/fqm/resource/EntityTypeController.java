package org.folio.fqm.resource;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.annotation.EntityTypePermissionsRequired;
import org.folio.fqm.service.EntityTypeService;
import org.folio.fqm.service.MigrationService;
import org.folio.querytool.domain.dto.ColumnValues;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.fqm.domain.dto.EntityTypeSummaries;
import org.folio.querytool.rest.resource.EntityTypesApi;
import org.folio.spring.FolioExecutionContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class EntityTypeController implements org.folio.fqm.resource.EntityTypesApi, org.folio.querytool.rest.resource.EntityTypesApi {

  private final EntityTypeService entityTypeService;
  private final MigrationService migrationService;
  private final FolioExecutionContext folioExecutionContext;

  @EntityTypePermissionsRequired
  @Override
  public ResponseEntity<EntityType> getEntityType(UUID entityTypeId, Boolean includeHidden) {
    return ResponseEntity.ok(entityTypeService.getEntityTypeDefinition(entityTypeId, Boolean.TRUE.equals(includeHidden)));
  }

  @Override
  public ResponseEntity<EntityTypeSummaries> getEntityTypeSummary(List<UUID> entityTypeIds, Boolean includeInaccessible, Boolean includeAll) {
    Set<UUID> idsSet = entityTypeIds == null ? Set.of() : Set.copyOf(entityTypeIds);
    // Permissions are handled in the service layer// Permissions are handled in the service layer
    return ResponseEntity.ok(
      new EntityTypeSummaries()
        .entityTypes(entityTypeService.getEntityTypeSummary(idsSet, Boolean.TRUE.equals(includeInaccessible), Boolean.TRUE.equals(includeAll)))
        .version(migrationService.getLatestVersion())
    );
  }

  @EntityTypePermissionsRequired
  @Override
  public ResponseEntity<ColumnValues> getColumnValues(UUID entityTypeId, String fieldName, String search) {
    return ResponseEntity.ok(entityTypeService.getFieldValues(entityTypeId, fieldName, search));
  }

  @Override
  public ResponseEntity<CustomEntityType> getCustomEntityType(UUID entityTypeId) {
    return ResponseEntity.ok(entityTypeService.getCustomEntityTypeWithAccessCheck(entityTypeId));
  }

  @Override
  public ResponseEntity<CustomEntityType> createCustomEntityType(CustomEntityType customEntityType) {
    var updatedCustomEntityType = entityTypeService.createCustomEntityType(customEntityType);
    return ResponseEntity.created(URI.create("/entity-types/custom/" + customEntityType.getId()))
      .body(updatedCustomEntityType);
  }

  @Override
  public ResponseEntity<CustomEntityType> updateCustomEntityType(UUID entityTypeId, CustomEntityType customEntityType) {
    return ResponseEntity.ok(entityTypeService.updateCustomEntityType(entityTypeId, customEntityType));
  }

  @Override
  public ResponseEntity<Void> deleteCustomEntityType(UUID entityTypeId) {
    entityTypeService.deleteCustomEntityType(entityTypeId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public Optional<NativeWebRequest> getRequest() {
    return EntityTypesApi.super.getRequest();
  }
}
