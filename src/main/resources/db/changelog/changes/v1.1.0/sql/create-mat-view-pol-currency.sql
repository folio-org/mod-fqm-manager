DROP MATERIALIZED VIEW IF EXISTS drv_pol_currency;

CREATE MATERIALIZED VIEW drv_pol_currency AS
	SELECT DISTINCT jsonb -> 'cost' ->> 'currency' AS currency FROM ${tenant_id}_mod_orders_storage.po_line;

CREATE UNIQUE INDEX fqm_pol_currency_idx
ON ${tenant_id}_mod_fqm_manager.drv_pol_currency(currency);
