package org.folio.fqm.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record Query(UUID queryId, UUID entityTypeId, String fqlQuery, List<String> fields, UUID createdBy,
                    OffsetDateTime startDate, OffsetDateTime endDate, QueryStatus status, String failureReason,
                    boolean crossTenant) {
  public static Query newQuery(UUID entityTypeId, String fqlQuery, List<String> fields, UUID createdBy, boolean crossTenant) {
    return new Query(UUID.randomUUID(), entityTypeId, fqlQuery, fields, createdBy, OffsetDateTime.now(),
      null, QueryStatus.IN_PROGRESS, null, crossTenant);
  }
}
