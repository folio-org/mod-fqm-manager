package org.folio.fqm.migration.strategies;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.fql.service.FqlService;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationUtils;
import org.folio.fqm.migration.warnings.Warning;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_NAME;
import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_PREPENDER;
import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_TYPE;
import static org.folio.fqm.repository.EntityTypeRepository.SUPPORTED_CUSTOM_FIELD_TYPES;
import static org.jooq.impl.DSL.field;

/**
 * Version 13 -> 14, handles custom field renaming.
 * <p>
 * The custom field naming scheme was changed in MODFQMMGR-376. This migration handles updating custom field names to match the new scheme.
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-642 for the addition of this migration
 */
@Log4j2
@RequiredArgsConstructor
public class V13CustomFieldRename implements MigrationStrategy {

  public static final String SOURCE_VERSION = "13";
  public static final String TARGET_VERSION = "14";

  static final UUID USERS_ENTITY_TYPE_ID = UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf");
  static final String CUSTOM_FIELD_SOURCE_VIEW = "src_user_custom_fields"; // Only user entity type currently supports custom fields

  private final DSLContext jooqContext;

  @Override
  public String getLabel() {
    return "V13 -> V14 Custom field renaming (MODFQMMGR-642)";
  }

  @Override
  public boolean applies(String version) {
    return SOURCE_VERSION.equals(version);
  }

  /////////////////////////////////////////////////////////////

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
    Result<Record2<Object, Object>> results = jooqContext
      .select(field("id"), field(CUSTOM_FIELD_NAME))
      .from(CUSTOM_FIELD_SOURCE_VIEW)
      .where(field(CUSTOM_FIELD_TYPE).in(SUPPORTED_CUSTOM_FIELD_TYPES))
      .fetch();

    var namePairs = results.stream()
      .map(row -> {
        String name = "";
        try {
          String id = row.get("id", String.class);
          name = row.get(CUSTOM_FIELD_NAME, String.class);
          return Pair.of(name, CUSTOM_FIELD_PREPENDER + id);
        } catch (Exception e) {
          log.error("Error processing custom field {} for entity type ID: {}", name, USERS_ENTITY_TYPE_ID, e);
          return null;
        }
      })
      .filter(Objects::nonNull)
      .toList();

    log.info("Name pairs: {}", namePairs);

    List<Warning> warnings = new ArrayList<>(query.warnings());

    return query
      .withFqlQuery(
        MigrationUtils.migrateFql(
          query.fqlQuery(),
          originalVersion -> TARGET_VERSION,
          (result, key, value) -> {
            if (!USERS_ENTITY_TYPE_ID.equals(query.entityTypeId())) {
              result.set(key, value); // no-op
              return;
            }

            ObjectNode conditions = (ObjectNode) value;
            AtomicReference<String> newKey = new AtomicReference<>("");
            conditions
              .fields()
              .forEachRemaining(entry -> {
                Optional<Pair<String, String>> namePair = namePairs.stream()
                  .filter(pair -> pair.getLeft().equals(key))
                  .findFirst();
                if (namePair.isEmpty()) {
                  result.set(key, value);
                } else {
                  String newName = namePair.get().getRight();
                  log.info("New name: {}", newName);
                  newKey.set(newName);
                }
              });
            if (!newKey.get().equals("")) {
              result.set(newKey.get(), value);
            }
          }
        )
      )
      .withWarnings(warnings)
      .withFields(query
        .fields()
        .stream()
        .map(oldName -> {
            Optional<Pair<String, String>> namePair = namePairs.stream()
              .filter(pair -> pair.getLeft().equals(oldName))
              .findFirst();
            if (namePair.isEmpty()) {
              return oldName;
            } else {
              String newName = namePair.get().getRight();
              log.info("New name: {}", newName);
              return newName;
            }
          }
      ).toList());
  }
}
