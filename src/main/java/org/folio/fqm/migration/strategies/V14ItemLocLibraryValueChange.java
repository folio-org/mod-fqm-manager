package org.folio.fqm.migration.strategies;

import java.util.List;
import java.util.UUID;
import org.folio.fqm.client.LocationUnitsClient;

/**
 * Version 14 -> 15, handles a change in the effective library name and code fields in the items entity type.
 *
 * Originally, values for this field were stored as the library's ID, however, this was changed to use the
 * name/code itself. As such, we need to update queries to map the original IDs to their corresponding values.
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-883 for adding this migration
 */
public class V14ItemLocLibraryValueChange extends AbstractLibraryValueChangeMigration {

  private static final UUID ITEMS_ENTITY_TYPE_ID = UUID.fromString("d0213d22-32cf-490f-9196-d81c3c66e53f");
  private static final List<String> FIELD_NAMES = List.of("loclibrary.code", "loclibrary.name");

  public V14ItemLocLibraryValueChange(LocationUnitsClient locationUnitsClient) {
    super(locationUnitsClient);
  }

  @Override
  protected UUID getEntityTypeId() {
    return ITEMS_ENTITY_TYPE_ID;
  }

  @Override
  protected List<String> getFieldNames() {
    return FIELD_NAMES;
  }

  @Override
  public String getMaximumApplicableVersion() {
    return "14";
  }

  @Override
  public String getLabel() {
    return "V14 -> V15 item effective library name/code value transformation (MODFQMMGR-883)";
  }
}
