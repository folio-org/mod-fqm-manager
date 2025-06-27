package org.folio.fqm.migration.strategies;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.folio.fqm.migration.AbstractSimpleMigrationStrategy;
import org.folio.fqm.migration.warnings.EntityTypeWarning;
import org.folio.fqm.migration.warnings.FieldWarning;
import org.folio.fqm.migration.warnings.QueryBreakingWarning;
import org.folio.fqm.migration.warnings.RemovedEntityWarning;

/**
 * Version 0 -> 1, original proof-of-concept changes (creation of complex entity types, etc).
 * Implemented July 2024 based on changes in commit 9a1e180186dc7178b0c3416f1aec088c9ce39a54.
 */
@SuppressWarnings("java:S1192") // allow constant string duplication
public class V0POCMigration extends AbstractSimpleMigrationStrategy {

  public static final UUID OLD_DRV_LOAN_DETAILS = UUID.fromString("4e09d89a-44ed-418e-a9cc-820dfb27bf3a");
  public static final UUID NEW_COMPOSITE_LOAN_DETAILS = UUID.fromString("d6729885-f2fb-4dc7-b7d0-a865a7f461e4");
  public static final Map<String, String> DRV_LOAN_DETAILS_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("holdings_id", "holdings.id"),
    Map.entry("instance_id", "instance.id"),
    Map.entry("instance_primary_contributor", "instance.contributors"),
    Map.entry("instance_title", "instance.title"),
    Map.entry("item_barcode", "items.barcode"),
    Map.entry("item_call_number", "items.effective_call_number"),
    Map.entry("item_id", "items.id"),
    Map.entry("item_material_type", "mtypes.name"),
    Map.entry("item_status", "items.status_name"),
    Map.entry("loan_checkin_servicepoint_name", "cispi.name"),
    Map.entry("loan_checkout_servicepoint_name", "cospi.name"),
    Map.entry("loan_checkout_date", "loans.checkout_date"),
    Map.entry("id", "loans.id"),
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
    // this column did not exist in the actual entity type, but it is (incorrectly) referenced by a canned list; we should fix that:
    Map.entry("item_holdingsrecord_id", "items.hrid"),
    // not query-breaking removals; this is to reflect a Poppy change in how ID columns are queried
    // we've hidden most of the fields, so our intended behavior now is to use the non-ID column in
    // the field list. Users can re-add the ID column if they want it, provided it is still available.
    Map.entry("loan_checkin_servicepoint_id", "cispi.name"),
    Map.entry("loan_checkout_servicepoint_id", "cospi.name"),
    Map.entry("loan_policy_id", "lpolicy.name"),
    Map.entry("item_material_type_id", "mtypes.name"),
    Map.entry("user_patron_group_id", "groups.group")
  );

  public static final UUID OLD_SRC_CIRCULATION_LOAN_POLICY = UUID.fromString("5e7de445-bcc6-4008-8032-8d9602b854d7");
  public static final UUID NEW_SIMPLE_LOAN_POLICY = UUID.fromString("64d7b5fb-2ead-444c-a9bd-b9db831f4132");
  public static final Map<String, String> SRC_CIRCULATION_LOAN_POLICY_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("policy_name", "name")
  );

  // drv_holdings_record_details was renamed to composite_holdings_record; same ID
  public static final UUID DRV_HOLDINGS_RECORD_DETAILS = UUID.fromString("8418e512-feac-4a6a-a56d-9006aab31e33");
  public static final Map<String, String> DRV_HOLDINGS_RECORD_DETAILS_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("holdings_effective_location", "effective_location.name"),
    Map.entry("holdings_effective_location_id", "effective_location.id"),
    Map.entry("holdings_effective_library_code", "effective_library.code"),
    Map.entry("holdings_effective_library_name", "effective_library.name"),
    Map.entry("holdings_effective_library_id", "effective_library.id"),
    Map.entry("holdings_hrid", "holdings.hrid"),
    Map.entry("id", "holdings.id"),
    Map.entry("holdings_permanent_location", "permanent_location.name"),
    Map.entry("holdings_permanent_location_id", "permanent_location.id"),
    Map.entry("holdings_statistical_code_ids", "holdings.statistical_code_ids"),
    Map.entry("holdings_statistical_codes", "holdings.statistical_code_names"),
    Map.entry("holdings_suppress_from_discovery", "holdings.discovery_suppress"),
    Map.entry("holdings_temporary_location", "temporary_location.name"),
    Map.entry("holdings_temporary_location_id", "temporary_location.id"),
    Map.entry("instance_id", "holdings.instance_id")
  );

  // drv_instances was renamed to composite_instances; same ID
  public static final UUID DRV_INSTANCES = UUID.fromString("6b08439b-4f8e-4468-8046-ea620f5cfb74");
  public static final Map<String, String> DRV_INSTANCES_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("instance_cataloged_date", "instance.cataloged_date"),
    Map.entry("instance_metadata_created_date", "instance.created_at"),
    Map.entry("instance_hrid", "instance.hrid"),
    Map.entry("id", "instance.id"),
    Map.entry("instance_title", "instance.title"),
    Map.entry("instance_discovery_suppress", "instance.discovery_suppress"),
    Map.entry("instance_metadata_updated_date", "instance.updated_at"),
    Map.entry("instance_statistical_code_ids", "instance.statistical_code_ids"),
    Map.entry("instance_statistical_codes", "instance.statistical_code_names"),
    Map.entry("instance_status_id", "instance.status_id"),
    Map.entry("instance_status", "inst_stat.name"),
    Map.entry("mode_of_issuance_id", "mode_of_issuance.id"),
    Map.entry("mode_of_issuance", "mode_of_issuance.name"),
    Map.entry("instance_source", "instance.source"),
    Map.entry("instance_contributor_type_ids", "instance.contributors[*]->contributor_type_id"),
    Map.entry("instance_contributor_type", "instance.contributors[*]->contributor_type"),
    Map.entry("instance_contributor_type_name_ids", "instance.contributors[*]->contributor_name_type_id"),
    Map.entry("instance_contributor_name_type", "instance.contributors[*]->name"),
    Map.entry("instance_language", "instance.languages")
  );

  public static final UUID OLD_DRV_ITEM_DETAILS = UUID.fromString("0cb79a4c-f7eb-4941-a104-745224ae0292");
  public static final UUID NEW_COMPOSITE_ITEM_DETAILS = UUID.fromString("d0213d22-32cf-490f-9196-d81c3c66e53f");
  public static final Map<String, String> DRV_ITEM_DETAILS_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("holdings_id", "holdings.id"),
    Map.entry("id", "items.id"),
    Map.entry("instance_created_date", "instances.created_at"),
    Map.entry("instance_id", "instances.id"),
    Map.entry("instance_primary_contributor", "instances.contributors"),
    Map.entry("instance_title", "instances.title"),
    Map.entry("instance_updated_date", "instances.updated_at"),
    Map.entry("item_barcode", "items.barcode"),
    Map.entry("item_copy_number", "items.copy_number"),
    Map.entry("item_created_date", "items.created_date"),
    Map.entry("item_effective_call_number", "items.effective_call_number"),
    Map.entry("item_effective_call_number_type_name", "effective_call_number.name"),
    Map.entry("item_effective_library_code", "loclibrary.code"),
    Map.entry("item_effective_library_name", "loclibrary.name"),
    Map.entry("item_effective_location_name", "effective_location.name"),
    Map.entry("item_hrid", "items.hrid"),
    Map.entry("item_level_call_number", "items.item_level_call_number"),
    Map.entry("item_level_call_number_type_name", "item_level_call_number.name"),
    Map.entry("item_material_type", "mtypes.name"),
    Map.entry("item_permanent_location_name", "permanent_location.name"),
    Map.entry("item_statistical_code_ids", "items.statistical_code_ids"),
    Map.entry("item_statistical_codes", "items.statistical_code_names"),
    Map.entry("item_status", "items.status_name"),
    Map.entry("item_temporary_location_name", "temporary_location.name"),
    Map.entry("item_updated_date", "items.updated_date"),
    // this column did not exist in the actual entity type, but it is (incorrectly) referenced by a canned list; we should fix that:
    Map.entry("item_holdings_record_id", "items.hrid"),
    // not query-breaking removals; this is to reflect a Poppy change in how ID columns are queried
    // we've hidden most of the fields, so our intended behavior now is to use the non-ID column in
    // the field list. Users can re-add the ID column if they want it, provided it is still available.
    Map.entry("item_effective_call_number_typeid", "effective_call_number.name"),
    Map.entry("item_effective_library_id", "loclibrary.name"),
    Map.entry("item_effective_location_id", "effective_location.name"),
    Map.entry("item_level_call_number_typeid", "item_level_call_number.name"),
    Map.entry("item_material_type_id", "mtypes.name"),
    Map.entry("item_permanent_location_id", "permanent_location.name"),
    Map.entry("item_temporary_location_id", "temporary_location.name")
  );

  public static final UUID OLD_SRC_INVENTORY_CALL_NUMBER_TYPE = UUID.fromString("5c8315be-13f5-4df5-ae8b-086bae83484d");
  public static final UUID NEW_SIMPLE_CALL_NUMBER_TYPE = UUID.fromString("d9338ced-3e71-4f24-b605-7912d590f005");
  public static final Map<String, String> SRC_INVENTORY_CALL_NUMBER_TYPE_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("call_number_type_name", "name")
  );

  public static final UUID OLD_SRC_INVENTORY_CONTRIBUTOR_NAME_TYPE = UUID.fromString(
    "9c24a719-679b-4cca-9146-42a46d721df5"
  );
  public static final UUID NEW_DRV_CONTRIBUTOR_NAME_TYPE_DETAILS = UUID.fromString(
    "6cd60e0e-f862-413a-9857-1d1ef1ca34ea"
  );
  public static final Map<String, String> SRC_INVENTORY_CONTRIBUTOR_NAME_TYPE_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("contributor_name_type", "name")
  );

  public static final UUID OLD_SRC_INVENTORY_CONTRIBUTOR_TYPE = UUID.fromString("3553ca38-d522-439b-9f91-1512275a43b9");
  public static final UUID NEW_DRV_CONTRIBUTOR_TYPE_DETAILS = UUID.fromString("a09e4959-3a6f-4fc6-a8a8-16bb9d30dba2");
  public static final Map<String, String> SRC_INVENTORY_CONTRIBUTOR_TYPE_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("contributor_type", "name")
  );

  public static final UUID OLD_SRC_INVENTORY_INSTANCE_STATUS = UUID.fromString("bc03686c-657e-4f74-9d89-91eac5ea86a4");
  public static final UUID NEW_SIMPLE_INSTANCE_STATUS = UUID.fromString("9c239bfd-198f-4013-bbc4-4551c0cbdeaa");
  public static final Map<String, String> SRC_INVENTORY_INSTANCE_STATUS_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("status", "name")
  );

  public static final UUID OLD_SRC_INVENTORY_LOCATION = UUID.fromString("a9d6305e-fdb4-4fc4-8a73-4a5f76d8410b");
  public static final UUID NEW_SIMPLE_LOCATIONS = UUID.fromString("74ddf1a6-19e0-4d63-baf0-cd2da9a46ca4");
  public static final Map<String, String> SRC_INVENTORY_LOCATION_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("location_code", "code"),
    Map.entry("location_name", "name")
  );

  public static final UUID OLD_SRC_INVENTORY_LOCLIBRARY = UUID.fromString("cf9f5c11-e943-483c-913b-81d1e338accc");
  public static final UUID NEW_SIMPLE_LOCLIBRARY = UUID.fromString("32f58888-1a7b-4840-98f8-cc69ca93fc67");
  public static final Map<String, String> SRC_INVENTORY_LOCLIBRARY_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("loclibrary_code", "code"),
    Map.entry("loclibrary_name", "name")
  );

  public static final UUID OLD_SRC_INVENTORY_MATERIAL_TYPE = UUID.fromString("917ea5c8-cafe-4fa6-a942-e2388a88c6f6");
  public static final UUID NEW_SIMPLE_MATERIAL_TYPE_DETAILS = UUID.fromString("8b1f51d6-8795-4113-a72e-3b7dc6cc6dfe");
  public static final Map<String, String> SRC_INVENTORY_MATERIAL_TYPE_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("material_type_name", "name")
  );

  public static final UUID OLD_SRC_INVENTORY_MODE_OF_ISSUANCE = UUID.fromString("60e315d6-db28-4077-9277-b946411fe7d9");
  public static final UUID NEW_SIMPLE_MODE_OF_ISSUANCE = UUID.fromString("073b554a-5b5c-4552-a51c-01448a1643b0");
  public static final Map<String, String> SRC_INVENTORY_MODE_OF_ISSUANCE_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("mode_of_issuance", "name")
  );

  public static final UUID OLD_SRC_INVENTORY_SERVICE_POINT = UUID.fromString("89cdeac4-9582-4388-800b-9ccffd8d7691");
  public static final UUID NEW_SIMPLE_SERVICE_POINT_DETAIL = UUID.fromString("1fdcc2e8-1ff8-4a99-b4ad-7d6bf564aec5");
  public static final Map<String, String> SRC_INVENTORY_SERVICE_POINT_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("service_point_code", "code"),
    Map.entry("service_point_name", "name")
  );

  public static final UUID OLD_DRV_ORGANIZATION_CONTACTS = UUID.fromString("7a7860cd-e939-504f-b51f-ed3e1e6b12b9");
  public static final UUID NEW_SIMPLE_ORGANIZATION = UUID.fromString("b5ffa2e9-8080-471a-8003-a8c5a1274503");
  public static final Map<String, String> DRV_ORGANIZATION_CONTACTS_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("acquisition_unit_id", "acq_unit_ids"),
    Map.entry("acquisition_unit_name", "acq_unit_names"),
    Map.entry("address", "addresses"),
    Map.entry("alias", "aliases"),
    Map.entry("email", "emails"),
    Map.entry("last_updated", "updated_at"),
    Map.entry("organization_status", "status"),
    Map.entry("organization_type_ids", "type_ids"),
    Map.entry("organization_type_name", "type_names"),
    Map.entry("phone_number", "phone_numbers"),
    Map.entry("url", "urls")
  );

  public static final UUID OLD_DRV_ORGANIZATION_DETAILS = UUID.fromString("837f262e-2073-4a00-8bcc-4e4ce6e669b3");
  public static final Map<String, String> DRV_ORGANIZATION_DETAILS_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("acquisition_unit", "acq_unit_names"),
    Map.entry("acqunit_ids", "acq_unit_ids"),
    Map.entry("alias", "aliases"),
    Map.entry("last_updated", "updated_at"),
    Map.entry("organization_status", "status"),
    Map.entry("organization_type_ids", "type_ids"),
    Map.entry("organization_type_name", "type_names")
  );

  public static final UUID OLD_SRC_ORGANIZATION_TYPES = UUID.fromString("6b335e41-2654-4e2a-9b4e-c6930b330ccc");
  public static final UUID NEW_SIMPLE_ORGANIZATION_TYPES = UUID.fromString("85a2b008-af8d-4890-9490-421cabcb7bad");
  public static final Map<String, String> SRC_ORGANIZATION_TYPES_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("organization_types_name", "name")
  );

  public static final UUID OLD_SRC_ORGANIZATIONS = UUID.fromString("489234a9-8703-48cd-85e3-7f84011bafa3");
  public static final Map<String, String> SRC_ORGANIZATIONS_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("vendor_code", "code"),
    Map.entry("vendor_name", "name")
  );

  public static final UUID OLD_DRV_PURCHASE_ORDER_LINE_DETAILS = UUID.fromString(
    "90403847-8c47-4f58-b117-9a807b052808"
  );
  public static final UUID NEW_COMPOSITE_PURCHASE_ORDER_LINES = UUID.fromString("abc777d3-2a45-43e6-82cb-71e8c96d13d2");
  public static final Map<String, String> DRV_PURCHASE_ORDER_LINE_DETAILS_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("acquisition_unit", "po.acquisition_unit"),
    Map.entry("acqunit_ids", "po.acq_unit_ids"),
    Map.entry("fund_distribution", "pol.fund_distribution"),
    Map.entry("id", "pol.id"),
    Map.entry("po_approved", "po.approved"),
    Map.entry("po_assigned_to", "assigned_to_user.last_name_first_name"),
    Map.entry("po_assigned_to_id", "assigned_to_user.id"),
    Map.entry("po_created_by", "po_created_by_user.last_name_first_name"),
    Map.entry("po_created_by_id", "po_created_by_user.id"),
    Map.entry("po_created_date", "po.created_at"),
    Map.entry("po_id", "po.id"),
    Map.entry("po_notes", "po.notes"),
    Map.entry("po_number", "po.po_number"),
    Map.entry("po_type", "po.order_type"),
    Map.entry("po_updated_by", "po_updated_by_user.last_name_first_name"),
    Map.entry("po_updated_by_id", "po_updated_by_user.id"),
    Map.entry("po_updated_date", "po.updated_at"),
    Map.entry("po_workflow_status", "po.workflow_status"),
    Map.entry("pol_created_by", "pol_created_by_user.last_name_first_name"),
    Map.entry("pol_created_by_id", "pol_created_by_user.id"),
    Map.entry("pol_created_date", "pol.created_at"),
    Map.entry("pol_currency", "pol.cost_currency"),
    Map.entry("pol_description", "pol.description"),
    Map.entry("pol_estimated_price", "pol.cost_po_line_estimated_price"),
    Map.entry("pol_exchange_rate", "pol.cost_exchange_rate"),
    Map.entry("pol_number", "pol.po_line_number"),
    Map.entry("pol_payment_status", "pol.payment_status"),
    Map.entry("pol_receipt_status", "pol.receipt_status"),
    Map.entry("pol_updated_by", "pol_updated_by_user.last_name_first_name"),
    Map.entry("pol_updated_by_id", "pol_updated_by_user.id"),
    Map.entry("pol_updated_date", "pol.updated_at"),
    Map.entry("vendor_code", "vendor_organization.code"),
    Map.entry("vendor_id", "vendor_organization.id"),
    Map.entry("vendor_name", "vendor_organization.name")
  );

  public static final UUID OLD_DRV_USER_DETAILS = UUID.fromString("0069cf6f-2833-46db-8a51-8934769b8289");
  public static final UUID NEW_COMPOSITE_USER_DETAILS = UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf");
  public static final Map<String, String> DRV_USER_DETAILS_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("id", "users.id"),
    Map.entry("user_active", "users.active"),
    Map.entry("user_address_ids", "users.addresses[*]->address_id"),
    Map.entry("user_address_line1", "users.addresses[*]->address_line1"),
    Map.entry("user_address_line2", "users.addresses[*]->address_line2"),
    Map.entry("user_barcode", "users.barcode"),
    Map.entry("user_cities", "users.addresses[*]->city"),
    Map.entry("user_country_ids", "users.addresses[*]->country_id"),
    Map.entry("user_created_date", "users.created_date"),
    Map.entry("user_date_of_birth", "users.date_of_birth"),
    Map.entry("user_department_ids", "users.department_ids"),
    Map.entry("user_department_names", "users.departments"),
    Map.entry("user_email", "users.email"),
    Map.entry("user_enrollment_date", "users.enrollment_date"),
    Map.entry("user_expiration_date", "users.expiration_date"),
    Map.entry("user_external_system_id", "users.external_system_id"),
    Map.entry("user_first_name", "users.first_name"),
    Map.entry("user_last_name", "users.last_name"),
    Map.entry("user_middle_name", "users.middle_name"),
    Map.entry("user_mobile_phone", "users.mobile_phone"),
    Map.entry("user_patron_group", "groups.group"),
    Map.entry("user_phone", "users.phone"),
    Map.entry("user_postal_codes", "users.addresses[*]->postal_code"),
    Map.entry("user_preferred_contact_type", "users.preferred_contact_type"),
    Map.entry("user_preferred_first_name", "users.preferred_first_name"),
    Map.entry("user_primary_address", "users.addresses"),
    Map.entry("user_regions", "users.addresses[*]->region"),
    Map.entry("user_updated_date", "users.updated_date"),
    Map.entry("username", "users.username"),
    // not query-breaking removals; this is to reflect a Poppy change in how ID columns are queried
    // we've hidden most of the fields, so our intended behavior now is to use the non-ID column in
    // the field list. Users can re-add the ID column if they want it, provided it is still available.
    Map.entry("user_patron_group_id", "groups.group")
  );

  public static final UUID OLD_SRC_USERS_ADDRESSTYPE = UUID.fromString("e627a89b-682b-41fe-b532-f4262020a451");
  public static final UUID NEW_SIMPLE_ADDRESS_TYPES = UUID.fromString("9176c676-0485-4f6c-b1fc-585355bac679");
  public static final Map<String, String> SRC_USERS_ADDRESSTYPE_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("addressType", "type")
  );

  public static final UUID OLD_SRC_USERS_DEPARTMENTS = UUID.fromString("c8364551-7e51-475d-8473-88951181452d");
  public static final UUID NEW_SIMPLE_DEPARTMENT_DETAILS = UUID.fromString("f067beda-cbeb-4423-9a0d-3b59fb329ce2");
  public static final Map<String, String> SRC_USERS_DEPARTMENTS_COLUMN_MAPPING = Map.ofEntries(
    Map.entry("department", "name")
  );

  // no column changes
  public static final UUID OLD_SRC_USERS_GROUPS = UUID.fromString("e611264d-377e-4d87-a93f-f1ca327d3db0");
  public static final UUID NEW_SIMPLE_GROUP_DETAILS = UUID.fromString("e7717b38-4ff3-4fb9-ae09-b3d0c8400710");

  @Override
  public String getLabel() {
    return "V0 -> V1 POC Migration";
  }

  @Override
  public String getMaximumApplicableVersion() {
    return "0";
  }

  @Override
  public Map<UUID, UUID> getEntityTypeChanges() {
    return Map.ofEntries(
      Map.entry(OLD_DRV_LOAN_DETAILS, NEW_COMPOSITE_LOAN_DETAILS),
      Map.entry(OLD_SRC_CIRCULATION_LOAN_POLICY, NEW_SIMPLE_LOAN_POLICY),
      Map.entry(OLD_DRV_ITEM_DETAILS, NEW_COMPOSITE_ITEM_DETAILS),
      Map.entry(OLD_SRC_INVENTORY_CALL_NUMBER_TYPE, NEW_SIMPLE_CALL_NUMBER_TYPE),
      Map.entry(OLD_SRC_INVENTORY_CONTRIBUTOR_NAME_TYPE, NEW_DRV_CONTRIBUTOR_NAME_TYPE_DETAILS),
      Map.entry(OLD_SRC_INVENTORY_CONTRIBUTOR_TYPE, NEW_DRV_CONTRIBUTOR_TYPE_DETAILS),
      Map.entry(OLD_SRC_INVENTORY_INSTANCE_STATUS, NEW_SIMPLE_INSTANCE_STATUS),
      Map.entry(OLD_SRC_INVENTORY_LOCATION, NEW_SIMPLE_LOCATIONS),
      Map.entry(OLD_SRC_INVENTORY_LOCLIBRARY, NEW_SIMPLE_LOCLIBRARY),
      Map.entry(OLD_SRC_INVENTORY_MATERIAL_TYPE, NEW_SIMPLE_MATERIAL_TYPE_DETAILS),
      Map.entry(OLD_SRC_INVENTORY_MODE_OF_ISSUANCE, NEW_SIMPLE_MODE_OF_ISSUANCE),
      Map.entry(OLD_SRC_INVENTORY_SERVICE_POINT, NEW_SIMPLE_SERVICE_POINT_DETAIL),
      Map.entry(OLD_DRV_ORGANIZATION_CONTACTS, NEW_SIMPLE_ORGANIZATION),
      Map.entry(OLD_DRV_ORGANIZATION_DETAILS, NEW_SIMPLE_ORGANIZATION),
      Map.entry(OLD_SRC_ORGANIZATION_TYPES, NEW_SIMPLE_ORGANIZATION_TYPES),
      Map.entry(OLD_SRC_ORGANIZATIONS, NEW_SIMPLE_ORGANIZATION),
      Map.entry(OLD_DRV_PURCHASE_ORDER_LINE_DETAILS, NEW_COMPOSITE_PURCHASE_ORDER_LINES),
      Map.entry(OLD_DRV_USER_DETAILS, NEW_COMPOSITE_USER_DETAILS),
      Map.entry(OLD_SRC_USERS_ADDRESSTYPE, NEW_SIMPLE_ADDRESS_TYPES),
      Map.entry(OLD_SRC_USERS_DEPARTMENTS, NEW_SIMPLE_DEPARTMENT_DETAILS),
      Map.entry(OLD_SRC_USERS_GROUPS, NEW_SIMPLE_GROUP_DETAILS)
    );
  }

  @Override
  public Map<UUID, Map<String, String>> getFieldChanges() {
    return Map.ofEntries(
      Map.entry(OLD_DRV_LOAN_DETAILS, DRV_LOAN_DETAILS_COLUMN_MAPPING),
      Map.entry(OLD_SRC_CIRCULATION_LOAN_POLICY, SRC_CIRCULATION_LOAN_POLICY_COLUMN_MAPPING),
      Map.entry(DRV_HOLDINGS_RECORD_DETAILS, DRV_HOLDINGS_RECORD_DETAILS_COLUMN_MAPPING),
      Map.entry(DRV_INSTANCES, DRV_INSTANCES_COLUMN_MAPPING),
      Map.entry(OLD_DRV_ITEM_DETAILS, DRV_ITEM_DETAILS_COLUMN_MAPPING),
      Map.entry(OLD_SRC_INVENTORY_CALL_NUMBER_TYPE, SRC_INVENTORY_CALL_NUMBER_TYPE_COLUMN_MAPPING),
      Map.entry(OLD_SRC_INVENTORY_CONTRIBUTOR_NAME_TYPE, SRC_INVENTORY_CONTRIBUTOR_NAME_TYPE_COLUMN_MAPPING),
      Map.entry(OLD_SRC_INVENTORY_CONTRIBUTOR_TYPE, SRC_INVENTORY_CONTRIBUTOR_TYPE_COLUMN_MAPPING),
      Map.entry(OLD_SRC_INVENTORY_INSTANCE_STATUS, SRC_INVENTORY_INSTANCE_STATUS_COLUMN_MAPPING),
      Map.entry(OLD_SRC_INVENTORY_LOCATION, SRC_INVENTORY_LOCATION_COLUMN_MAPPING),
      Map.entry(OLD_SRC_INVENTORY_LOCLIBRARY, SRC_INVENTORY_LOCLIBRARY_COLUMN_MAPPING),
      Map.entry(OLD_SRC_INVENTORY_MATERIAL_TYPE, SRC_INVENTORY_MATERIAL_TYPE_COLUMN_MAPPING),
      Map.entry(OLD_SRC_INVENTORY_MODE_OF_ISSUANCE, SRC_INVENTORY_MODE_OF_ISSUANCE_COLUMN_MAPPING),
      Map.entry(OLD_SRC_INVENTORY_SERVICE_POINT, SRC_INVENTORY_SERVICE_POINT_COLUMN_MAPPING),
      Map.entry(OLD_DRV_ORGANIZATION_CONTACTS, DRV_ORGANIZATION_CONTACTS_COLUMN_MAPPING),
      Map.entry(OLD_DRV_ORGANIZATION_DETAILS, DRV_ORGANIZATION_DETAILS_COLUMN_MAPPING),
      Map.entry(OLD_SRC_ORGANIZATION_TYPES, SRC_ORGANIZATION_TYPES_COLUMN_MAPPING),
      Map.entry(OLD_SRC_ORGANIZATIONS, SRC_ORGANIZATIONS_COLUMN_MAPPING),
      Map.entry(OLD_DRV_PURCHASE_ORDER_LINE_DETAILS, DRV_PURCHASE_ORDER_LINE_DETAILS_COLUMN_MAPPING),
      Map.entry(OLD_DRV_USER_DETAILS, DRV_USER_DETAILS_COLUMN_MAPPING),
      Map.entry(OLD_SRC_USERS_ADDRESSTYPE, SRC_USERS_ADDRESSTYPE_COLUMN_MAPPING),
      Map.entry(OLD_SRC_USERS_DEPARTMENTS, SRC_USERS_DEPARTMENTS_COLUMN_MAPPING)
    );
  }

  @Override
  public Map<UUID, Function<String, EntityTypeWarning>> getEntityTypeWarnings() {
    return Map.ofEntries(
      Map.entry(
        UUID.fromString("146dfba5-cdc9-45f5-a8a1-3fdc454c9ae2"),
        RemovedEntityWarning.withAlternative("drv_loan_status", "simple_loans")
      ),
      Map.entry(
        UUID.fromString("097a6f96-edd0-11ed-a05b-0242ac120003"),
        RemovedEntityWarning.withoutAlternative("drv_item_callnumber_location")
      ),
      Map.entry(
        UUID.fromString("0cb79a4c-f7eb-4941-a104-745224ae0293"),
        RemovedEntityWarning.withAlternative("drv_item_holdingsrecord_instance", "composite_item_details")
      ),
      Map.entry(
        UUID.fromString("a1a37288-1afe-4fa5-ab59-a5bcf5d8ca2d"),
        RemovedEntityWarning.withoutAlternative("drv_item_status")
      ),
      Map.entry(
        UUID.fromString("5fefec2a-9d6c-474c-8698-b0ea77186c12"),
        RemovedEntityWarning.withoutAlternative("drv_pol_receipt_status")
      ),
      Map.entry(
        UUID.fromString("2168014f-9316-4760-9d82-d0306d5f59e4"),
        RemovedEntityWarning.withoutAlternative("drv_pol_payment_status")
      )
    );
  }

  @Override
  public Map<UUID, Map<String, BiFunction<String, String, FieldWarning>>> getFieldWarnings() {
    return Map.ofEntries(
      Map.entry(
        OLD_DRV_LOAN_DETAILS,
        Map.of("instance_primary_contributor", QueryBreakingWarning.withAlternative("instance.contributors"))
      ),
      Map.entry(
        OLD_DRV_ITEM_DETAILS,
        Map.of("instance_primary_contributor", QueryBreakingWarning.withAlternative("instance.contributors"))
      ),
      Map.entry(
        OLD_DRV_USER_DETAILS,
        Map.of("user_primary_address", QueryBreakingWarning.withAlternative("users.addresses"))
      )
    );
  }
}
