package org.folio.fqm.migration.strategies.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.migration.strategies.AbstractSimpleMigrationStrategy;
import org.folio.fqm.migration.types.MigratableFqlFieldAndCondition;
import org.folio.fqm.migration.types.MigratableFqlFieldOnly;
import org.folio.fqm.migration.types.SingleFieldMigrationResult;
import org.folio.fqm.migration.warnings.FieldWarning;
import org.folio.fqm.migration.warnings.RemovedFieldWarning;

/**
 * Version 24 -> 25, done with MODFQMMGR-1221.
 *
 * <p>The "Voucher lines with fund" and "Voucher lines with invoice, fund, organization" composites used to join
 * fund (and, through it, fund type and ledger) on external account number, producing one row per voucher line + fund.
 * Those sources were removed; the fund-related data now lives in the {@code voucher_line.fund_distribution}
 * sub-table, resolved per distribution via {@code fundId}.
 *
 * <ul>
 *   <li>FQL conditions on {@code fund.*}, {@code fund_type.*}, and {@code ledger.*} are moved to the equivalent
 *   {@code voucher_line.fund_distribution[*]->...} property.</li>
 *   <li>Field selections (which can only reference top-level columns) are moved to
 *   {@code voucher_line.fund_distribution}.</li>
 *   <li>{@code fund.locations} changed structure (now names only), so it is migrated with a warning.</li>
 *   <li>Low-value metadata fields (versions, created/updated dates and users, ledger description and fiscal year
 *   one) were not carried over and are removed with a warning.</li>
 * </ul>
 *
 * <p>Note: V24 also declares a maximum applicable version of 24 (CURRENT_VERSION was not bumped with it), so it
 * is re-run for version 24 queries before this one. That is harmless, as V24's changes are idempotent here.
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-1221
 */
@Log4j2
public class V25VoucherLineFundDistributionMigration extends AbstractSimpleMigrationStrategy {

  static final UUID COMPOSITE_VOUCHER_LINE_FUND_ID = UUID.fromString("86475f57-7ee2-4b7f-b631-e1a4ead3cdc8");
  static final UUID COMPOSITE_INVOICE_VOUCHER_LINE_FUND_ORG_ID = UUID.fromString(
    "8ddd1e32-5c85-46ab-8bf3-1ec9a76c18cf"
  );
  private static final Set<UUID> ENTITY_TYPES = Set.of(
    COMPOSITE_VOUCHER_LINE_FUND_ID,
    COMPOSITE_INVOICE_VOUCHER_LINE_FUND_ORG_ID
  );

  static final String FUND_DISTRIBUTION = "voucher_line.fund_distribution";
  private static final String FUND_DISTRIBUTION_PROPERTY = FUND_DISTRIBUTION + "[*]->";

  static final String LOCATIONS = "fund.locations";
  static final String LOCATIONS_ALTERNATIVE = FUND_DISTRIBUTION_PROPERTY + "fund_locations";

  /** Old field -> new fund_distribution property name */
  private static final Map<String, String> PROPERTY_MAP = Map.ofEntries(
    Map.entry("fund.id", "fund_id"),
    Map.entry("fund.code", "code"),
    Map.entry("fund.name", "fund_name"),
    Map.entry("fund.description", "fund_description"),
    Map.entry("fund.external_account_no", "fund_external_account_no"),
    Map.entry("fund.fund_status", "fund_status"),
    Map.entry("fund.restrict_by_locations", "fund_restrict_by_locations"),
    Map.entry("fund.tag_list", "fund_tags"),
    Map.entry("fund.acq_unit_ids", "fund_acq_unit_ids"),
    Map.entry("fund.acquisition_unit", "fund_acquisition_units"),
    Map.entry("fund.allocated_from_ids", "fund_allocated_from_ids"),
    Map.entry("fund.allocated_from", "fund_allocated_from"),
    Map.entry("fund.allocated_to_ids", "fund_allocated_to_ids"),
    Map.entry("fund.allocated_to", "fund_allocated_to"),
    Map.entry("fund.donor_organization_ids", "fund_donor_organization_ids"),
    Map.entry("fund.donor_organizations", "fund_donor_organizations"),
    Map.entry("fund.fund_type_id", "fund_type_id"),
    Map.entry("fund.ledger_id", "ledger_id"),
    Map.entry("fund_type.id", "fund_type_id"),
    Map.entry("fund_type.name", "fund_type"),
    Map.entry("ledger.id", "ledger_id"),
    Map.entry("ledger.code", "ledger_code"),
    Map.entry("ledger.name", "ledger_name"),
    Map.entry("ledger.ledger_status", "ledger_status"),
    Map.entry("ledger.currency", "ledger_currency"),
    Map.entry("ledger.restrict_encumbrance", "ledger_restrict_encumbrance"),
    Map.entry("ledger.restrict_expenditures", "ledger_restrict_expenditures"),
    Map.entry("ledger.acq_unit_ids", "ledger_acq_unit_ids"),
    Map.entry("ledger.acquisition_unit", "ledger_acquisition_units")
  );

  /** Fields intentionally not carried over to the sub-table */
  static final Set<String> REMOVED_FIELDS = Set.of(
    "fund.version",
    "fund.created_date",
    "fund.updated_date",
    "fund.created_by_user_id",
    "fund.updated_by_user_id",
    "fund_type.version",
    "ledger.version",
    "ledger.description",
    "ledger.fiscal_year_one_id",
    "ledger.fiscal_year_one",
    "ledger.created_date",
    "ledger.updated_date",
    "ledger.created_by_user_id",
    "ledger.updated_by_user_id"
  );

  @Override
  public String getMaximumApplicableVersion() {
    return "24";
  }

  @Override
  public String getLabel() {
    return "V24 -> V25 Voucher line fund data moved to fund distribution sub-table (MODFQMMGR-1221)";
  }

  @Override
  public SingleFieldMigrationResult<MigratableFqlFieldAndCondition> migrateFql(
    Void v,
    MigratableFqlFieldAndCondition condition
  ) {
    if (!ENTITY_TYPES.contains(condition.entityTypeId())) {
      return SingleFieldMigrationResult.noop(condition);
    }

    String field = condition.field();
    String fql = condition.getConditionObject().toPrettyString();

    if (REMOVED_FIELDS.contains(field)) {
      return removed(RemovedFieldWarning.withoutAlternative().apply(condition.fieldPrefix(), field, fql));
    }
    if (LOCATIONS.equals(field)) {
      // was never queryable, and the structure changed, so there is nothing meaningful to migrate the condition to
      return removed(
        RemovedFieldWarning.withAlternative(LOCATIONS_ALTERNATIVE).apply(condition.fieldPrefix(), field, fql)
      );
    }

    String property = PROPERTY_MAP.get(field);
    if (property == null) {
      return SingleFieldMigrationResult.noop(condition);
    }
    return SingleFieldMigrationResult.withField(condition.withField(FUND_DISTRIBUTION_PROPERTY + property));
  }

  @Override
  public SingleFieldMigrationResult<MigratableFqlFieldOnly> migrateFieldName(Void v, MigratableFqlFieldOnly field) {
    if (!ENTITY_TYPES.contains(field.entityTypeId())) {
      return SingleFieldMigrationResult.noop(field);
    }

    String name = field.field();

    if (REMOVED_FIELDS.contains(name)) {
      return removed(RemovedFieldWarning.withoutAlternative().apply(field.fieldPrefix(), name, null));
    }

    // field lists can only contain top-level columns, so everything goes to the sub-table column itself
    // (duplicates are collapsed by MigrationUtils)
    SingleFieldMigrationResult<MigratableFqlFieldOnly> result = SingleFieldMigrationResult.withField(
      field.withField(FUND_DISTRIBUTION)
    );

    if (LOCATIONS.equals(name)) {
      return result.withWarnings(
        List.of(RemovedFieldWarning.withAlternative(LOCATIONS_ALTERNATIVE).apply(field.fieldPrefix(), name, null))
      );
    }
    if (PROPERTY_MAP.containsKey(name)) {
      return result;
    }
    return SingleFieldMigrationResult.noop(field);
  }

  private static <T extends org.folio.fqm.migration.types.MigratableFqlField<T>> SingleFieldMigrationResult<T> removed(
    FieldWarning warning
  ) {
    return SingleFieldMigrationResult.<T>removed().withWarnings(List.of(warning)).withHadBreakingChange(true);
  }
}
