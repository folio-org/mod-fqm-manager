DROP MATERIALIZED VIEW IF EXISTS drv_languages;

 CREATE MATERIALIZED VIEW drv_languages AS
 	SELECT DISTINCT jsonb ->> 'languages' AS languages FROM ${tenant_id}_mod_inventory_storage.instance;

 CREATE UNIQUE INDEX fqm_languages_idx
 ON ${tenant_id}_mod_fqm_manager.drv_languages(languages);
