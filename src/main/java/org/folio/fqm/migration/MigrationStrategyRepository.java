package org.folio.fqm.migration;

import java.util.List;
import org.folio.fqm.client.LocationUnitsClient;
import org.folio.fqm.client.LocationsClient;
import org.folio.fqm.client.ModesOfIssuanceClient;
import org.folio.fqm.client.OrganizationsClient;
import org.folio.fqm.client.PatronGroupsClient;
import org.folio.fqm.client.LocaleClient;
import org.folio.fqm.migration.strategies.MigrationStrategy;
import org.folio.fqm.migration.strategies.impl.V0POCMigration;
import org.folio.fqm.migration.strategies.impl.V10OrganizationStatusValueChange;
import org.folio.fqm.migration.strategies.impl.V11OrganizationNameCodeOperatorChange;
import org.folio.fqm.migration.strategies.impl.V12PurchaseOrderIdFieldRemoval;
import org.folio.fqm.migration.strategies.impl.V13CustomFieldRename;
import org.folio.fqm.migration.strategies.impl.V14ItemLocLibraryValueChange;
import org.folio.fqm.migration.strategies.impl.V15AlertsAndReportingCodesRemoval;
import org.folio.fqm.migration.strategies.impl.V16OrganizationSimpleToCompositeMigration;
import org.folio.fqm.migration.strategies.impl.V17ContainsAnyToInOperatorMigration;
import org.folio.fqm.migration.strategies.impl.V18NotContainsAnyToNinOperatorMigration;
import org.folio.fqm.migration.strategies.impl.V19RegexOperatorMigration;
import org.folio.fqm.migration.strategies.impl.V1ModeOfIssuanceConsolidation;
import org.folio.fqm.migration.strategies.impl.V20ContainsAllToEqOperatorMigration;
import org.folio.fqm.migration.strategies.impl.V21NotContainsAllToNeqOperatorMigration;
import org.folio.fqm.migration.strategies.impl.V22UserCustomFieldMigration;
import org.folio.fqm.migration.strategies.impl.V23UserCreatedUpdatedDateFieldDeprecation;
import org.folio.fqm.migration.strategies.impl.V24Dot1TestForEmma;
import org.folio.fqm.migration.strategies.impl.V24InvoiceSimpleToCompositeMigration;
import org.folio.fqm.migration.strategies.impl.V2ResourceTypeConsolidation;
import org.folio.fqm.migration.strategies.impl.V3RamsonsFieldCleanup;
import org.folio.fqm.migration.strategies.impl.V4DateFieldTimezoneAddition;
import org.folio.fqm.migration.strategies.impl.V5UUIDNotEqualOperatorRemoval;
import org.folio.fqm.migration.strategies.impl.V6ModeOfIssuanceValueChange;
import org.folio.fqm.migration.strategies.impl.V7PatronGroupsValueChange;
import org.folio.fqm.migration.strategies.impl.V8LocationValueChange;
import org.folio.fqm.migration.strategies.impl.V9LocLibraryValueChange;
import org.folio.spring.FolioExecutionContext;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

@Component
public class MigrationStrategyRepository {

  private final List<MigrationStrategy> migrationStrategies;

  public MigrationStrategyRepository(
    LocaleClient localeClient,
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
        new V4DateFieldTimezoneAddition(localeClient),
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
        new V22UserCustomFieldMigration(),
        new V23UserCreatedUpdatedDateFieldDeprecation(),
        new V24InvoiceSimpleToCompositeMigration(),
        new V24Dot1TestForEmma()
        // adding a strategy? be sure to update the `CURRENT_VERSION` in MigrationConfiguration!
      );
  }

  public List<MigrationStrategy> getMigrationStrategies() {
    return this.migrationStrategies;
  }
}
