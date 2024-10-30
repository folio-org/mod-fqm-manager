package org.folio.fqm.model;

import org.folio.querytool.domain.dto.EntityType;

public record FqlQueryWithContext(String tenantId, EntityType entityType, String fqlQuery, boolean sortResults) {}
