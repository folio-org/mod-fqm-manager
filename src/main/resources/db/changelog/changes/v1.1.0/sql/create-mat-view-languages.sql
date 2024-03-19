DROP MATERIALIZED VIEW IF EXISTS drv_languages;

 CREATE MATERIALIZED VIEW drv_languages AS
 SELECT DISTINCT elements.languages FROM ${tenant_id}_mod_inventory_storage.instance, jsonb_array_elements_text(jsonb -> 'languages') AS elements(languages)
 WITH NO DATA;


 CREATE UNIQUE INDEX fqm_languages_idx
 ON ${tenant_id}_mod_fqm_manager.drv_languages(languages);

