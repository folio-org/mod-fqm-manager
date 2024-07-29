package org.folio.fqm.migration;

import java.util.List;
import java.util.UUID;
import javax.annotation.CheckForNull;
import lombok.Builder;
import lombok.With;

@With
@Builder
public record MigratableQueryInformation(UUID entityTypeId, @CheckForNull String fqlQuery, List<String> fields) {}
