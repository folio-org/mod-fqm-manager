package org.folio.fqm.resource;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.annotation.EntityTypePermissionsRequired;
import org.folio.fqm.service.EntityTypeService;
import org.folio.querytool.domain.dto.ColumnValues;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.fqm.domain.dto.EntityTypeSummary;
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

  @EntityTypePermissionsRequired
  @Override
  public ResponseEntity<EntityType> getEntityType(UUID entityTypeId) {
    return ResponseEntity.ok(entityTypeService.getEntityTypeDefinition(entityTypeId));
  }

  @Override
  public ResponseEntity<List<EntityTypeSummary>> getEntityTypeSummary(List<UUID> entityTypeIds, Boolean includeInaccessible) {
    Set<UUID> idsSet = entityTypeIds == null ? Set.of() : Set.copyOf(entityTypeIds);
    // Permissions are handled in the service layer
    return ResponseEntity.ok(entityTypeService.getEntityTypeSummary(idsSet, Boolean.TRUE.equals(includeInaccessible)));
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
