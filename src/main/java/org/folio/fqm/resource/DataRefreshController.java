package org.folio.fqm.resource;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.domain.dto.DataRefreshResponse;
import org.folio.fqm.service.DataRefreshService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DataRefreshController implements MaterializedViewsApi {
  private final FolioExecutionContext executionContext;
  private final DataRefreshService dataRefreshService;

  @Override
  public ResponseEntity<DataRefreshResponse> refreshData() {
    return ResponseEntity.ok(dataRefreshService.refreshData(executionContext.getTenantId()));
  }
}
