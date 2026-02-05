package org.folio.fqm.migration.strategies.impl;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.migration.strategies.AbstractSimpleMigrationStrategy;
import org.folio.fqm.migration.warnings.FieldWarningFactory;
import org.folio.fqm.migration.warnings.RemovedFieldWarning;

/**
 * Version 24, migrates from simple_invoice to composite_invoice entity type.
 * All fields from simple_invoice get "invoice." prepended to their names.
 * The (now non-existent) bill_to field is specifically migrated to bill_to.address.
 * For other composites containing simple_invoice, a warning is issued for bill_to references.
 */
@Log4j2
@RequiredArgsConstructor
public class V24InvoiceSimpleToCompositeMigration extends AbstractSimpleMigrationStrategy {

  private static final UUID SIMPLE_INVOICE_ID = UUID.fromString("4d626ce1-1880-48d2-9d4c-81667fdc5dbb");
  private static final UUID COMPOSITE_INVOICE_ID = UUID.fromString("5c4cb0c9-c8bf-4fe5-b844-4de90ca445dc");
  private static final UUID COMPOSITE_INVOICE_LINE_ID = UUID.fromString("a2ea9d7a-3ed3-41c7-9cdd-f433e029ea0f");
  private static final UUID COMPOSITE_ORDER_INVOICE_ANALYTICS_ID = UUID.fromString("f3ccbf49-8e3e-4f5c-a60e-04ad80543a4a");
  private static final UUID COMPOSITE_INVOICE_VOUCHER_LINE_LEDGER_FUND_ORG_ID = UUID.fromString("8ddd1e32-5c85-46ab-8bf3-1ec9a76c18cf");
  private static final UUID COMPOSITE_INVOICE_VOUCHER_LINE_ORG_ID = UUID.fromString("2028a343-5603-4e86-99d5-c7de322c1709");

  @Override
  public String getMaximumApplicableVersion() {
    return "24";
  }

  @Override
  public String getLabel() {
    return "V24 Invoice simple to composite migration";
  }

  @Override
  public Map<UUID, Map<String, FieldWarningFactory>> getFieldWarnings() {
    FieldWarningFactory billToWarning = RemovedFieldWarning.withoutAlternative();
    return Map.of(
      COMPOSITE_INVOICE_LINE_ID,
      Map.of("invoice.bill_to", billToWarning),
      COMPOSITE_ORDER_INVOICE_ANALYTICS_ID,
      Map.of("invoice.bill_to", billToWarning),
      COMPOSITE_INVOICE_VOUCHER_LINE_LEDGER_FUND_ORG_ID,
      Map.of("invoice.bill_to", billToWarning),
      COMPOSITE_INVOICE_VOUCHER_LINE_ORG_ID,
      Map.of("invoice.bill_to", billToWarning)
    );
  }

  @Override
  public Map<UUID, UUID> getEntityTypeChanges() {
    return Map.of(SIMPLE_INVOICE_ID, COMPOSITE_INVOICE_ID);
  }

  @Override
  public Map<UUID, Map<String, String>> getFieldChanges() {
    return Map.of(
      SIMPLE_INVOICE_ID,
      Map.of(
        "*", "invoice.%s",
        "bill_to", "bill_to.address"
      )
    );
  }
}
