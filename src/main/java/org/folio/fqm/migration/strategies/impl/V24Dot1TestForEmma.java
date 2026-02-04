package org.folio.fqm.migration.strategies.impl;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.migration.strategies.AbstractSimpleMigrationStrategy;
import org.folio.fqm.migration.warnings.FieldWarningFactory;
import org.folio.fqm.migration.warnings.RemovedFieldWarning;

/**
 * Changes the following:
 * - simple_FOLIO_user ET is replaced with simple_user_details
 * - email field in simple_FOLIO_user ET is changed to phone
 * - last_name_first_name field in simple_FOLIO_user ET is removed with a warning
 * - updated_by_user_id field in simple_budget ET is changed to updated_by_user_id2
 */
@Log4j2
@RequiredArgsConstructor
public class V24Dot1TestForEmma extends AbstractSimpleMigrationStrategy {

  private static final UUID SIMPLE_USER_DETAILS = UUID.fromString("bb058933-cd06-4539-bd3a-6f248ff98ee2");
  private static final UUID SIMPLE_FOLIO_USER_DETAILS = UUID.fromString("f2615ea6-450b-425d-804d-6a495afd9308");
  private static final UUID SIMPLE_BUDGET = UUID.fromString("71525bf3-ea51-47c4-bb18-7ab6f07d2b42");
  private static final UUID COMPOSITE_BUDGET = UUID.fromString("592f6e78-51f0-40d5-9857-5b47976afb8e");

  @Override
  public String getMaximumApplicableVersion() {
    return "24.3";
  }

  @Override
  public String getLabel() {
    return "Test for emma";
  }

  @Override
  public Map<UUID, Map<String, UUID>> getEntityTypeSourceMaps() {
    return Map.of(COMPOSITE_BUDGET, Map.of("budget", SIMPLE_BUDGET));
  }

  @Override
  public Map<UUID, Map<String, FieldWarningFactory>> getFieldWarnings() {
    return Map.of(
      SIMPLE_FOLIO_USER_DETAILS,
      Map.of("last_name_first_name", RemovedFieldWarning.withAlternative("testing!"))
    );
  }

  @Override
  public Map<UUID, UUID> getEntityTypeChanges() {
    return Map.of(SIMPLE_FOLIO_USER_DETAILS, SIMPLE_USER_DETAILS);
  }

  @Override
  public Map<UUID, Map<String, String>> getFieldChanges() {
    return Map.of(
      SIMPLE_FOLIO_USER_DETAILS,
      Map.of("email", "phone"),
      SIMPLE_BUDGET,
      Map.of("updated_by_user_id", "updated_by_user_id2")
    );
  }
}
