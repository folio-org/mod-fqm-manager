DROP MATERIALIZED VIEW IF EXISTS drv_inventory_statistical_code_full;
DROP MATERIALIZED VIEW IF EXISTS drv_circulation_loan_status;
DROP MATERIALIZED VIEW IF EXISTS drv_inventory_item_status;
DROP VIEW IF EXISTS drv_inventory_statistical_codes_full;

CREATE VIEW drv_inventory_statistical_codes_full AS
SELECT
	statcode.id AS "id",
	CONCAT(stattype.jsonb ->> 'name', ': ', statcode.jsonb ->> 'code', ' - ', statcode.jsonb ->> 'name') AS "statistical_code"
FROM ${tenant_id}_mod_fqm_manager.src_inventory_statistical_code statcode
JOIN ${tenant_id}_mod_fqm_manager.src_inventory_statistical_code_type stattype ON stattype.id::text = statcode.jsonb ->> 'statisticalCodeTypeId';
