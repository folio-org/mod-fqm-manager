package org.folio.fqm.migration.strategies.impl;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.migration.strategies.AbstractSimpleMigrationStrategy;

/**
 * Version 23 -> 24, done with deprecation of legacy created_date and updated_date fields in user entity types.
 * These fields were renamed to created_date_deprecated and updated_date_deprecated, however, we want queries
 * to be migrated to the correct ones, so those are mapped here.
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-1006
 */
@Log4j2
@RequiredArgsConstructor
public class V23UserCreatedUpdatedDateFieldDeprecation extends AbstractSimpleMigrationStrategy {

  private static final UUID SIMPLE_USERS_ID = UUID.fromString("bb058933-cd06-4539-bd3a-6f248ff98ee2");
  private static final UUID COMPOSITE_USERS_ID = UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf");
  private static final UUID COMPOSITE_LOAN_DETAILS_ID = UUID.fromString("d6729885-f2fb-4dc7-b7d0-a865a7f461e4");

  @Override
  public String getMaximumApplicableVersion() {
    return "23";
  }

  @Override
  public String getLabel() {
    return "V23 -> V24 Legacy user created/updated dates deprecation (part of MODFQMMGR-1006)";
  }

  @Override
  public Map<UUID, Map<String, UUID>> getEntityTypeSourceMaps() {
    return Map.ofEntries(
      Map.entry(COMPOSITE_USERS_ID, Map.of("users", SIMPLE_USERS_ID)),
      Map.entry(COMPOSITE_LOAN_DETAILS_ID, Map.of("users", SIMPLE_USERS_ID))
    );
  }

  @Override
  public Map<UUID, Map<String, String>> getFieldChanges() {
    return Map.ofEntries(
      Map.entry(
        SIMPLE_USERS_ID,
        Map.ofEntries(Map.entry("created_date", "user_created_date"), Map.entry("updated_date", "user_updated_date"))
      )
    );
  }
}
