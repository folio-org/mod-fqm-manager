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
 * Version 15 -> 16, remove POL alerts and reporting codes.
 *
 * @see https://folio-org.atlassian.net/browse/MODORDERS-1269 for the addition of this migration
 */
@Log4j2
@RequiredArgsConstructor
public class V15AlertsAndReportingCodesRemoval extends AbstractSimpleMigrationStrategy {

  private static final UUID PURCHASE_ORDER_LINE_ENTITY_TYPE_ID = UUID.fromString(
    "58148257-bfb0-4687-8c42-d2833d772f3e"
  );

  @Override
  public String getLabel() {
    return "V15 -> V16 Alerts and reporting codes removal (MODORDERS-1269)";
  }

  @Override
  public String getMaximumApplicableVersion() {
    return "15";
  }

  @Override
  public Map<UUID, Map<String, BiFunction<String, String, FieldWarning>>> getFieldWarnings() {
    return Map.ofEntries(
      Map.entry(
        PURCHASE_ORDER_LINE_ENTITY_TYPE_ID,
        Map.ofEntries(
          Map.entry("pol.alerts", RemovedFieldWarning.withoutAlternative()),
          Map.entry("pol.reporting_codes", RemovedFieldWarning.withoutAlternative())
        )
      )
    );
  }
}
