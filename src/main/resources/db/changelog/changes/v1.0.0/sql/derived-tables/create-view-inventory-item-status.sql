CREATE MATERIALIZED VIEW drv_inventory_item_status AS
SELECT DISTINCT jsonb -> 'status' ->> 'name' AS item_status
FROM ${tenant_id}_mod_inventory_storage.item;

REFRESH MATERIALIZED VIEW drv_inventory_item_status;
