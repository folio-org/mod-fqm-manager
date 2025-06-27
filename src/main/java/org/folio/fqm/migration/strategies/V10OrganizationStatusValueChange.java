package org.folio.fqm.migration.strategies;

import java.util.Map;
import java.util.UUID;

import lombok.extern.log4j.Log4j2;
import org.folio.fql.service.FqlService;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationUtils;

/**
 * Version 10 -> 11, handles a change in the status field for the organizations entity types.
 *
 * Originally, values for this field were stored lowercase, but they were changed to be title cased.
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-602 for adding this migration
 */
@Log4j2
public class V10OrganizationStatusValueChange implements MigrationStrategy {

  private static final UUID ORGANIZATIONS_ENTITY_TYPE_ID = UUID.fromString("b5ffa2e9-8080-471a-8003-a8c5a1274503");
  private static final String FIELD_NAME = "status";

  private static final Map<String, String> NEW_VALUES = Map.ofEntries(
    Map.entry("active", "Active"),
    Map.entry("pending", "Pending"),
    Map.entry("inactive", "Inactive")
  );

  @Override
  public String getMaximumApplicableVersion() {
    return "10";
  }

  @Override
  public String getLabel() {
    return "V10 -> V11 organization status value transformation (MODFQMMGR-602)";
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
    return query.withFqlQuery(
      MigrationUtils.migrateFqlValues(
        query.fqlQuery(),
        key -> ORGANIZATIONS_ENTITY_TYPE_ID.equals(query.entityTypeId()) && FIELD_NAME.equals(key),
        (key, value, fql) -> NEW_VALUES.getOrDefault(value, value)
      )
    );
  }
}
