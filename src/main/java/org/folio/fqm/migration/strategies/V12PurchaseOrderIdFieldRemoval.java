package org.folio.fqm.migration.strategies;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.migration.AbstractSimpleMigrationStrategy;
import org.folio.fqm.migration.warnings.FieldWarning;
import org.folio.fqm.migration.warnings.RemovedFieldWarning;

/**
 * Version 12 -> 13, handles a change in available fields for the POL entity type.
 *
 * The "PO ID", "PO created by ID", and "POL created by ID" fields were made API-only in Ramsons.
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-619 for the addition of this migration
 */
@Log4j2
@RequiredArgsConstructor
public class V12PurchaseOrderIdFieldRemoval extends AbstractSimpleMigrationStrategy {

  private static final UUID COMPOSITE_PURCHASE_ORDER_LINES_ENTITY_TYPE_ID = UUID.fromString(
    "abc777d3-2a45-43e6-82cb-71e8c96d13d2"
  );

  @Override
  public String getLabel() {
    return "V12 -> V13 POL ID field removal (MODFQMMGR-619)";
  }

  @Override
  public String getMaximumApplicableVersion() {
    return "12";
  }

  @Override
  public Map<UUID, Map<String, BiFunction<String, String, FieldWarning>>> getFieldWarnings() {
    return Map.ofEntries(
      Map.entry(
        COMPOSITE_PURCHASE_ORDER_LINES_ENTITY_TYPE_ID,
        Map.ofEntries(
          Map.entry("po.id", RemovedFieldWarning.withAlternative("po.po_number")),
          Map.entry("po_created_by_user.id", RemovedFieldWarning.withAlternative("po_created_by_user.username")),
          Map.entry("pol_created_by_user.id", RemovedFieldWarning.withAlternative("pol_created_by_user.username"))
        )
      )
    );
  }
}
