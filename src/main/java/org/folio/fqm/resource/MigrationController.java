package org.folio.fqm.resource;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.domain.dto.FqmMigrateRequest;
import org.folio.fqm.domain.dto.FqmMigrateResponse;
import org.folio.fqm.domain.dto.FqmMigrateResponseWarningsInner;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.service.MigrationService;
import org.folio.spring.i18n.service.TranslationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
public class MigrationController implements FqmVersionApi {
  private final MigrationService migrationService;

  private final TranslationService translationService;

  @Override
  public ResponseEntity<String> getFqmVersion() {
    return new ResponseEntity<>(migrationService.getLatestVersion(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<FqmMigrateResponse> fqmMigrate(FqmMigrateRequest fqmMigrateRequest) {
    MigratableQueryInformation migratableQueryInformation = new MigratableQueryInformation(
      fqmMigrateRequest.getEntityTypeId(),
      fqmMigrateRequest.getFqlQuery(),
      fqmMigrateRequest.getFields()
    );

    MigratableQueryInformation updatedQueryInfo = migrationService.migrate(migratableQueryInformation);

    FqmMigrateResponse fqmMigrateResponse = new FqmMigrateResponse()
      .entityTypeId(updatedQueryInfo.entityTypeId())
      .fqlQuery(updatedQueryInfo.fqlQuery())
      .fields(updatedQueryInfo.fields())
      .warnings(updatedQueryInfo.warnings().stream()
        .map(warning -> new FqmMigrateResponseWarningsInner()
          .type(warning.getType() != null ? warning.getType().toString() : null)
          .description(warning.getDescription(translationService) != null ?
            warning.getDescription(translationService) : "No description provided"
          )
        )
        .toList()
      );

    return new ResponseEntity<>(fqmMigrateResponse, HttpStatus.OK);
  }
}
