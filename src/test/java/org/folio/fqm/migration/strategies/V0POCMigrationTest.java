package org.folio.fqm.migration.strategies;

import java.util.List;
import java.util.UUID;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.junit.jupiter.params.provider.Arguments;

class V0POCMigrationTest extends TestTemplate {

  @Override
  public MigrationStrategy getStrategy() {
    return new V0POCMigration();
  }

  @Override
  public List<Arguments> getExpectedTransformations() {
    return List.of(
      Arguments.of(
        "Canned list: Missing Items List",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("0cb79a4c-f7eb-4941-a104-745224ae0292"))
          .fqlQuery(
            "{\"item_status\": {\"$in\": [\"missing\", \"aged to lost\", \"claimed returned\", \"declared lost\", \"long missing\" ] }}"
          )
          .fields(
            List.of(
              "id",
              "item_hrid",
              "holdings_id",
              "item_effective_call_number",
              "item_effective_call_number_typeid",
              "item_effective_call_number_type_name",
              "item_holdings_record_id",
              "item_status",
              "item_copy_number",
              "item_barcode",
              "item_created_date",
              "item_updated_date",
              "item_effective_location_id",
              "item_effective_location_name",
              "item_effective_library_id",
              "item_effective_library_name",
              "item_effective_library_code",
              "item_material_type_id",
              "item_material_type",
              "instance_id",
              "instance_title",
              "instance_created_date",
              "instance_updated_date",
              "instance_primary_contributor",
              "item_level_call_number",
              "item_level_call_number_typeid",
              "item_permanent_location_id",
              "item_temporary_location_id",
              "item_level_call_number_type_name",
              "item_permanent_location_name",
              "item_temporary_location_name"
            )
          )
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("d0213d22-32cf-490f-9196-d81c3c66e53f"))
          .fqlQuery(
            "{\"items.status_name\":{\"$in\":[\"missing\",\"aged to lost\",\"claimed returned\",\"declared lost\",\"long missing\"]}}"
          )
          .fields(
            List.of(
              "items.id",
              "items.hrid",
              "holdings.id",
              "items.effective_call_number",
              "effective_call_number.name",
              "items.status_name",
              "items.copy_number",
              "items.barcode",
              "items.created_date",
              "items.updated_date",
              "effective_location.name",
              "loclibrary.name",
              "loclibrary.code",
              "mtypes.name",
              "instances.id",
              "instances.title",
              "instances.created_at",
              "instances.updated_at",
              "instances.contributors",
              "items.item_level_call_number",
              "item_level_call_number.name",
              "permanent_location.name",
              "temporary_location.name"
            )
          )
          .build()
      ),
      Arguments.of(
        "Canned list: Expired Patron Loan List",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("4e09d89a-44ed-418e-a9cc-820dfb27bf3a"))
          .fqlQuery("{\"$and\": [{\"loan_status\" : {\"$eq\": \"Open\"}}, {\"user_active\" : {\"$eq\": \"false\"}}]}")
          .fields(
            List.of(
              "user_id",
              "user_first_name",
              "user_last_name",
              "user_full_name",
              "user_active",
              "user_barcode",
              "user_expiration_date",
              "user_patron_group_id",
              "user_patron_group",
              "id",
              "loan_status",
              "loan_checkout_date",
              "loan_due_date",
              "loan_policy_id",
              "loan_policy_name",
              "loan_checkout_servicepoint_id",
              "loan_checkout_servicepoint_name",
              "item_holdingsrecord_id",
              "instance_id",
              "instance_title",
              "instance_primary_contributor",
              "item_id",
              "item_barcode",
              "item_status",
              "item_material_type_id",
              "item_material_type"
            )
          )
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("d6729885-f2fb-4dc7-b7d0-a865a7f461e4"))
          .fqlQuery(
            "{\"$and\":[{\"loans.status_name\":{\"$eq\":\"Open\"}},{\"users.active\":{\"$eq\":\"false\"}}]}"
          )
          .fields(
            List.of(
              "users.id",
              "users.first_name",
              "users.last_name",
              "users.last_name_first_name",
              "users.active",
              "users.barcode",
              "users.expiration_date",
              "groups.group",
              "loans.id",
              "loans.status_name",
              "loans.checkout_date",
              "loans.due_date",
              "lpolicy.name",
              "cospi.name",
              "items.hrid",
              "instance.id",
              "instance.title",
              "instance.contributors",
              "items.id",
              "items.barcode",
              "items.status_name",
              "mtypes.name"
            )
          )
          .build()
      )
    );
  }
}
