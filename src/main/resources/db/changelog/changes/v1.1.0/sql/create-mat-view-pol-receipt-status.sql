CREATE MATERIALIZED VIEW IF NOT EXISTS drv_pol_receipt_status AS
SELECT DISTINCT jsonb ->> 'receiptStatus' AS receipt_status
FROM ${tenant_id}_mod_orders_storage.po_line;

REFRESH MATERIALIZED VIEW drv_pol_receipt_status;
