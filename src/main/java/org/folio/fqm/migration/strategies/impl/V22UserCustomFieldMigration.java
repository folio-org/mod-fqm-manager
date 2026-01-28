package org.folio.fqm.migration.strategies.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.migration.strategies.AbstractRegularMigrationStrategy;
import org.folio.fqm.migration.types.MigratableFqlFieldAndCondition;
import org.folio.fqm.migration.types.MigratableFqlFieldOnly;
import org.folio.fqm.migration.types.SingleFieldMigrationResult;

/**
 * Version 22 -> 23, migrates user custom fields from composite_user to simple_user entity type.
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-908
 */
@Log4j2
@RequiredArgsConstructor
public class V22UserCustomFieldMigration extends AbstractRegularMigrationStrategy<Void> {

  private static final UUID COMPOSITE_USERS_ID = UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf");

  @Override
  public String getMaximumApplicableVersion() {
    return "22";
  }

  @Override
  public SingleFieldMigrationResult<MigratableFqlFieldAndCondition> migrateFql(
    Void v,
    MigratableFqlFieldAndCondition condition
  ) {
    if (!COMPOSITE_USERS_ID.equals(condition.entityTypeId())) {
      return SingleFieldMigrationResult.noop(condition);
    }

    return SingleFieldMigrationResult.withField(condition.withField(getNewName(condition.field())));
  }

  @Override
  public SingleFieldMigrationResult<MigratableFqlFieldOnly> migrateFieldName(Void v, MigratableFqlFieldOnly field) {
    if (!COMPOSITE_USERS_ID.equals(field.entityTypeId())) {
      return SingleFieldMigrationResult.noop(field);
    }

    return SingleFieldMigrationResult.withField(field.withField(getNewName(field.field())));
  }

  @Override
  public String getLabel() {
    return "V22 -> V23 User custom field composite-to-simple migration (part of MODFQMMGR-908)";
  }

  public static String getNewName(String name) {
    return name != null && name.startsWith("_custom_field") ? "users." + name : name;
  }
}
