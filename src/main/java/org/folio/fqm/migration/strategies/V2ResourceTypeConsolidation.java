package org.folio.fqm.migration.strategies;

import java.util.Map;
import java.util.UUID;
import org.folio.fqm.migration.AbstractSimpleMigrationStrategy;

/**
 * Version 2 -> 3, decouples simple_mode_of_issuance entity type from composite_instances
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-429
 */
public class V2ResourceTypeConsolidation extends AbstractSimpleMigrationStrategy {

  public static final UUID COMPOSITE_INSTANCES = UUID.fromString("6b08439b-4f8e-4468-8046-ea620f5cfb74");

  @Override
  public String getLabel() {
    return "V2 -> V3 Exposing resource_type directly in composite_instances (MODFQMMGR-429)";
  }

  @Override
  public String getMaximumApplicableVersion() {
    return "2";
  }

  @Override
  public Map<UUID, Map<String, String>> getFieldChanges() {
    return Map.ofEntries(
      Map.entry(
        COMPOSITE_INSTANCES,
        Map.ofEntries(
          Map.entry("instance_type.id", "instance.instance_type_id"),
          Map.entry("instance_type.name", "instance.instance_type_name")
        )
      )
    );
  }
}
