CREATE OR REPLACE VIEW drv_purchase_order_line_details
AS
SELECT pol.id,
    pol.jsonb ->> 'poLineNumber' AS purchase_order_line_number,
    pol.jsonb ->> 'receiptStatus'AS purchase_order_line_receipt_status,
    pol.jsonb ->> 'paymentStatus' AS purchase_order_line_payment_status,
    (pol.jsonb -> 'metadata'::text) ->> 'createdDate' AS purchase_order_line_created_date,
    (pol.jsonb -> 'metadata'::text) ->> 'updatedDate' AS purchase_order_line_updated_date,
    (pol.jsonb -> 'metadata'::text) ->> 'createdByUserId'AS purchase_order_line_created_by_id,
	concat_ws(', ',
        NULLIF((user_details_for_pol_created_by.jsonb -> 'personal'::text) ->> 'lastName'::text, ''),
        NULLIF((user_details_for_pol_created_by.jsonb -> 'personal'::text) ->> 'firstName'::text, '')
        ) AS purchase_order_line_created_by,
    (pol.jsonb -> 'metadata'::text) ->> 'updatedByUserId'::text AS purchase_order_line_updater_id,
	concat_ws(', ',
        NULLIF((user_details_for_pol_updated_by.jsonb -> 'personal'::text) ->> 'lastName'::text, ''),
        NULLIF((user_details_for_pol_updated_by.jsonb -> 'personal'::text) ->> 'firstName'::text, '')
        ) AS purchase_order_line_updated_by,
    pol.jsonb ->> 'description'::text AS purchase_order_line_description,
    (pol.jsonb -> 'cost'::text) ->> 'poLineEstimatedPrice' AS purchase_order_line_estimated_price,
    (pol.jsonb -> 'cost'::text) ->> 'exchangeRate' AS purchase_order_line_exchange_rate,
    COALESCE(json_array_elements.value ->> 'value'::text, NULL::text) AS fund_distribution_value,
    COALESCE(json_array_elements.value ->> 'code'::text, NULL::text) AS fund_distribution_code,
    COALESCE(json_array_elements.value ->> 'distributionType'::text, NULL::text) AS fund_distribution_type,
    COALESCE(json_array_elements.value ->> 'fundId'::text, NULL::text) AS fund_id,
    COALESCE(json_array_elements.value ->> 'encumbrance'::text, NULL::text) AS fund_distribution_encumbrance,
    pol.jsonb ->> 'purchaseOrderId' AS purchase_order_id,
    purchase_order.jsonb ->> 'approved' AS  purchase_order_approved,
    purchase_order.jsonb ->> 'orderType'AS  purchase_order_type,
    purchase_order.jsonb ->> 'poNumber' AS po_number,
    purchase_order.jsonb ->> 'workflowStatus'AS  purchase_order_workflow_status,
    (purchase_order.jsonb -> 'metadata'::text) ->> 'createdDate' AS  purchase_order_created_date,
    (purchase_order.jsonb -> 'metadata'::text) ->> 'createdByUserId' AS  purchase_order_created_by_id,
	concat_ws(', ',
        NULLIF((user_details.jsonb -> 'personal'::text) ->> 'lastName'::text, ''),
        NULLIF((user_details.jsonb -> 'personal'::text) ->> 'firstName'::text, '')
        ) AS  purchase_order_created_by,
    (purchase_order.jsonb -> 'metadata'::text) ->> 'updatedDate'::text AS purchase_order_updated_date,
	concat_ws(', ',
        NULLIF((user_details_for_order_updated_by.jsonb -> 'personal'::text) ->> 'lastName'::text, ''),
        NULLIF((user_details_for_order_updated_by.jsonb -> 'personal'::text) ->> 'firstName'::text, '')
        ) AS  purchase_order_updated_by,
    (purchase_order.jsonb -> 'metadata'::text) ->> 'updatedByUserId' AS purchase_order_updated_by_id,
    purchase_order.jsonb ->> 'vendor' AS vendor_id,
    organization_details.jsonb ->> 'code' AS vendor_code,
    organization_details.jsonb ->> 'name' AS vendor_name,
    purchase_order.jsonb ->> 'assignedTo' AS purchase_order_assigned_to_id,
	concat_ws(', ',
        NULLIF((user_details_of_assignee.jsonb -> 'personal'::text) ->> 'lastName'::text, ''),
        NULLIF((user_details_of_assignee.jsonb -> 'personal'::text) ->> 'firstName'::text, '')
        ) AS purchase_order_assigned_to,
    jsonb_array_elements(purchase_order.jsonb -> 'notes'::text)::text AS purchase_order_notes,
    ( SELECT array_to_string(array_agg(acq_id.value),',') AS array_agg
           FROM jsonb_array_elements_text(purchase_order.jsonb -> 'acqUnitIds'::text) acq_id(value)) AS acqunit_ids,
    ( SELECT array_agg(acq_unit.jsonb ->> 'name'::text) AS array_agg
           FROM jsonb_array_elements_text(purchase_order.jsonb -> 'acqUnitIds'::text) acq_id(value)
             JOIN src_acquisitions_unit acq_unit ON acq_id.value = acq_unit.id::text) AS acqunit_names
   FROM src_purchase_order_line pol
     JOIN src_purchase_order purchase_order ON purchase_order.id = ((pol.jsonb ->> 'purchaseOrderId'::text)::uuid)
     LEFT JOIN src_users_users user_details ON user_details.id = (((purchase_order.jsonb -> 'metadata'::text) ->> 'createdByUserId'::text)::uuid)
     LEFT JOIN src_users_users user_details_for_order_updated_by ON user_details_for_order_updated_by.id = (((purchase_order.jsonb -> 'metadata'::text) ->> 'updatedByUserId'::text)::uuid)
     LEFT JOIN src_users_users user_details_for_pol_created_by ON user_details_for_pol_created_by.id = (((pol.jsonb -> 'metadata'::text) ->> 'createdByUserId'::text)::uuid)
     LEFT JOIN src_users_users user_details_for_pol_updated_by ON user_details_for_pol_updated_by.id = (((pol.jsonb -> 'metadata'::text) ->> 'updatedByUserId'::text)::uuid)
	 LEFT JOIN src_organizations organization_details ON organization_details.id = ((purchase_order.jsonb ->> 'vendor'::text)::uuid)
     LEFT JOIN src_users_users user_details_of_assignee ON user_details_of_assignee.id = ((purchase_order.jsonb ->> 'assignedTo'::text)::uuid)
     LEFT JOIN LATERAL jsonb_array_elements(pol.jsonb -> 'fundDistribution'::text) json_array_elements(value) ON true;
