package org.folio.fqm.resource;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.service.QueryManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.folio.fqm.domain.dto.PurgedQueries;

@RestController
@RequiredArgsConstructor
public class PurgeQueryController implements PurgeQueryApi {
  private final QueryManagementService queryManagementService;

  @Override
  public ResponseEntity<PurgedQueries> deleteOldQueries() {
    return new ResponseEntity<>(queryManagementService.deleteOldQueries(), HttpStatus.OK);
  }
}
