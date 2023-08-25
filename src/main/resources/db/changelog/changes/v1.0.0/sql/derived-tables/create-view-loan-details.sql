CREATE OR REPLACE VIEW drv_loan_details
AS
SELECT hrim.instanceid                                                                      AS instance_id,
       jsonb_path_query_first(
       instance_details.jsonb,
       '$."contributors"[*]?(@."primary" == true)."name"'::jsonpath)
        #>> '{}'::text[]                                                                    AS instance_primary_contributor,
       instance_details.jsonb ->> 'title'::text                                             AS instance_title,
       item_details.jsonb ->> 'barcode'::text                                               AS item_barcode,
       (item_details.jsonb -> 'effectiveCallNumberComponents'::text) ->> 'callNumber'::text AS item_call_number,
       item_details.holdingsrecordid                                                        AS item_holdingsrecord_id,
       item_details.id                                                                      AS item_id,
       material_type_ref_data.jsonb ->> 'name'::text                                        AS item_material_type,
       item_details.materialtypeid                                                          AS item_material_type_id,
       (item_details.jsonb -> 'status'::text) ->> 'name'::text                              AS item_status,
       cispi.id                                                                             AS loan_checkin_servicepoint_id,
       cispi.jsonb ->> 'name'::text                                                         AS loan_checkin_servicepoint_name,
       src_circulation_loan.jsonb ->> 'loanDate'::text                                      AS loan_checkout_date,
       cospi.id                                                                             AS loan_checkout_servicepoint_id,
       cospi.jsonb ->> 'name'::text                                                         AS loan_checkout_servicepoint_name,
       src_circulation_loan.jsonb ->> 'dueDate'::text                                       AS loan_due_date,
       src_circulation_loan.id                                                              AS id,
       loan_policy_ref_data.id                                                              AS loan_policy_id,
       loan_policy_ref_data.jsonb ->> 'name'::text                                          AS loan_policy_name,
       src_circulation_loan.jsonb ->> 'returnDate'::text                                    AS loan_return_date,
       (src_circulation_loan.jsonb -> 'status'::text) ->> 'name'::text                      AS loan_status,
       user_details.jsonb ->> 'active'::text                                                AS user_active,
       user_details.jsonb ->> 'barcode'::text                                               AS user_barcode,
       user_details.jsonb ->> 'expirationDate'::text                                        AS user_expiration_date,
       (user_details.jsonb -> 'personal'::text) ->> 'firstName'::text                       AS user_first_name,
       concat((user_details.jsonb -> 'personal'::text) ->> 'lastName'::text, ', ',
              (user_details.jsonb -> 'personal'::text) ->> 'firstName'::text)               AS user_full_name,
       user_details.id                                                                      AS user_id,
       (user_details.jsonb -> 'personal'::text) ->> 'lastName'::text                        AS user_last_name,
       patron_id_ref_data.jsonb ->> 'group'::text                                           AS user_patron_group,
       patron_id_ref_data.id::text                                                          AS user_patron_group_id
FROM src_circulation_loan
       LEFT JOIN src_circulation_loan_policy loan_policy_ref_data
                 ON loan_policy_ref_data.id = ((src_circulation_loan.jsonb ->> 'loanPolicyId'::text)::uuid)
       LEFT JOIN src_inventory_service_point cospi
                 ON cospi.id = "left"(lower(src_circulation_loan.jsonb ->> 'checkoutServicePointId'::text), 600)::uuid
       LEFT JOIN src_inventory_service_point cispi
                 ON cispi.id = "left"(lower(src_circulation_loan.jsonb ->> 'checkinServicePointId'::text), 600)::uuid
       JOIN src_inventory_item item_details
            ON item_details.id = "left"(lower(f_unaccent(src_circulation_loan.jsonb ->> 'itemId'::text)), 600)::uuid
       LEFT JOIN src_inventory_material_type material_type_ref_data
                 ON material_type_ref_data.id = item_details.materialtypeid
       JOIN src_users_users user_details
            ON user_details.id = "left"(lower(f_unaccent(src_circulation_loan.jsonb ->> 'userId'::text)), 600)::uuid
       LEFT JOIN src_users_groups patron_id_ref_data
                 ON patron_id_ref_data.id ::text = (user_details.jsonb ->> 'patronGroup'::text)
       JOIN src_inventory_holdings_record hrim
            ON item_details.holdingsrecordid = hrim.id
       JOIN src_inventory_instance instance_details
            ON hrim.instanceid = instance_details.id;
