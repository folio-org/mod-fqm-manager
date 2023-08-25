package org.folio.fqm.resource;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.service.QueryManagementService;
import org.folio.querytool.domain.dto.QueryDetails;
import org.folio.querytool.domain.dto.QueryIdentifier;
import org.folio.querytool.domain.dto.ResultsetPage;
import org.folio.querytool.domain.dto.SubmitQuery;
import org.folio.spring.FolioExecutionContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.folio.querytool.rest.resource.FqlQueryApi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FqlQueryController implements FqlQueryApi {

  private final QueryManagementService queryManagementService;
  private final FolioExecutionContext executionContext;

  @Override
  public ResponseEntity<QueryIdentifier> runFqlQueryAsync(SubmitQuery submitQuery) {
    return new ResponseEntity<>(queryManagementService.runFqlQueryAsync(submitQuery), HttpStatus.CREATED);
  }

  @Override
  public ResponseEntity<QueryDetails> getQuery(UUID queryId, Boolean includeResults, Integer offset, Integer limit) {
    Optional<QueryDetails> queryDetails = queryManagementService.getQuery(queryId, Boolean.TRUE.equals(includeResults), offset, limit);
    return queryDetails.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  public ResponseEntity<ResultsetPage> runFqlQuery(String query, UUID entityTypeId, List<String> fields,
                                                   UUID afterId, Integer limit) {
    return ResponseEntity.ok(queryManagementService.runFqlQuery(query, entityTypeId, fields, afterId, limit));
  }

  @Override
  public ResponseEntity<Void> deleteQuery(UUID queryId) {
    queryManagementService.deleteQuery(queryId);
    return ResponseEntity.noContent().build();
  }
}
