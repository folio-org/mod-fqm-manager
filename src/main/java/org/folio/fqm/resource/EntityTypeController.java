package org.folio.fqm.resource;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.annotation.EntityTypePermissionsRequired;
import org.folio.fqm.service.EntityTypeService;
import org.folio.fqm.service.MigrationService;
import org.folio.querytool.domain.dto.ColumnValues;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.fqm.domain.dto.EntityTypeSummaries;
import org.folio.querytool.rest.resource.EntityTypesApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class EntityTypeController implements org.folio.fqm.resource.EntityTypesApi, org.folio.querytool.rest.resource.EntityTypesApi {

  private final EntityTypeService entityTypeService;
  private final MigrationService migrationService;

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
  public Optional<NativeWebRequest> getRequest() {
    return EntityTypesApi.super.getRequest();
  }
}
