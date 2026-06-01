# Value Source API / Source Changes

This file lists the fields changed while removing self-referential `source` blocks from fields that already have `valueSourceApi`.

## Summary

- Removed self-referential `source` blocks from 65 fields/properties.
- Changed 40 entity type files.
- Left self-referential sources without `valueSourceApi` unchanged.

## Changed Fields

- `src/main/resources/entity-types/circulation/simple_loan_policy.json5:16` - `simple_loan_policy.id`
- `src/main/resources/entity-types/circulation/simple_loan_policy.json5:83` - `simple_loan_policy.name`
- `src/main/resources/entity-types/configuration/simple_tenant_addresses.json5:29` - `simple_tenant_addresses.name`
- `src/main/resources/entity-types/configuration/simple_tenant_addresses.json5:49` - `simple_tenant_addresses.address`
- `src/main/resources/entity-types/external/authorities/mod-entities-links/mod_entities_links__authority-source-file.json5:36` - `mod_entities_links__authority-source-file.name`
- `src/main/resources/entity-types/external/circulation/mod-feesfines/mod_feesfines__accounts.json5:144` - `mod_feesfines__accounts.fee_fine_type`
- `src/main/resources/entity-types/external/circulation/mod-feesfines/mod_feesfines__accounts.json5:167` - `mod_feesfines__accounts.fee_fine_owner`
- `src/main/resources/entity-types/external/circulation/mod-feesfines/mod_feesfines__accounts.json5:227` - `mod_feesfines__accounts.material_type`
- `src/main/resources/entity-types/external/circulation/mod-feesfines/mod_feesfines__accounts.json5:249` - `mod_feesfines__accounts.location`
- `src/main/resources/entity-types/external/circulation/mod-patron-blocks/mod_patron_blocks__user_summary.json5:246` - `mod_patron_blocks__user_summary.open_fees_fines.fee_fine_type_id`
- `src/main/resources/entity-types/external/other/mod-notes/mod_notes__note_type.json5:36` - `mod_notes__note_type.name`
- `src/main/resources/entity-types/finance/simple_expense_class.json5:34` - `simple_expense_class.name`
- `src/main/resources/entity-types/finance/simple_expense_class.json5:57` - `simple_expense_class.code`
- `src/main/resources/entity-types/finance/simple_fiscal_year.json5:88` - `simple_fiscal_year.name`
- `src/main/resources/entity-types/finance/simple_fund.json5:51` - `simple_fund.allocated_from`
- `src/main/resources/entity-types/finance/simple_fund.json5:106` - `simple_fund.allocated_to`
- `src/main/resources/entity-types/finance/simple_fund.json5:146` - `simple_fund.code`
- `src/main/resources/entity-types/finance/simple_fund_type.json5:36` - `simple_fund_type.name`
- `src/main/resources/entity-types/inventory/simple_alternative_title_type.json5:25` - `simple_alternative_title_type.name`
- `src/main/resources/entity-types/inventory/simple_call_number_type.json5:29` - `simple_call_number_type.name`
- `src/main/resources/entity-types/inventory/simple_classification_type.json5:27` - `simple_classification_type.name`
- `src/main/resources/entity-types/inventory/simple_contributor_name_type.json5:27` - `simple_contributor_name_type.name`
- `src/main/resources/entity-types/inventory/simple_contributor_type.json5:27` - `simple_contributor_type.name`
- `src/main/resources/entity-types/inventory/simple_electronic_access_relationship.json5:38` - `simple_electronic_access_relationship.name`
- `src/main/resources/entity-types/inventory/simple_holdings_note_type.json5:38` - `simple_holdings_note_type.name`
- `src/main/resources/entity-types/inventory/simple_holdings_type.json5:26` - `simple_holdings_type.name`
- `src/main/resources/entity-types/inventory/simple_identifier_type.json5:25` - `simple_identifier_type.name`
- `src/main/resources/entity-types/inventory/simple_instance.json5:245` - `simple_instance.source`
- `src/main/resources/entity-types/inventory/simple_instance_format.json5:27` - `simple_instance_format.code`
- `src/main/resources/entity-types/inventory/simple_instance_format.json5:51` - `simple_instance_format.name`
- `src/main/resources/entity-types/inventory/simple_instance_note_type.json5:16` - `simple_instance_note_type.id`
- `src/main/resources/entity-types/inventory/simple_instance_note_type.json5:36` - `simple_instance_note_type.name`
- `src/main/resources/entity-types/inventory/simple_instance_status.json5:16` - `simple_instance_status.id`
- `src/main/resources/entity-types/inventory/simple_instance_status.json5:38` - `simple_instance_status.name`
- `src/main/resources/entity-types/inventory/simple_instance_type.json5:25` - `simple_instance_type.code`
- `src/main/resources/entity-types/inventory/simple_instance_type.json5:49` - `simple_instance_type.name`
- `src/main/resources/entity-types/inventory/simple_item_damaged_status.json5:14` - `simple_item_damaged_status.id`
- `src/main/resources/entity-types/inventory/simple_item_damaged_status.json5:36` - `simple_item_damaged_status.name`
- `src/main/resources/entity-types/inventory/simple_item_note_type.json5:16` - `simple_item_note_type.id`
- `src/main/resources/entity-types/inventory/simple_item_note_type.json5:36` - `simple_item_note_type.name`
- `src/main/resources/entity-types/inventory/simple_loan_type.json5:64` - `simple_loan_type.name`
- `src/main/resources/entity-types/inventory/simple_location.json5:14` - `simple_location.id`
- `src/main/resources/entity-types/inventory/simple_location.json5:36` - `simple_location.code`
- `src/main/resources/entity-types/inventory/simple_location.json5:58` - `simple_location.name`
- `src/main/resources/entity-types/inventory/simple_location.json5:92` - `simple_location.discovery_display_name`
- `src/main/resources/entity-types/inventory/simple_loclibrary.json5:16` - `simple_loclibrary.id`
- `src/main/resources/entity-types/inventory/simple_loclibrary.json5:38` - `simple_loclibrary.name`
- `src/main/resources/entity-types/inventory/simple_loclibrary.json5:61` - `simple_loclibrary.code`
- `src/main/resources/entity-types/inventory/simple_loclibrary.json5:82` - `simple_loclibrary.campus_id`
- `src/main/resources/entity-types/inventory/simple_material_type.json5:14` - `simple_material_type.id`
- `src/main/resources/entity-types/inventory/simple_material_type.json5:36` - `simple_material_type.name`
- `src/main/resources/entity-types/inventory/simple_nature_of_content_term.json5:27` - `simple_nature_of_content_term.name`
- `src/main/resources/entity-types/inventory/simple_service_point.json5:14` - `simple_service_point.id`
- `src/main/resources/entity-types/inventory/simple_service_point.json5:50` - `simple_service_point.name`
- `src/main/resources/entity-types/inventory/simple_subject_source.json5:25` - `simple_subject_source.name`
- `src/main/resources/entity-types/inventory/simple_subject_type.json5:25` - `simple_subject_type.name`
- `src/main/resources/entity-types/invoice/simple_invoice.json5:106` - `simple_invoice.batch_group`
- `src/main/resources/entity-types/orders/simple_acquisition_method.json5:28` - `simple_acquisition_method.name`
- `src/main/resources/entity-types/organizations/simple_organization.json5:120` - `simple_organization.name`
- `src/main/resources/entity-types/organizations/simple_organization.json5:146` - `simple_organization.code`
- `src/main/resources/entity-types/simple_instance_date_type.json5:28` - `simple_instance_date_type.name`
- `src/main/resources/entity-types/simple_instance_date_type.json5:47` - `simple_instance_date_type.code`
- `src/main/resources/entity-types/tags/simple_tags.json5:25` - `simple_tags.label`
- `src/main/resources/entity-types/users/simple_group.json5:16` - `simple_group.group`
- `src/main/resources/entity-types/users/simple_group.json5:39` - `simple_group.id`
