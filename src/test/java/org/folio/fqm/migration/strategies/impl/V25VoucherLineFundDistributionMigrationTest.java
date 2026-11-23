package org.folio.fqm.migration.strategies.impl;

import java.util.List;
import java.util.UUID;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.strategies.MigrationStrategy;
import org.folio.fqm.migration.warnings.RemovedFieldWarning;
import org.junit.jupiter.params.provider.Arguments;
import tools.jackson.databind.ObjectMapper;

class V25VoucherLineFundDistributionMigrationTest extends TestTemplate {

  private static final UUID COMPOSITE_VOUCHER_LINE_FUND_ID = UUID.fromString("86475f57-7ee2-4b7f-b631-e1a4ead3cdc8");
  private static final UUID COMPOSITE_INVOICE_VOUCHER_LINE_FUND_ORG_ID = UUID.fromString(
    "8ddd1e32-5c85-46ab-8bf3-1ec9a76c18cf"
  );
  private static final UUID SIMPLE_FUND_ID = UUID.fromString("4c63c7f5-1a28-4394-b401-98c02c31492d");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static String pretty(String json) {
    return MAPPER.readTree(json).toPrettyString();
  }

  @Override
  public MigrationStrategy getStrategy() {
    return new V25VoucherLineFundDistributionMigration();
  }

  @Override
  public List<Arguments> getExpectedTransformations() {
    return List.of(
      Arguments.of(
        "Fund, fund type, and ledger fields move into the fund distribution sub-table",
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_INVOICE_VOUCHER_LINE_FUND_ORG_ID)
          .fqlQuery(
            """
            {"_version": "24", "$and": [
              {"fund.name": {"$eq": "General"}},
              {"fund_type.name": {"$in": ["a8f0c1e2-0000-4000-8000-000000000001"]}},
              {"ledger.code": {"$eq": "ONE"}},
              {"fund.acquisition_unit": {"$eq": "b8f0c1e2-0000-4000-8000-000000000002"}},
              {"voucher_line.amount": {"$gt": 10}}
            ]}
            """
          )
          .fields(List.of("voucher_line.id", "fund.name", "fund.code", "ledger.name", "voucher_line.amount"))
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_INVOICE_VOUCHER_LINE_FUND_ORG_ID)
          .fqlQuery(
            """
            {"_version": "24", "$and": [
              {"voucher_line.fund_distribution[*]->fund_name": {"$eq": "General"}},
              {"voucher_line.fund_distribution[*]->fund_type": {"$in": ["a8f0c1e2-0000-4000-8000-000000000001"]}},
              {"voucher_line.fund_distribution[*]->ledger_code": {"$eq": "ONE"}},
              {"voucher_line.fund_distribution[*]->fund_acquisition_units": {"$eq": "b8f0c1e2-0000-4000-8000-000000000002"}},
              {"voucher_line.amount": {"$gt": 10}}
            ]}
            """
          )
          .fields(List.of("voucher_line.id", "voucher_line.fund_distribution", "voucher_line.amount"))
          .build()
      ),
      Arguments.of(
        "IDs map to the hidden sub-table properties",
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_VOUCHER_LINE_FUND_ID)
          .fqlQuery(
            """
            {"$and": [
              {"fund.id": {"$eq": "c8f0c1e2-0000-4000-8000-000000000003"}},
              {"fund_type.id": {"$eq": "d8f0c1e2-0000-4000-8000-000000000004"}},
              {"ledger.id": {"$eq": "e8f0c1e2-0000-4000-8000-000000000005"}}
            ]}
            """
          )
          .fields(List.of("fund.id", "fund_type.id", "ledger.id"))
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_VOUCHER_LINE_FUND_ID)
          .fqlQuery(
            """
            {"$and": [
              {"voucher_line.fund_distribution[*]->fund_id": {"$eq": "c8f0c1e2-0000-4000-8000-000000000003"}},
              {"voucher_line.fund_distribution[*]->fund_type_id": {"$eq": "d8f0c1e2-0000-4000-8000-000000000004"}},
              {"voucher_line.fund_distribution[*]->ledger_id": {"$eq": "e8f0c1e2-0000-4000-8000-000000000005"}}
            ]}
            """
          )
          .fields(List.of("voucher_line.fund_distribution"))
          .build()
      ),
      Arguments.of(
        "Fields that were not carried over are removed with a warning",
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_VOUCHER_LINE_FUND_ID)
          .fqlQuery(
            """
            {"$and": [
              {"ledger.fiscal_year_one": {"$eq": "FY2026"}},
              {"fund.fund_status": {"$eq": "Active"}}
            ]}
            """
          )
          .fields(List.of("voucher_line.id", "fund.created_date"))
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_VOUCHER_LINE_FUND_ID)
          .fqlQuery("{\"voucher_line.fund_distribution[*]->fund_status\": {\"$eq\": \"Active\"}}")
          .fields(List.of("voucher_line.id"))
          .warnings(
            List.of(
              RemovedFieldWarning
                .withoutAlternative()
                .apply("", "ledger.fiscal_year_one", pretty("{\"$eq\": \"FY2026\"}")),
              RemovedFieldWarning.withoutAlternative().apply("", "fund.created_date", null)
            )
          )
          .build()
      ),
      Arguments.of(
        "Fund locations is migrated with a warning",
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_INVOICE_VOUCHER_LINE_FUND_ORG_ID)
          .fqlQuery(
            """
            {"$and": [
              {"fund.locations": {"$empty": false}},
              {"fund.name": {"$eq": "General"}}
            ]}
            """
          )
          .fields(List.of("fund.locations", "fund.name"))
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_INVOICE_VOUCHER_LINE_FUND_ORG_ID)
          .fqlQuery("{\"voucher_line.fund_distribution[*]->fund_name\": {\"$eq\": \"General\"}}")
          .fields(List.of("voucher_line.fund_distribution"))
          .warnings(
            List.of(
              RemovedFieldWarning
                .withAlternative("voucher_line.fund_distribution[*]->fund_locations")
                .apply("", "fund.locations", pretty("{\"$empty\": false}")),
              RemovedFieldWarning
                .withAlternative("voucher_line.fund_distribution[*]->fund_locations")
                .apply("", "fund.locations", null)
            )
          )
          .build()
      ),
      Arguments.of(
        "Other entity types are untouched",
        MigratableQueryInformation
          .builder()
          .entityTypeId(SIMPLE_FUND_ID)
          .fqlQuery("{\"fund.name\": {\"$eq\": \"General\"}}")
          .fields(List.of("fund.name", "fund.version"))
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(SIMPLE_FUND_ID)
          .fqlQuery("{\"fund.name\": {\"$eq\": \"General\"}}")
          .fields(List.of("fund.name", "fund.version"))
          .build()
      )
    );
  }
}
