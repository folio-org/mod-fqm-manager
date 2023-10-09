CREATE MATERIALIZED VIEW IF NOT EXISTS drv_circulation_loan_status AS
 SELECT DISTINCT jsonb -> 'status' ->> 'name' AS loan_status
 FROM ${tenant_id}_mod_circulation_storage.loan;

 REFRESH MATERIALIZED VIEW drv_circulation_loan_status;
