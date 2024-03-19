DROP MATERIALIZED VIEW IF EXISTS drv_pol_receipt_status;

CREATE MATERIALIZED VIEW drv_pol_receipt_status AS
SELECT receipt_status FROM
	(SELECT DISTINCT jsonb ->> 'receiptStatus' AS receipt_status FROM ${tenant_id}_mod_orders_storage.po_line) existing_statuses
UNION
	(SELECT receipt_status FROM
	 	(VALUES
			('Awaiting Receipt'),
			('Cancelled'),
			('Fully Received'),
			('Partially Received'),
			('Pending'),
			('Receipt Not Required')
		) AS hardcoded_statuses(receipt_status)
	)
WITH NO DATA;

CREATE UNIQUE INDEX fqm_pol_receipt_status
ON ${tenant_id}_mod_fqm_manager.drv_pol_receipt_status(receipt_status);
