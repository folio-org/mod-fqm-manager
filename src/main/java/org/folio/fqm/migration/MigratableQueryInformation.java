package org.folio.fqm.migration;

import java.util.List;
import java.util.UUID;
import javax.annotation.CheckForNull;
import lombok.With;

@With
public record MigratableQueryInformation(UUID entityTypeId, @CheckForNull String fqlQuery, List<String> fields) {}
