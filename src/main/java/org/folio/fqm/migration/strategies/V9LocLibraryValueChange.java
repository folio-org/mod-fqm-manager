package org.folio.fqm.migration.strategies;

import java.util.List;
import java.util.UUID;
import org.folio.fqm.client.LocationUnitsClient;

/**
 * Version 9 -> 10, handles a change in the effective library name and code fields in the holdings entity type.
 *
 * Originally, values for this field were stored as the library's ID, however, this was changed to use the
 * name/code itself. As such, we need to update queries to map the original IDs to their corresponding values.
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-602 for adding this migration
 */
public class V9LocLibraryValueChange extends AbstractLibraryValueChangeMigration {

  private static final UUID HOLDINGS_ENTITY_TYPE_ID = UUID.fromString("8418e512-feac-4a6a-a56d-9006aab31e33");
  private static final List<String> FIELD_NAMES = List.of("effective_library.code", "effective_library.name");

  public V9LocLibraryValueChange(LocationUnitsClient locationUnitsClient) {
    super(locationUnitsClient);
  }

  @Override
  protected UUID getEntityTypeId() {
    return HOLDINGS_ENTITY_TYPE_ID;
  }

  @Override
  protected List<String> getFieldNames() {
    return FIELD_NAMES;
  }

  @Override
  public String getMaximumApplicableVersion() {
    return "9";
  }

  @Override
  public String getLabel() {
    return "V9 -> V10 holdings effective library name/code value transformation (MODFQMMGR-602)";
  }
}
