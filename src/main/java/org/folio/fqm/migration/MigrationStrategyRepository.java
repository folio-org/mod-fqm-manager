package org.folio.fqm.migration;

import java.util.List;
import org.folio.fqm.client.SettingsClient;
import org.folio.fqm.client.LocationUnitsClient;
import org.folio.fqm.client.LocationsClient;
import org.folio.fqm.client.ModesOfIssuanceClient;
import org.folio.fqm.client.OrganizationsClient;
import org.folio.fqm.client.PatronGroupsClient;
import org.folio.fqm.migration.strategies.V0POCMigration;
import org.folio.fqm.migration.strategies.V10OrganizationStatusValueChange;
import org.folio.fqm.migration.strategies.V11OrganizationNameCodeOperatorChange;
import org.folio.fqm.migration.strategies.V12PurchaseOrderIdFieldRemoval;
import org.folio.fqm.migration.strategies.V13CustomFieldRename;
import org.folio.fqm.migration.strategies.V14ItemLocLibraryValueChange;
import org.folio.fqm.migration.strategies.V17ContainsAnyToInOperatorMigration;
import org.folio.fqm.migration.strategies.V18NotContainsAnyToNinOperatorMigration;
import org.folio.fqm.migration.strategies.V19RegexOperatorMigration;
import org.folio.fqm.migration.strategies.V1ModeOfIssuanceConsolidation;
import org.folio.fqm.migration.strategies.V20ContainsAllToEqOperatorMigration;
import org.folio.fqm.migration.strategies.V22UserCustomFieldMigration;
import org.folio.fqm.migration.strategies.V2ResourceTypeConsolidation;
import org.folio.fqm.migration.strategies.V3RamsonsFieldCleanup;
import org.folio.fqm.migration.strategies.V4DateFieldTimezoneAddition;
import org.folio.fqm.migration.strategies.V5UUIDNotEqualOperatorRemoval;
import org.folio.fqm.migration.strategies.V6ModeOfIssuanceValueChange;
import org.folio.fqm.migration.strategies.V7PatronGroupsValueChange;
import org.folio.fqm.migration.strategies.V8LocationValueChange;
import org.folio.fqm.migration.strategies.V9LocLibraryValueChange;
import org.folio.fqm.migration.strategies.V15AlertsAndReportingCodesRemoval;
import org.folio.fqm.migration.strategies.V16OrganizationSimpleToCompositeMigration;
import org.folio.fqm.migration.strategies.V21NotContainsAllToNeqOperatorMigration;
import org.folio.spring.FolioExecutionContext;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

@Component
public class MigrationStrategyRepository {

  private final List<MigrationStrategy> migrationStrategies;

  public MigrationStrategyRepository(
    SettingsClient settingsClient,
    LocationsClient locationsClient,
    LocationUnitsClient locationUnitsClient,
    ModesOfIssuanceClient modesOfIssuanceClient,
    OrganizationsClient organizationsClient,
    PatronGroupsClient patronGroupsClient,
    DSLContext jooqContext,
    FolioExecutionContext executionContext
  ) {
    this.migrationStrategies =
      List.of(
        new V0POCMigration(),
        new V1ModeOfIssuanceConsolidation(),
        new V2ResourceTypeConsolidation(),
        new V3RamsonsFieldCleanup(),
        new V4DateFieldTimezoneAddition(settingsClient),
        new V5UUIDNotEqualOperatorRemoval(),
        new V6ModeOfIssuanceValueChange(modesOfIssuanceClient),
        new V7PatronGroupsValueChange(patronGroupsClient),
        new V8LocationValueChange(locationsClient),
        new V9LocLibraryValueChange(locationUnitsClient),
        new V10OrganizationStatusValueChange(),
        new V11OrganizationNameCodeOperatorChange(organizationsClient),
        new V12PurchaseOrderIdFieldRemoval(),
        new V13CustomFieldRename(executionContext, jooqContext),
        new V14ItemLocLibraryValueChange(locationUnitsClient),
        new V15AlertsAndReportingCodesRemoval(),
        new V16OrganizationSimpleToCompositeMigration(),
        new V17ContainsAnyToInOperatorMigration(),
        new V18NotContainsAnyToNinOperatorMigration(),
        new V19RegexOperatorMigration(),
        new V20ContainsAllToEqOperatorMigration(),
        new V21NotContainsAllToNeqOperatorMigration(),
        new V22UserCustomFieldMigration()
      // adding a strategy? be sure to update the `CURRENT_VERSION` in MigrationConfiguration!
      );
  }

  public List<MigrationStrategy> getMigrationStrategies() {
    return this.migrationStrategies;
  }
}
