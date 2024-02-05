DROP MATERIALIZED VIEW IF EXISTS drv_pol_payment_status;

CREATE MATERIALIZED VIEW drv_pol_payment_status AS
SELECT payment_status FROM
	(SELECT DISTINCT jsonb ->> 'paymentStatus' AS payment_status FROM ${tenant_id}_mod_orders_storage.po_line) existing_statuses
UNION
	(SELECT payment_status FROM
	 	(VALUES
			('Awaiting Payment'),
			('Cancelled'),
			('Fully Paid'),
			('Partially Paid'),
			('Payment Not Required'),
			('Pending')
		) AS hardcoded_statuses(payment_status)
	);

CREATE UNIQUE INDEX fqm_pol_payment_status
ON ${tenant_id}_mod_fqm_manager.drv_pol_payment_status(payment_status);
