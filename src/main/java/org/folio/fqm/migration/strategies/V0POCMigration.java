package org.folio.fqm.migration.strategies;

import java.util.Map;
import java.util.UUID;
import org.folio.fqm.migration.AbstractSimpleMigrationStrategy;

/**
 * Version 0 -> 1, original proof-of-concept changes (creation of complex entity types, etc).
 * Implemented July 2024
 */
public class V0POCMigration extends AbstractSimpleMigrationStrategy {

  private static final UUID OLD_DRV_LOAN_DETAILS = UUID.fromString("4e09d89a-44ed-418e-a9cc-820dfb27bf3a");
  private static final UUID NEW_COMPOSITE_LOAN_DETAILS = UUID.fromString("d6729885-f2fb-4dc7-b7d0-a865a7f461e4");
  private static final Map<String, String> DRV_LOAN_DETAILS_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("holdings_id", "holdings.id"),
    Map.entry("instance_id", "instance.id"),
    // iffy on this. old one gets the first one of these, whereas this is array/object
    Map.entry("instance_primary_contributor", "instance.contributors"),
    Map.entry("instance_title", "instance.title"),
    Map.entry("item_barcode", "items.barcode"),
    Map.entry("item_call_number", "items.effective_call_number"),
    Map.entry("item_id", "items.id"),
    Map.entry("item_material_type", "mtypes.name"),
    Map.entry("item_material_type_id", "mtypes.id"),
    Map.entry("item_status", "items.status_name"),
    Map.entry("loan_checkin_servicepoint_id", "cispi.id"),
    Map.entry("loan_checkin_servicepoint_name", "cispi.name"),
    Map.entry("loan_checkout_servicepoint_id", "cospi.id"),
    Map.entry("loan_checkout_servicepoint_name", "cospi.name"),
    Map.entry("loan_checkout_date", "loans.loan_date"),
    Map.entry("id", "loans.id"),
    Map.entry("loan_policy_id", "lpolicy.id"),
    Map.entry("loan_policy_name", "lpolicy.name"),
    Map.entry("loan_return_date", "loans.return_date"),
    Map.entry("loan_status", "loans.status_name"),
    Map.entry("user_active", "users.active"),
    Map.entry("user_barcode", "users.barcode"),
    Map.entry("user_expiration_date", "users.expiration_date"),
    Map.entry("user_first_name", "users.first_name"),
    Map.entry("user_full_name", "users.last_name_first_name"),
    Map.entry("user_id", "users.id"),
    Map.entry("loan_due_date", "loans.due_date"),
    Map.entry("user_last_name", "users.last_name"),
    Map.entry("user_patron_group", "groups.group"),
    Map.entry("user_patron_group_id", "groups.id")
  );

  private static final UUID OLD_SRC_CIRCULATION_LOAN_POLICY = UUID.fromString("5e7de445-bcc6-4008-8032-8d9602b854d7");
  private static final UUID NEW_SIMPLE_LOAN_POLICY = UUID.fromString("64d7b5fb-2ead-444c-a9bd-b9db831f4132");
  private static final Map<String, String> SRC_CIRCULATION_LOAN_POLICY_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("id", "id"),
    Map.entry("policy_name", "name")
  );

  @Override
  public String getLabel() {
    return "V0 -> V1 POC Migration";
  }

  @Override
  public String getSourceVersion() {
    return "0";
  }

  @Override
  public String getTargetVersion() {
    return "1";
  }

  @Override
  protected Map<UUID, UUID> getEntityTypeChanges() {
    return Map.ofEntries(
      Map.entry(OLD_DRV_LOAN_DETAILS, NEW_COMPOSITE_LOAN_DETAILS),
      Map.entry(OLD_SRC_CIRCULATION_LOAN_POLICY, NEW_SIMPLE_LOAN_POLICY)
    );
  }

  @Override
  protected Map<UUID, Map<String, String>> getFieldChanges() {
    return Map.ofEntries(
      Map.entry(OLD_DRV_LOAN_DETAILS, DRV_LOAN_DETAILS_COLUMN_MAPPING),
      Map.entry(OLD_SRC_CIRCULATION_LOAN_POLICY, SRC_CIRCULATION_LOAN_POLICY_COLUMN_MAPPING)
    );
  }
}
