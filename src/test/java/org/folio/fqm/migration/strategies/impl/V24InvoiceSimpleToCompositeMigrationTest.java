package org.folio.fqm.migration.strategies.impl;

import java.util.List;
import java.util.UUID;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.strategies.MigrationStrategy;
import org.junit.jupiter.params.provider.Arguments;

public class V24InvoiceSimpleToCompositeMigrationTest extends TestTemplate {

  private static final UUID SIMPLE_INVOICE_ID = UUID.fromString("4d626ce1-1880-48d2-9d4c-81667fdc5dbb");
  private static final UUID COMPOSITE_INVOICE_ID = UUID.fromString("5c4cb0c9-c8bf-4fe5-b844-4de90ca445dc");
  private static final UUID COMPOSITE_INVOICE_LINE_ID = UUID.fromString("a2ea9d7a-3ed3-41c7-9cdd-f433e029ea0f");
  private static final UUID COMPOSITE_ORDER_INVOICE_ANALYTICS_ID = UUID.fromString("f3ccbf49-8e3e-4f5c-a60e-04ad80543a4a");
  private static final UUID COMPOSITE_INVOICE_VOUCHER_LINE_LEDGER_FUND_ORG_ID = UUID.fromString("8ddd1e32-5c85-46ab-8bf3-1ec9a76c18cf");
  private static final UUID COMPOSITE_INVOICE_VOUCHER_LINE_ORG_ID = UUID.fromString("2028a343-5603-4e86-99d5-c7de322c1709");

  @Override
  public MigrationStrategy getStrategy() {
    return new V24InvoiceSimpleToCompositeMigration();
  }

  @Override
  public List<Arguments> getExpectedTransformations() {
    return List.of(
      Arguments.of(
        "Migrate bill_to to bill_to.address and other fields to invoice.field",
        MigratableQueryInformation
          .builder()
          .entityTypeId(SIMPLE_INVOICE_ID)
          .fqlQuery(
            "{\"bill_to\": {\"$eq\": \"some address\"}, \"accounting_code\": {\"$eq\": \"abc\"}, \"bill_to_id\": {\"$eq\": \"some-uuid\"}}"
          )
          .fields(List.of("bill_to", "accounting_code", "bill_to_id"))
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_INVOICE_ID)
          .fqlQuery(
            "{\"$and\": [{\"bill_to.address\": {\"$eq\": \"some address\"}}, {\"invoice.accounting_code\": {\"$eq\": \"abc\"}}, {\"invoice.bill_to_id\": {\"$eq\": \"some-uuid\"}}]}"
          )
          .fields(List.of("bill_to.address", "invoice.accounting_code", "invoice.bill_to_id"))
          .build()
      ),
      Arguments.of(
        "Migrate bill_to to bill_to.address in a composite containing simple_invoice (invoice_line case)",
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_INVOICE_LINE_ID)
          .fqlQuery(
            "{\"invoice.bill_to\": {\"$eq\": \"some address\"}, \"invoice.accounting_code\": {\"$eq\": \"abc\"}}"
          )
          .fields(List.of("invoice.bill_to", "invoice.accounting_code"))
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_INVOICE_LINE_ID)
          .fqlQuery(
            "{\"$and\": [{\"bill_to.address\": {\"$eq\": \"some address\"}}, {\"invoice.accounting_code\": {\"$eq\": \"abc\"}}]}"
          )
          .fields(List.of("bill_to.address", "invoice.accounting_code"))
          .build()
      ),
      Arguments.of(
        "Migrate bill_to to bill_to.address in a composite containing simple_invoice (analytics case)",
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_ORDER_INVOICE_ANALYTICS_ID)
          .fqlQuery(
            "{\"invoice.bill_to\": {\"$eq\": \"some address\"}, \"invoice.accounting_code\": {\"$eq\": \"abc\"}}"
          )
          .fields(List.of("invoice.bill_to", "invoice.accounting_code"))
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_ORDER_INVOICE_ANALYTICS_ID)
          .fqlQuery(
            "{\"$and\": [{\"bill_to.address\": {\"$eq\": \"some address\"}}, {\"invoice.accounting_code\": {\"$eq\": \"abc\"}}]}"
          )
          .fields(List.of("bill_to.address", "invoice.accounting_code"))
          .build()
      ),
      Arguments.of(
        "Migrate bill_to to bill_to.address in a composite containing simple_invoice (voucher line ledger fund org case)",
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_INVOICE_VOUCHER_LINE_LEDGER_FUND_ORG_ID)
          .fqlQuery(
            "{\"invoice.bill_to\": {\"$eq\": \"some address\"}, \"invoice.accounting_code\": {\"$eq\": \"abc\"}}"
          )
          .fields(List.of("invoice.bill_to", "invoice.accounting_code"))
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_INVOICE_VOUCHER_LINE_LEDGER_FUND_ORG_ID)
          .fqlQuery(
            "{\"$and\": [{\"bill_to.address\": {\"$eq\": \"some address\"}}, {\"invoice.accounting_code\": {\"$eq\": \"abc\"}}]}"
          )
          .fields(List.of("bill_to.address", "invoice.accounting_code"))
          .build()
      ),
      Arguments.of(
        "Migrate bill_to to bill_to.address in a composite containing simple_invoice (voucher line org case)",
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_INVOICE_VOUCHER_LINE_ORG_ID)
          .fqlQuery(
            "{\"invoice.bill_to\": {\"$eq\": \"some address\"}, \"invoice.accounting_code\": {\"$eq\": \"abc\"}}"
          )
          .fields(List.of("invoice.bill_to", "invoice.accounting_code"))
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_INVOICE_VOUCHER_LINE_ORG_ID)
          .fqlQuery(
            "{\"$and\": [{\"bill_to.address\": {\"$eq\": \"some address\"}}, {\"invoice.accounting_code\": {\"$eq\": \"abc\"}}]}"
          )
          .fields(List.of("bill_to.address", "invoice.accounting_code"))
          .build()
      ),
      Arguments.of(
        "Do not over-migrate already-migrated fields in composite_invoice",
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_INVOICE_ID)
          .fqlQuery(
            "{\"bill_to.address\": {\"$eq\": \"some address\"}, \"invoice.accounting_code\": {\"$eq\": \"abc\"}}"
          )
          .fields(List.of("bill_to.address", "invoice.accounting_code"))
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(COMPOSITE_INVOICE_ID)
          .fqlQuery(
            "{\"$and\": [{\"bill_to.address\": {\"$eq\": \"some address\"}}, {\"invoice.accounting_code\": {\"$eq\": \"abc\"}}]}"
          )
          .fields(List.of("bill_to.address", "invoice.accounting_code"))
          .build()
      )
    );
  }
}
