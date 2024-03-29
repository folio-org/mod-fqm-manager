package org.folio.fqm.resource;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.service.DataRefreshService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DataRefreshController implements MaterializedViewsApi {
  private final FolioExecutionContext executionContext;
  private final DataRefreshService dataRefreshService;

  @Override
  public ResponseEntity<Void> refreshData() {
    dataRefreshService.refreshData(executionContext.getTenantId());
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }
}
