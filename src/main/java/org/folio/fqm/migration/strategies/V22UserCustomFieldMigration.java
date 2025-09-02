package org.folio.fqm.migration.strategies;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.service.FqlService;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationUtils;
import org.folio.fqm.migration.warnings.Warning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Version 22 -> 23, migrates user custom fields from composite_user to simple_user entity type.
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-908
 */
@Log4j2
@RequiredArgsConstructor
public class V22UserCustomFieldMigration implements MigrationStrategy {

  private static final UUID COMPOSITE_USERS_ID = UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf");

  @Override
  public String getMaximumApplicableVersion() {
    return "22";
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
    boolean applies = COMPOSITE_USERS_ID.equals(query.entityTypeId());
    List<Warning> warnings = query.warnings() == null ? Collections.emptyList() : new ArrayList<>(query.warnings());

    List<String> newFields = applies
      ? renameCustomFields(query.fields())
      : query.fields();

    return query
      .withFqlQuery(
        MigrationUtils.migrateFql(
          query.fqlQuery(),
          (result, key, value) -> {
            if (applies && key.startsWith("_custom_field")) {
              result.remove(key);
              result.set("users." + key, value);
            } else {
              result.set(key, value);
            }
          }
        )
      )
      .withFields(newFields)
      .withHadBreakingChanges(query.hadBreakingChanges() || !warnings.isEmpty())
      .withWarnings(warnings);
  }

  @Override
  public String getLabel() {
    return "V22 -> V23 User custom field composite-to-simple migration (part of MODFQMMGR-908)";
  }

  private static List<String> renameCustomFields(List<String> fields) {
    if (fields == null) return Collections.emptyList();
    List<String> renamed = new ArrayList<>(fields.size());
    for (String name : fields) {
      renamed.add(getNewName(name));
    }
    return renamed;
  }

  public static String getNewName(String name) {
    return name != null && name.startsWith("_custom_field") ? "users." + name : name;
  }
}
