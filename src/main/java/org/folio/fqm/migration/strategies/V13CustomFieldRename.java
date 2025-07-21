package org.folio.fqm.migration.strategies;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.fql.service.FqlService;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationUtils;
import org.folio.fqm.migration.warnings.Warning;
import org.folio.spring.FolioExecutionContext;
import org.jooq.DSLContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_NAME;
import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_PREPENDER;
import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_TYPE;
import static org.folio.fqm.repository.EntityTypeRepository.SUPPORTED_CUSTOM_FIELD_TYPES;
import static org.jooq.impl.DSL.field;

/**
 * Version 13 / V13.5 -> 14, handles custom field renaming.
 *
 * The custom field naming scheme was changed in MODFQMMGR-376. This migration handles updating custom field names to match the new scheme.
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-642 for the addition of this migration
 */
@Log4j2
@RequiredArgsConstructor
public class V13CustomFieldRename implements MigrationStrategy {

  static final UUID USERS_ENTITY_TYPE_ID = UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf");
  static final String CUSTOM_FIELD_SOURCE_VIEW = "src_user_custom_fields"; // Only user entity type currently supports custom fields

  private final FolioExecutionContext executionContext;
  private final DSLContext jooqContext;

  private final Map<String, List<Pair<String, String>>> tenantCustomFieldNamePairs = new ConcurrentHashMap<>();

  @Override
  public String getMaximumApplicableVersion() {
    return "13.5";
  }

  @Override
  public String getLabel() {
    return "V13 / V13.5 -> V14 Custom field renaming (MODFQMMGR-642)";
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
    List<Warning> warnings = new ArrayList<>(query.warnings());

    return query
      .withFqlQuery(
        MigrationUtils.migrateFql(
          query.fqlQuery(),
          (result, key, value) -> {
            if (!USERS_ENTITY_TYPE_ID.equals(query.entityTypeId())) {
              result.set(key, value); // no-op
              return;
            }

            Optional<Pair<String, String>> namePair = getNamePairs(executionContext.getTenantId())
              .stream()
              .filter(pair -> pair.getLeft().equals(key))
              .findFirst();
            String resultKey = namePair.map(Pair::getRight).orElse(key);
            result.set(resultKey, value);
          }
        )
      )
      .withFields(query
        .fields()
        .stream()
        .map(oldName -> getNamePairs(executionContext.getTenantId())
          .stream()
          .filter(pair -> pair.getLeft().equals(oldName))
          .map(Pair::getRight)
          .findFirst()
          .orElse(oldName)
        ).toList()
      )
      .withHadBreakingChanges(query.hadBreakingChanges() || !warnings.isEmpty())
      .withWarnings(warnings);
  }

  private synchronized List<Pair<String, String>> getNamePairs(String tenantId) {
    return tenantCustomFieldNamePairs.computeIfAbsent(tenantId, id -> {
      try {
        return jooqContext
          .select(field("id"), field(CUSTOM_FIELD_NAME))
          .from(CUSTOM_FIELD_SOURCE_VIEW)
          .where(field(CUSTOM_FIELD_TYPE).in(SUPPORTED_CUSTOM_FIELD_TYPES))
          .fetch()
          .stream()
          .map(row -> {
            String name = "";
            try {
              String idValue = row.get("id", String.class);
              name = row.get(CUSTOM_FIELD_NAME, String.class);
              return Pair.of(name, CUSTOM_FIELD_PREPENDER + idValue);
            } catch (Exception e) {
              log.error("Error processing custom field {} for tenant ID: {}", name, tenantId, e);
              return null;
            }
          })
          .filter(Objects::nonNull)
          .toList();
      } catch (Exception e) {
        log.error("Failed to fetch custom fields for tenant ID: {}", tenantId, e);
        return List.of();
      }
    });
  }
}
