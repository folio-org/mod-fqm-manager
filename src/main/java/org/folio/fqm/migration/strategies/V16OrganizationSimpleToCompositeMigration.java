package org.folio.fqm.migration.strategies;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.migration.AbstractSimpleMigrationStrategy;

import java.util.Map;
import java.util.UUID;

/**
 * Version 16 -> 17, migrates from simple_organization to composite_organization entity type.
 * The public Organizations ET is being changed from a simple ET to a composite. Because of this,
 * all fields get "organization." prepended to their names.
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-679
 */
@Log4j2
@RequiredArgsConstructor
public class V16OrganizationSimpleToCompositeMigration extends AbstractSimpleMigrationStrategy {

  private static final UUID SIMPLE_ORGANIZATIONS_ID = UUID.fromString("b5ffa2e9-8080-471a-8003-a8c5a1274503");
  private static final UUID COMPOSITE_ORGANIZATIONS_ID = UUID.fromString("e0ea4212-4023-458a-adce-8003ff6c5d9e");

  @Override
  public String getMaximumApplicableVersion() {
    return "16";
  }

  @Override
  public String getLabel() {
    return "V16 -> V17 Organization simple to composite migration (part of MODFQMMGR-679)";
  }

  @Override
  public Map<UUID, UUID> getEntityTypeChanges() {
    return Map.of(SIMPLE_ORGANIZATIONS_ID, COMPOSITE_ORGANIZATIONS_ID);
  }

  @Override
  public Map<UUID, Map<String, String>> getFieldChanges() {
    return Map.of(
      SIMPLE_ORGANIZATIONS_ID,
      Map.of("*", "organization.%s")
    );
  }
}
