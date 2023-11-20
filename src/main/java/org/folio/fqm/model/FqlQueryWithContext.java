package org.folio.fqm.model;

import java.util.UUID;

public record FqlQueryWithContext(String tenantId, UUID entityTypeId, String fqlQuery, boolean sortResults) {
}
