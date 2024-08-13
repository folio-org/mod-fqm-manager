package org.folio.fqm.resource;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.domain.dto.FqmMigrateResponse;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.service.MigrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MigrationController implements FqmVersionApi {
  private final MigrationService migrationService;
  private final MigratableQueryInformation migratableQueryInformation;
  @Override
  public ResponseEntity<String> getFqmVersion() {
    return new ResponseEntity<>(migrationService.getLatestVersion(), HttpStatus.OK);
  }
  @Override
  public ResponseEntity<FqmMigrateResponse> fqmMigrate() {
    MigratableQueryInformation migratedQueryInformation = migrationService.migrate(migratableQueryInformation);
    return ResponseEntity.ok(migratedQueryInformation);
  }
}
