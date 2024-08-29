package org.folio.fqm.migration.strategies;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.folio.fqm.migration.AbstractSimpleMigrationStrategy;
import org.folio.fqm.migration.warnings.EntityTypeWarning;
import org.folio.fqm.migration.warnings.FieldWarning;

/**
 * Version 1 -> 2, decouples simple_mode_of_issuance entity type from simple_instances
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-427
 */
public class V1ModeOfIssuanceConsolidation extends AbstractSimpleMigrationStrategy {

  public static final UUID COMPOSITE_INSTANCES = UUID.fromString("6b08439b-4f8e-4468-8046-ea620f5cfb74");

  @Override
  public String getLabel() {
    return "V1 -> V2 Removed simple_mode_of_issuance";
  }

  @Override
  public String getSourceVersion() {
    return "1";
  }

  @Override
  public String getTargetVersion() {
    return "2";
  }

  @Override
  public Map<UUID, UUID> getEntityTypeChanges() {
    return Map.of();
  }

  @Override
  public Map<UUID, Map<String, String>> getFieldChanges() {
    return Map.ofEntries(
      Map.entry(
        COMPOSITE_INSTANCES,
        Map.ofEntries(
          Map.entry("mode_of_issuance.id", "instance.mode_of_issuance_id"),
          Map.entry("mode_of_issuance.name", "instance.mode_of_issuance_name")
        )
      )
    );
  }

  @Override
  public Map<UUID, Function<String, EntityTypeWarning>> getEntityTypeWarnings() {
    return Map.of();
  }

  @Override
  public Map<UUID, Map<String, BiFunction<String, String, FieldWarning>>> getFieldWarnings() {
    return Map.of();
  }
}
