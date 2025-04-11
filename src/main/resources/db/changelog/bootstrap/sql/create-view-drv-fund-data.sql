DROP VIEW IF EXISTS drv_fund_data;

CREATE VIEW drv_fund_data AS
SELECT
  F.jsonb->>'id' as "fund_id",
	F.jsonb->>'code' as "fund_code",
	L.jsonb->>'id' as "ledger_id",
	L.jsonb->>'code' as "ledger_code",
	FY.jsonb->>'id' as "fiscal_year_id",
	FY.jsonb->>'code' as "fiscal_year_code",
	jsonb_agg(B.jsonb->'name') as "budget_names",
FROM  ${tenant_id}_mod_fqm_manager.src_finance_fund F
JOIN  ${tenant_id}_mod_fqm_manager.src_finance_budget B ON B.jsonb->>'fundId' = F.id::text
JOIN  ${tenant_id}_mod_fqm_manager.src_finance_ledger L ON L.id = F.ledgerId
JOIN  ${tenant_id}_mod_fqm_manager.src_finance_fiscal_year FY ON B.jsonb->>'fiscalYearId' = FY.id::text
GROUP BY
  F.jsonb->>'id',
  F.jsonb->>'code',
  L.jsonb->>'id',
  L.jsonb->>'code',
  FY.jsonb->>'id',
  FY.jsonb->>'code';
