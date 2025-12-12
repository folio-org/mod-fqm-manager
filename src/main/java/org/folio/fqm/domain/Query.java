package org.folio.fqm.domain;

import lombok.With;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@With
public record Query(UUID queryId, UUID entityTypeId, String fqlQuery, List<String> fields, UUID createdBy,
                    OffsetDateTime startDate, OffsetDateTime endDate, QueryStatus status, String failureReason) {
  public static Query newQuery(UUID entityTypeId, String fqlQuery, List<String> fields, UUID createdBy) {
    return new Query(UUID.randomUUID(), entityTypeId, fqlQuery, fields, createdBy, OffsetDateTime.now(),
      null, QueryStatus.QUEUED, null);
  }
}
