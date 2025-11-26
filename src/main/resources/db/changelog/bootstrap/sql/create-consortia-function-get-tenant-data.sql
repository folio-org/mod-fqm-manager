CREATE OR REPLACE FUNCTION ${tenant_id}_mod_fqm_manager.get_tenant_data()
  RETURNS TABLE(id TEXT, name TEXT) AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = '${tenant_id}_mod_consortia_keycloak'
          AND table_name = 'tenant'
    ) THEN
        RETURN QUERY
        SELECT t.id, t.name
        FROM ${tenant_id}_mod_consortia_keycloak.tenant t;
    ELSE
        RETURN;
    END IF;
END;
$$ LANGUAGE plpgsql;
