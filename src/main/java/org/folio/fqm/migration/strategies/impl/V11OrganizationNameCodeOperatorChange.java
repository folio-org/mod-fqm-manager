package org.folio.fqm.migration.strategies.impl;

import com.fasterxml.jackson.databind.node.TextNode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.OrganizationsClient;
import org.folio.fqm.client.OrganizationsClient.Organization;
import org.folio.fqm.migration.strategies.AbstractRegularMigrationStrategy;
import org.folio.fqm.migration.types.MigratableFqlFieldAndCondition;
import org.folio.fqm.migration.types.SingleFieldMigrationResult;
import org.folio.fqm.migration.warnings.OperatorBreakingWarning;
import org.folio.fqm.migration.warnings.ValueBreakingWarning;

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
public class V11OrganizationNameCodeOperatorChange
  extends AbstractRegularMigrationStrategy<AtomicReference<List<Organization>>> {

  private static final UUID ORGANIZATIONS_ENTITY_TYPE_ID = UUID.fromString("b5ffa2e9-8080-471a-8003-a8c5a1274503");
  private static final List<String> FIELD_NAMES = List.of("code", "name");

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
  public AtomicReference<List<Organization>> getStartingState() {
    return new AtomicReference<>();
  }

  @Override
  public SingleFieldMigrationResult<MigratableFqlFieldAndCondition> migrateFql(
    AtomicReference<List<Organization>> state,
    MigratableFqlFieldAndCondition condition
  ) {
    if (!ORGANIZATIONS_ENTITY_TYPE_ID.equals(condition.entityTypeId()) || !FIELD_NAMES.contains(condition.field())) {
      return SingleFieldMigrationResult.noop(condition);
    }

    return switch (condition.operator()) {
      case "$eq", "$ne" -> {
        // attempt transformation
        if (state.get() == null) {
          state.set(organizationsClient.getOrganizations().organizations());

          log.info("Fetched {} records from API", state.get().size());
        }

        yield state
          .get()
          .stream()
          .filter(org ->
            // our earlier condition ensures that the field is either "code" or "name",
            // so no need to check both here.
            "code".equals(condition.field())
              ? org.code().equalsIgnoreCase(condition.value().textValue())
              : org.name().equalsIgnoreCase(condition.value().textValue())
          )
          .findAny()
          .map(org -> SingleFieldMigrationResult.withField(condition.withValue(TextNode.valueOf(org.id()))))
          .orElseGet(() ->
            SingleFieldMigrationResult
              .<MigratableFqlFieldAndCondition>removed()
              .withWarnings(
                List.of(
                  ValueBreakingWarning
                    .builder()
                    .field(condition.getFullField())
                    .value(condition.value().asText())
                    .fql(condition.getConditionObject().toPrettyString())
                    .build()
                )
              )
              .withHadBreakingChange(true)
          );
      }
      case "$regex" -> SingleFieldMigrationResult
        .<MigratableFqlFieldAndCondition>removed()
        .withWarnings(
          List.of(
            OperatorBreakingWarning
              .builder()
              .field(condition.getFullField())
              .operator(condition.operator())
              .fql(condition.getConditionObject().toPrettyString())
              .build()
          )
        );
      case "$empty" -> SingleFieldMigrationResult.noop(condition);
      default -> {
        log.warn(
          "Unknown operator {}: {} found for organizations' {} field, not migrating...",
          condition.operator(),
          condition.value(),
          condition.getFullField()
        );
        yield SingleFieldMigrationResult.noop(condition);
      }
    };
  }
}
