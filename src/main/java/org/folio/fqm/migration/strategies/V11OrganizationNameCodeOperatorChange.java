package org.folio.fqm.migration.strategies;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.service.FqlService;
import org.folio.fqm.client.OrganizationsClient;
import org.folio.fqm.client.OrganizationsClient.Organization;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationUtils;
import org.folio.fqm.migration.warnings.OperatorBreakingWarning;
import org.folio.fqm.migration.warnings.ValueBreakingWarning;
import org.folio.fqm.migration.warnings.Warning;

/**
 * Version 11 -> 12, handles a change in operators for the organization code and name fields.
 *
 * Previously, organization codes/names were considered a plain text field, with $eq/$ne and $regex
 * (backing "contains" and "starts with" functionality). In Ramsons, this was changed to have a
 * value source of the codes/names themselves, using a dropdown in the UI. The values used in the
 * query are now the UUIDs, too, rather than just the plain text.
 *
 * Therefore, we need to (for $eq/$ne) update queries to use the corresponding UUIDs, and will
 * be removing $regex queries (the alternative was to pull all that matched these, but a query
 * such as "contains A" could lead to a _massive_ query beyond reason).
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-606 for the addition of this migration
 */
@Log4j2
@RequiredArgsConstructor
public class V11OrganizationNameCodeOperatorChange implements MigrationStrategy {

  private static final UUID ORGANIZATIONS_ENTITY_TYPE_ID = UUID.fromString("b5ffa2e9-8080-471a-8003-a8c5a1274503");
  private static final List<String> FIELD_NAMES = List.of("code", "name");

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OrganizationsClient organizationsClient;


  @Override
  public String getMaximumApplicableVersion() {
    return "11";
  }

  @Override
  public String getLabel() {
    return "V11 -> V12 Organizations name/code operator/value transformation (MODFQMMGR-606)";
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
    List<Warning> warnings = new ArrayList<>(query.warnings());

    AtomicReference<List<Organization>> records = new AtomicReference<>();

    return query
      .withFqlQuery(
        MigrationUtils.migrateFql(
          query.fqlQuery(),
          (result, key, value) -> {
            if (!ORGANIZATIONS_ENTITY_TYPE_ID.equals(query.entityTypeId()) || !FIELD_NAMES.contains(key)) {
              result.set(key, value); // no-op
              return;
            }

            ObjectNode conditions = (ObjectNode) value;
            ObjectNode transformed = objectMapper.createObjectNode();

            conditions
              .fields()
              .forEachRemaining(entry -> {
                switch (entry.getKey()) {
                  case "$eq", "$ne" -> {
                    // attempt transformation
                    if (records.get() == null) {
                      records.set(organizationsClient.getOrganizations().organizations());

                      log.info("Fetched {} records from API", records.get().size());
                    }

                    records
                      .get()
                      .stream()
                      .filter(org ->
                        // our earlier condition ensures that the key is either "code" or "name",
                        // so no need to check both here.
                        "code".equals(key)
                          ? org.code().equalsIgnoreCase(entry.getValue().textValue())
                          : org.name().equalsIgnoreCase(entry.getValue().textValue())
                      )
                      .findAny()
                      .ifPresentOrElse(
                        org -> transformed.set(entry.getKey(), TextNode.valueOf(org.id())),
                        () ->
                          warnings.add(
                            ValueBreakingWarning
                              .builder()
                              .field(key)
                              .value(entry.getValue().asText())
                              .fql(
                                objectMapper.createObjectNode().set(entry.getKey(), entry.getValue()).toPrettyString()
                              )
                              .build()
                          )
                      );
                  }
                  case "$regex" -> warnings.add(
                    OperatorBreakingWarning
                      .builder()
                      .field(key)
                      .operator(entry.getKey())
                      .fql(objectMapper.createObjectNode().set(entry.getKey(), entry.getValue()).toPrettyString())
                      .build()
                  );
                  case "$empty" -> transformed.set(entry.getKey(), entry.getValue());
                  default -> {
                    log.warn(
                      "Unknown operator {}: {} found for organizations' {} field, not migrating...",
                      entry.getKey(),
                      entry.getValue(),
                      key
                    );
                    transformed.set(entry.getKey(), entry.getValue());
                  }
                }
              });

            if (transformed.size() != 0) {
              result.set(key, transformed);
            }
          }
        )
      )
      .withHadBreakingChanges(query.hadBreakingChanges() || !warnings.isEmpty())
      .withWarnings(warnings);
  }
}
