package org.folio.fqm.migration.strategies;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.migration.AbstractSimpleMigrationStrategy;

/**
 * Version 23 -> 24, done with deprecation of legacy created_date and updated_date fields in user entity types.
 * These fields were renamed to created_date_deprecated and updated_date_deprecated, however, we want queries
 * to be migrated to the correct ones, so those are used here.
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-1006
 */
@Log4j2
@RequiredArgsConstructor
public class V23UserCreatedUpdatedDateFieldDeprecation extends AbstractSimpleMigrationStrategy {

  private static final UUID COMPOSITE_USERS_ID = UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf");
  private static final UUID SIMPLE_USERS_ID = UUID.fromString("bb058933-cd06-4539-bd3a-6f248ff98ee2");

  @Override
  public String getMaximumApplicableVersion() {
    return "23";
  }

  @Override
  public String getLabel() {
    return "V23 -> V24 Legacy user created/updated dates deprecation (part of MODFQMMGR-1006)";
  }

  @Override
  public Map<UUID, Map<String, String>> getFieldChanges() {
    return Map.ofEntries(
      Map.entry(
        SIMPLE_USERS_ID,
        Map.ofEntries(
          Map.entry("created_date", "user_created_date"),
          Map.entry("updated_date", "user_updated_date")
        )
      ),
      Map.entry(
        COMPOSITE_USERS_ID,
        Map.ofEntries(
          Map.entry("users.created_date", "users.user_created_date"),
          Map.entry("users.updated_date", "users.user_updated_date")
        )
      )
    );
  }
}
