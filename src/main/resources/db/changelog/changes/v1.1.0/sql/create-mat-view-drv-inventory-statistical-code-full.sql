DROP MATERIALIZED VIEW IF EXISTS drv_inventory_statistical_code_full;

CREATE MATERIALIZED VIEW drv_inventory_statistical_code_full AS
SELECT
	statcode.id AS "id",
	CONCAT(stattype.jsonb ->> 'name', ': ', statcode.jsonb ->> 'code', ' - ', statcode.jsonb ->> 'name') AS "statistical_code"
FROM ${tenant_id}_mod_fqm_manager.src_inventory_statistical_code statcode
JOIN ${tenant_id}_mod_fqm_manager.src_inventory_statistical_code_type stattype ON stattype.id::text = statcode.jsonb ->> 'statisticalCodeTypeId';

CREATE UNIQUE INDEX fqm_statistical_code_full_idx
ON ${tenant_id}_mod_fqm_manager.drv_inventory_statistical_code_full(statistical_code);
