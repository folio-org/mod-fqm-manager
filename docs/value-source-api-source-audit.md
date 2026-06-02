# Value Source API / Source Audit

Generated from `src/main/resources/entity-types/**/*.json5`.

This audit lists field and nested property definitions that contain `valueSourceApi`, `source`, or both. A self-referential source is an entity-type source whose `entityTypeId` matches the containing entity type and whose `columnName` resolves to the same field/property path.

## Summary

| Category | Count |
| --- | ---: |
| valueSourceApi without source | 70 |
| self-referential source | 2 |
| valueSourceApi with non-self source | 0 |
| source without valueSourceApi | 79 |

| Marker | Meaning |
|--------| --- |
| blank  | Not checked yet |
| DDD    | Depends on a changed source field and needs verification |
| Y      | Working as-is |
| YC     | Working after direct change |
| YD     | Working after indirect change (change to source field) |
| NNN    | Not working |
| NNNX   | Fix being tested |

All YCs and YDs should be double checked before final merge.

## valueSourceApi without source (70)

- Y `src/main/resources/entity-types/circulation/simple_loan_policy.json5:16` - `simple_loan_policy.id`
- Y `src/main/resources/entity-types/circulation/simple_loan_policy.json5:79` - `simple_loan_policy.name`
- Y `src/main/resources/entity-types/configuration/simple_tenant_addresses.json5:29` - `simple_tenant_addresses.name`
- Y `src/main/resources/entity-types/configuration/simple_tenant_addresses.json5:45` - `simple_tenant_addresses.address`
- Y `src/main/resources/entity-types/external/authorities/mod-entities-links/mod_entities_links__authority-heading-type.json5:34` - `mod_entities_links__authority-heading-type.name`
- Y `src/main/resources/entity-types/external/authorities/mod-entities-links/mod_entities_links__authority-identifier-type.json5:34` - `mod_entities_links__authority-identifier-type.name`
- Y `src/main/resources/entity-types/external/authorities/mod-entities-links/mod_entities_links__authority-source-file.json5:36` - `mod_entities_links__authority-source-file.name`
- Y `src/main/resources/entity-types/external/circulation/mod-feesfines/mod_feesfines__accounts.json5:144` - `mod_feesfines__accounts.fee_fine_type`
- Y `src/main/resources/entity-types/external/circulation/mod-feesfines/mod_feesfines__accounts.json5:163` - `mod_feesfines__accounts.fee_fine_owner`
- Y `src/main/resources/entity-types/external/circulation/mod-feesfines/mod_feesfines__accounts.json5:219` - `mod_feesfines__accounts.material_type`
- Y `src/main/resources/entity-types/external/circulation/mod-feesfines/mod_feesfines__accounts.json5:237` - `mod_feesfines__accounts.location`
- Y `src/main/resources/entity-types/external/circulation/mod-patron-blocks/mod_patron_blocks__user_summary.json5:246` - `mod_patron_blocks__user_summary.open_fees_fines.fee_fine_type_id`
- Y `src/main/resources/entity-types/external/other/mod-notes/mod_notes__note_type.json5:36` - `mod_notes__note_type.name`
- Y `src/main/resources/entity-types/finance/simple_expense_class.json5:34` - `simple_expense_class.name`
- Y `src/main/resources/entity-types/finance/simple_expense_class.json5:53` - `simple_expense_class.code`
- Y `src/main/resources/entity-types/finance/simple_fiscal_year.json5:83` - `simple_fiscal_year.name`
- Y `src/main/resources/entity-types/finance/simple_fund.json5:51` - `simple_fund.allocated_from`
- Y `src/main/resources/entity-types/finance/simple_fund.json5:102` - `simple_fund.allocated_to`
- Y `src/main/resources/entity-types/finance/simple_fund.json5:138` - `simple_fund.code`
- Y `src/main/resources/entity-types/finance/simple_fund_type.json5:36` - `simple_fund_type.name`
- YC `src/main/resources/entity-types/inventory/simple_alternative_title_type.json5:25` - `simple_alternative_title_type.name`
- Y `src/main/resources/entity-types/inventory/simple_call_number_type.json5:29` - `simple_call_number_type.name`
- Y `src/main/resources/entity-types/inventory/simple_classification_type.json5:27` - `simple_classification_type.name`
- YC `src/main/resources/entity-types/inventory/simple_contributor_name_type.json5:27` - `simple_contributor_name_type.name`
- YC `src/main/resources/entity-types/inventory/simple_contributor_type.json5:27` - `simple_contributor_type.name`
- YC `src/main/resources/entity-types/inventory/simple_electronic_access_relationship.json5:38` - `simple_electronic_access_relationship.name`
- YC `src/main/resources/entity-types/inventory/simple_holdings_note_type.json5:38` - `simple_holdings_note_type.name`
- Y `src/main/resources/entity-types/inventory/simple_holdings_type.json5:26` - `simple_holdings_type.name`
- Y `src/main/resources/entity-types/inventory/simple_identifier_type.json5:25` - `simple_identifier_type.name`
- Y `src/main/resources/entity-types/inventory/simple_instance.json5:245` - `simple_instance.source`
- YC `src/main/resources/entity-types/inventory/simple_instance_format.json5:27` - `simple_instance_format.code`
- YC `src/main/resources/entity-types/inventory/simple_instance_format.json5:47` - `simple_instance_format.name`
- Y `src/main/resources/entity-types/inventory/simple_instance_note_type.json5:16` - `simple_instance_note_type.id`
- Y `src/main/resources/entity-types/inventory/simple_instance_note_type.json5:32` - `simple_instance_note_type.name`
- Y `src/main/resources/entity-types/inventory/simple_instance_status.json5:16` - `simple_instance_status.id`
- Y `src/main/resources/entity-types/inventory/simple_instance_status.json5:34` - `simple_instance_status.name`
- Y `src/main/resources/entity-types/inventory/simple_instance_type.json5:25` - `simple_instance_type.code`
- Y `src/main/resources/entity-types/inventory/simple_instance_type.json5:45` - `simple_instance_type.name`
- Y `src/main/resources/entity-types/inventory/simple_item_damaged_status.json5:14` - `simple_item_damaged_status.id`
- Y `src/main/resources/entity-types/inventory/simple_item_damaged_status.json5:32` - `simple_item_damaged_status.name`
- Y `src/main/resources/entity-types/inventory/simple_item_note_type.json5:16` - `simple_item_note_type.id`
- Y `src/main/resources/entity-types/inventory/simple_item_note_type.json5:32` - `simple_item_note_type.name`
- Y `src/main/resources/entity-types/inventory/simple_loan_type.json5:64` - `simple_loan_type.name`
- Y `src/main/resources/entity-types/inventory/simple_location.json5:14` - `simple_location.id`
- Y `src/main/resources/entity-types/inventory/simple_location.json5:32` - `simple_location.code`
- Y `src/main/resources/entity-types/inventory/simple_location.json5:50` - `simple_location.name`
- Y `src/main/resources/entity-types/inventory/simple_location.json5:80` - `simple_location.discovery_display_name`
- Y `src/main/resources/entity-types/inventory/simple_loclibrary.json5:16` - `simple_loclibrary.id`
- Y `src/main/resources/entity-types/inventory/simple_loclibrary.json5:34` - `simple_loclibrary.name`
- Y `src/main/resources/entity-types/inventory/simple_loclibrary.json5:53` - `simple_loclibrary.code`
- Y `src/main/resources/entity-types/inventory/simple_loclibrary.json5:70` - `simple_loclibrary.campus_id`
- Y `src/main/resources/entity-types/inventory/simple_material_type.json5:14` - `simple_material_type.id`
- Y `src/main/resources/entity-types/inventory/simple_material_type.json5:32` - `simple_material_type.name`
- Y `src/main/resources/entity-types/inventory/simple_mode_of_issuance.json5:29` - `simple_mode_of_issuance.name`
- YC `src/main/resources/entity-types/inventory/simple_nature_of_content_term.json5:27` - `simple_nature_of_content_term.name`
- Y `src/main/resources/entity-types/inventory/simple_service_point.json5:14` - `simple_service_point.id`
- Y `src/main/resources/entity-types/inventory/simple_service_point.json5:46` - `simple_service_point.name`
- YC `src/main/resources/entity-types/inventory/simple_subject_source.json5:25` - `simple_subject_source.name`
- YC `src/main/resources/entity-types/inventory/simple_subject_type.json5:25` - `simple_subject_type.name`
- Y `src/main/resources/entity-types/invoice/simple_invoice.json5:106` - `simple_invoice.batch_group`
- Y `src/main/resources/entity-types/orders/simple_acquisition_method.json5:28` - `simple_acquisition_method.name`
- Y `src/main/resources/entity-types/organizations/simple_organization.json5:120` - `simple_organization.name`
- Y `src/main/resources/entity-types/organizations/simple_organization.json5:141` - `simple_organization.code`
- YC `src/main/resources/entity-types/shared/simple_acq_unit.json5:25` - `simple_acq_unit.name`
- Y `src/main/resources/entity-types/simple_instance_date_type.json5:28` - `simple_instance_date_type.name`
- YC `src/main/resources/entity-types/simple_instance_date_type.json5:43` - `simple_instance_date_type.code`
- YC `src/main/resources/entity-types/tags/simple_tags.json5:25` - `simple_tags.label`
- YC `src/main/resources/entity-types/users/simple_department.json5:27` - `simple_department.name`
- Y `src/main/resources/entity-types/users/simple_group.json5:16` - `simple_group.group`
- Y `src/main/resources/entity-types/users/simple_group.json5:35` - `simple_group.id`

## self-referential source (2)

- YC `src/main/resources/entity-types/inventory/simple_inventory_statistical_code_full.json5:28` - `simple_inventory_statistical_code_full.statistical_code`
- YC `src/main/resources/entity-types/organizations/simple_organization_type.json5:93` - `simple_organization_type.name`

## valueSourceApi with non-self source (0)

_None found._

## source without valueSourceApi (79)

- YC `src/main/resources/entity-types/consortia/simple_consortia_tenant.json5:26` - `simple_consortia_tenant.name` -> source: `FQM.tenant_name`
- Y `src/main/resources/entity-types/external/acquisition/mod-orders-storage/mod_orders_storage__titles.json5:135` - `mod_orders_storage__titles.product_ids.product_id_type` -> source: `simple_identifier_type.name`
- YD `src/main/resources/entity-types/external/acquisition/mod-orders-storage/mod_orders_storage__titles.json5:528` - `mod_orders_storage__titles.acq_unit_names` -> source: `simple_acq_unit.name`
- Y `src/main/resources/entity-types/external/authorities/mod-entities-links/mod_entities_links__authority.json5:85` - `mod_entities_links__authority.heading_type` -> source: `mod_entities_links__authority-heading-type.name`
- Y `src/main/resources/entity-types/external/authorities/mod-entities-links/mod_entities_links__authority.json5:129` - `mod_entities_links__authority.sft_headings.heading_type` -> source: `mod_entities_links__authority-heading-type.name`
- Y `src/main/resources/entity-types/external/authorities/mod-entities-links/mod_entities_links__authority.json5:202` - `mod_entities_links__authority.saft_headings.heading_type` -> source: `mod_entities_links__authority-heading-type.name`
- Y `src/main/resources/entity-types/external/authorities/mod-entities-links/mod_entities_links__authority.json5:286` - `mod_entities_links__authority.identifiers.identifier_type` -> source: `mod_entities_links__authority-identifier-type.name`
- Y `src/main/resources/entity-types/external/authorities/mod-entities-links/mod_entities_links__authority.json5:327` - `mod_entities_links__authority.notes.note_type` -> source: `mod_entities_links__authority-note-type.name`
- Y `src/main/resources/entity-types/external/circulation/mod-circulation-storage/mod_circulation_storage__actual_cost_record.json5:187` - `mod_circulation_storage__actual_cost_record.user_patron_group` -> source: `simple_group.group`
- YD `src/main/resources/entity-types/finance/simple_budget.json5:431` - `simple_budget.tags` -> source: `simple_tags.label`
- YD `src/main/resources/entity-types/finance/simple_finance_group.json5:114` - `simple_finance_group.acquisition_unit` -> source: `simple_acq_unit.name`
- YD `src/main/resources/entity-types/finance/simple_fiscal_year.json5:62` - `simple_fiscal_year.acquisition_unit` -> source: `simple_acq_unit.name`
- Y `src/main/resources/entity-types/finance/simple_fund.json5:271` - `simple_fund.donor_organizations` -> source: `simple_organization.name`
- YD `src/main/resources/entity-types/finance/simple_fund.json5:388` - `simple_fund.tag_list` -> source: `simple_tags.label`
- YD `src/main/resources/entity-types/finance/simple_fund.json5:492` - `simple_fund.acquisition_unit` -> source: `simple_acq_unit.name`
- Y `src/main/resources/entity-types/finance/simple_ledger.json5:85` - `simple_ledger.fiscal_year_one` -> source: `simple_fiscal_year.name`
- YD `src/main/resources/entity-types/finance/simple_ledger.json5:158` - `simple_ledger.acquisition_unit` -> source: `simple_acq_unit.name`
- YD `src/main/resources/entity-types/finance/simple_transaction.json5:276` - `simple_transaction.tags` -> source: `simple_tags.label`
- Y `src/main/resources/entity-types/inventory/simple_holdings_record.json5:156` - `simple_holdings_record.call_number_type` -> source: `simple_call_number_type.name`
- YD `src/main/resources/entity-types/inventory/simple_holdings_record.json5:414` - `simple_holdings_record.statistical_code_names` -> source: `simple_inventory_statistical_code_full.statistical_code`
- YD `src/main/resources/entity-types/inventory/simple_holdings_record.json5:626` - `simple_holdings_record.electronic_access.relationship` -> source: `simple_electronic_access_relationship.name`
- YD `src/main/resources/entity-types/inventory/simple_holdings_record.json5:823` - `simple_holdings_record.tags` -> source: `simple_tags.label`
- YD `src/main/resources/entity-types/inventory/simple_holdings_record.json5:869` - `simple_holdings_record.notes.holdings_note_type` -> source: `simple_holdings_note_type.name`
- Y `src/main/resources/entity-types/inventory/simple_holdings_record.json5:929` - `simple_holdings_record.tenant_id` -> source: `FQM.tenant_id`
- Y `src/main/resources/entity-types/inventory/simple_holdings_record.json5:947` - `simple_holdings_record.tenant_name` -> source: `FQM.tenant_name`
- Y `src/main/resources/entity-types/inventory/simple_holdings_record.json5:1019` - `simple_holdings_record.additional_call_numbers.call_number_type_id` -> source: `simple_call_number_type.name`
- Y `src/main/resources/entity-types/inventory/simple_instance.json5:169` - `simple_instance.instance_type_name` -> source: `simple_instance_type.name`
- Y `src/main/resources/entity-types/inventory/simple_instance.json5:201` - `simple_instance.mode_of_issuance_name` -> source: `simple_mode_of_issuance.name`
- DDD `src/main/resources/entity-types/inventory/simple_instance.json5:327` - `simple_instance.alternative_titles.title_type` -> source: `simple_alternative_title_type.name`
- `src/main/resources/entity-types/inventory/simple_instance.json5:420` - `simple_instance.identifiers.identifier_type_name` -> source: `simple_identifier_type.name`
- DDD `src/main/resources/entity-types/inventory/simple_instance.json5:502` - `simple_instance.contributors.contributor_name_type` -> source: `simple_contributor_name_type.name`
- DDD `src/main/resources/entity-types/inventory/simple_instance.json5:516` - `simple_instance.contributors.contributor_type` -> source: `simple_contributor_type.name`
- DDD `src/main/resources/entity-types/inventory/simple_instance.json5:626` - `simple_instance.subjects.subject_source` -> source: `simple_subject_source.name`
- DDD `src/main/resources/entity-types/inventory/simple_instance.json5:642` - `simple_instance.subjects.subject_type` -> source: `simple_subject_type.name`
- `src/main/resources/entity-types/inventory/simple_instance.json5:694` - `simple_instance.classifications.type_name` -> source: `simple_classification_type.name`
- DDD `src/main/resources/entity-types/inventory/simple_instance.json5:916` - `simple_instance.electronic_access.relationship` -> source: `simple_electronic_access_relationship.name`
- DDD `src/main/resources/entity-types/inventory/simple_instance.json5:1004` - `simple_instance.format_names` -> source: `simple_instance_format.name`
- `src/main/resources/entity-types/inventory/simple_instance.json5:1050` - `simple_instance.languages` -> source: `FQM.languages`
- `src/main/resources/entity-types/inventory/simple_instance.json5:1093` - `simple_instance.notes.instance_note_type` -> source: `simple_instance_note_type.name`
- DDD `src/main/resources/entity-types/inventory/simple_instance.json5:1269` - `simple_instance.statistical_code_names` -> source: `simple_inventory_statistical_code_full.statistical_code`
- DDD `src/main/resources/entity-types/inventory/simple_instance.json5:1323` - `simple_instance.tags` -> source: `simple_tags.label`
- DDD `src/main/resources/entity-types/inventory/simple_instance.json5:1377` - `simple_instance.nature_of_content_term` -> source: `simple_nature_of_content_term.name`
- Y `src/main/resources/entity-types/inventory/simple_instance.json5:1430` - `simple_instance.tenant_id` -> source: `FQM.tenant_id`
- Y `src/main/resources/entity-types/inventory/simple_instance.json5:1449` - `simple_instance.tenant_name` -> source: `FQM.tenant_name`
- `src/main/resources/entity-types/inventory/simple_item.json5:62` - `simple_item.notes.item_note_type` -> source: `simple_item_note_type.name`
- DDD `src/main/resources/entity-types/inventory/simple_item.json5:555` - `simple_item.electronic_access.relationship` -> source: `simple_electronic_access_relationship.name`
- YD `src/main/resources/entity-types/inventory/simple_item.json5:666` - `simple_item.statistical_code_names` -> source: `simple_inventory_statistical_code_full.statistical_code`
- YD `src/main/resources/entity-types/inventory/simple_item.json5:1044` - `simple_item.tags` -> source: `simple_tags.label`
- `src/main/resources/entity-types/inventory/simple_item.json5:1178` - `simple_item.item_damaged_status` -> source: `simple_item_damaged_status.name`
- Y `src/main/resources/entity-types/inventory/simple_item.json5:1271` - `simple_item.tenant_id` -> source: `FQM.tenant_id`
- Y `src/main/resources/entity-types/inventory/simple_item.json5:1289` - `simple_item.tenant_name` -> source: `FQM.tenant_name`
- `src/main/resources/entity-types/inventory/simple_item.json5:1373` - `simple_item.additional_call_numbers.call_number_type_id` -> source: `simple_call_number_type.name`
- `src/main/resources/entity-types/invoice/simple_invoice.json5:461` - `simple_invoice.vendor_name` -> source: `simple_organization.name`
- `src/main/resources/entity-types/invoice/simple_invoice.json5:512` - `simple_invoice.fiscal_year` -> source: `simple_fiscal_year.name`
- DDD `src/main/resources/entity-types/invoice/simple_invoice.json5:569` - `simple_invoice.acquisition_unit` -> source: `simple_acq_unit.name`
- DDD `src/main/resources/entity-types/invoice/simple_invoice.json5:673` - `simple_invoice.tag_list` -> source: `simple_tags.label`
- DDD `src/main/resources/entity-types/invoice/simple_invoice_line.json5:481` - `simple_invoice_line.tag_list` -> source: `simple_tags.label`
- DDD `src/main/resources/entity-types/invoice/simple_voucher.json5:218` - `simple_voucher.acquisition_units` -> source: `simple_acq_unit.name`
- `src/main/resources/entity-types/invoice/simple_voucher.json5:262` - `simple_voucher.batch_group` -> source: `simple_invoice.batch_group`
- `src/main/resources/entity-types/invoice/simple_voucher_line.json5:160` - `simple_voucher_line.fund_distribution.code` -> source: `simple_fund.code`
- `src/main/resources/entity-types/orders/composite_order_invoice_analytics.json5:172` - `composite_order_invoice_analytics.all_fiscal_years` -> source: `simple_fiscal_year.name`
- DDD `src/main/resources/entity-types/orders/simple_purchase_order.json5:384` - `simple_purchase_order.acquisition_unit` -> source: `simple_acq_unit.name`
- DDD `src/main/resources/entity-types/orders/simple_purchase_order.json5:417` - `simple_purchase_order.tags` -> source: `simple_tags.label`
- `src/main/resources/entity-types/orders/simple_purchase_order_line.json5:156` - `simple_purchase_order_line.acquisition_method` -> source: `simple_acquisition_method.id`
- `src/main/resources/entity-types/orders/simple_purchase_order_line.json5:173` - `simple_purchase_order_line.acquisition_method_name` -> source: `simple_acquisition_method.name`
- `src/main/resources/entity-types/orders/simple_purchase_order_line.json5:416` - `simple_purchase_order_line.cost_currency` -> source: `FQM.currency`
- `src/main/resources/entity-types/orders/simple_purchase_order_line.json5:714` - `simple_purchase_order_line.donor_organization_ids` -> source: `simple_organization.id`
- `src/main/resources/entity-types/orders/simple_purchase_order_line.json5:935` - `simple_purchase_order_line.fund_distribution.code` -> source: `simple_fund.code`
- `src/main/resources/entity-types/orders/simple_purchase_order_line.json5:1155` - `simple_purchase_order_line.locations.location_name` -> source: `simple_location.name`
- `src/main/resources/entity-types/orders/simple_purchase_order_line.json5:1188` - `simple_purchase_order_line.locations.location_code` -> source: `simple_location.code`
- YD `src/main/resources/entity-types/orders/simple_purchase_order_line.json5:1276` - `simple_purchase_order_line.locations.tenant_name` -> source: `simple_consortia_tenant.name`
- DDD `src/main/resources/entity-types/orders/simple_purchase_order_line.json5:1770` - `simple_purchase_order_line.tags` -> source: `simple_tags.label`
- DDD `src/main/resources/entity-types/organizations/simple_organization.json5:239` - `simple_organization.type_names` -> source: `simple_organization_type.name`
- DDD `src/main/resources/entity-types/organizations/simple_organization.json5:1583` - `simple_organization.acq_unit_names` -> source: `simple_acq_unit.name`
- DDD `src/main/resources/entity-types/organizations/simple_organization.json5:1603` - `simple_organization.tags` -> source: `simple_tags.label`
- `src/main/resources/entity-types/users/simple_user_details.json5:264` - `simple_user_details.addresses.country_id` -> source: `FQM.countries`
- `src/main/resources/entity-types/users/simple_user_details.json5:302` - `simple_user_details.addresses.address_type_id` -> source: `simple_address_type.type`
- DDD `src/main/resources/entity-types/users/simple_user_details.json5:529` - `simple_user_details.tags_tag_list` -> source: `simple_tags.label`
- YD `src/main/resources/entity-types/users/simple_user_details.json5:576` - `simple_user_details.departments` -> source: `simple_department.name`
