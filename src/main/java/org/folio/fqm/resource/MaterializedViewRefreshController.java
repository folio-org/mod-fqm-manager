package org.folio.fqm.resource;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.service.MaterializedViewRefreshService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MaterializedViewRefreshController implements MaterializedViewsApi {
  private final FolioExecutionContext executionContext;
  private final MaterializedViewRefreshService materializedViewRefreshService;

  @Override
  public ResponseEntity<Void> refreshMaterializedViews() {
    materializedViewRefreshService.refreshMaterializedViews(executionContext.getTenantId());
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }
}
