package org.folio.fqm.resource;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.service.MigrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MigrationController implements FqmVersionApi {
  private final MigrationService migrationService;

  @Override
  public ResponseEntity<String> getFqmVersion() {
    return new ResponseEntity<>(migrationService.getLatestVersion(), HttpStatus.OK);
  }
}
