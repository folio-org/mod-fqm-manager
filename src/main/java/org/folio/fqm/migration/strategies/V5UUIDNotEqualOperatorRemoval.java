package org.folio.fqm.migration.strategies;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.service.FqlService;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationUtils;
import org.folio.fqm.migration.warnings.OperatorBreakingWarning;
import org.folio.fqm.migration.warnings.Warning;

/**
 * Version 5 -> 6, handles removal of the not equal operator for UUID fields.
 *
 * @see https://folio-org.atlassian.net/browse/UIPQB-144 for the operator removal
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-599 for the addition of this migration
 */
@Log4j2
@SuppressWarnings({ "java:S115", "java:S1192" })
public class V5UUIDNotEqualOperatorRemoval implements MigrationStrategy {

  private static final String $NE = "$ne";

  @Override
  public String getMaximumApplicableVersion() {
    return "5";
  }

  // must snapshot this point in time, as the entity types stored within may change past this migration
  private static final Map<UUID, Set<String>> UUID_FIELDS = Map.ofEntries(
    Map.entry(
      UUID.fromString("8418e512-feac-4a6a-a56d-9006aab31e33"),
      Set.of(
        "effective_library.campus_id",
        "effective_library.id",
        "effective_location.campus_id",
        "effective_location.institution_id",
        "effective_location.primary_service_point",
        "effective_location.id",
        "holdings.created_by",
        "holdings.effective_location_id",
        "holdings.holdings_type_id",
        "holdings.instance_id",
        "holdings.permanent_location_id",
        "holdings.source_id",
        "holdings.temporary_location_id",
        "holdings.updated_by",
        "holdings.id",
        "permanent_location.campus_id",
        "permanent_location.institution_id",
        "permanent_location.primary_service_point",
        "permanent_location.id",
        "temporary_location.campus_id",
        "temporary_location.institution_id",
        "temporary_location.primary_service_point",
        "temporary_location.id"
      )
    ),
    Map.entry(
      UUID.fromString("6b08439b-4f8e-4468-8046-ea620f5cfb74"),
      Set.of(
        "date_type.id",
        "inst_stat.id",
        "instance.classifications[*]->type_name",
        "instance.created_by",
        "instance.id",
        "instance.updated_by"
      )
    ),
    Map.entry(
      UUID.fromString("d0213d22-32cf-490f-9196-d81c3c66e53f"),
      Set.of(
        "effective_call_number.id",
        "loclibrary.campus_id",
        "loclibrary.id",
        "effective_location.campus_id",
        "effective_location.institution_id",
        "effective_location.primary_service_point",
        "effective_location.id",
        "holdings.created_by",
        "holdings.effective_location_id",
        "holdings.holdings_type_id",
        "holdings.instance_id",
        "holdings.permanent_location_id",
        "holdings.source_id",
        "holdings.temporary_location_id",
        "holdings.updated_by",
        "holdings.id",
        "instances.classifications[*]->type_name",
        "instances.created_by",
        "instances.id",
        "instances.updated_by",
        "item_level_call_number.id",
        "items.effective_location_id",
        "items.holdings_record_id",
        "items.item_level_call_number_type_id",
        "items.id",
        "items.last_check_in_service_point_id",
        "items.last_check_in_staff_member_id",
        "items.material_type_id",
        "items.permanent_loan_type_id",
        "items.permanent_location_id",
        "mtypes.id",
        "permanent_location.campus_id",
        "permanent_location.institution_id",
        "permanent_location.primary_service_point",
        "permanent_location.id",
        "temporary_location.campus_id",
        "temporary_location.institution_id",
        "temporary_location.primary_service_point",
        "temporary_location.id"
      )
    ),
    Map.entry(
      UUID.fromString("d6729885-f2fb-4dc7-b7d0-a865a7f461e4"),
      Set.of(
        "cispi.id",
        "cospi.id",
        "holdings.created_by",
        "holdings.effective_location_id",
        "holdings.holdings_type_id",
        "holdings.instance_id",
        "holdings.permanent_location_id",
        "holdings.source_id",
        "holdings.temporary_location_id",
        "holdings.updated_by",
        "holdings.id",
        "instance.classifications[*]->type_name",
        "instance.created_by",
        "instance.id",
        "instance.mode_of_issuance_id",
        "instance.instance_type_id",
        "instance.updated_by",
        "items.effective_location_id",
        "items.holdings_record_id",
        "items.item_level_call_number_type_id",
        "items.id",
        "items.last_check_in_service_point_id",
        "items.last_check_in_staff_member_id",
        "items.material_type_id",
        "items.permanent_loan_type_id",
        "items.permanent_location_id",
        "lpolicy.id",
        "loans.checkin_service_point_id",
        "loans.checkout_service_point_id",
        "loans.item_id",
        "loans.loan_policy_id",
        "loans.proxy_user_id",
        "loans.user_id",
        "loans.id",
        "mtypes.id",
        "groups.id",
        "users.id"
      )
    ),
    Map.entry(UUID.fromString("b5ffa2e9-8080-471a-8003-a8c5a1274503"), Set.of("created_by", "updated_by", "id")),
    Map.entry(
      UUID.fromString("abc777d3-2a45-43e6-82cb-71e8c96d13d2"),
      Set.of(
        "assigned_to_user.id",
        "po_created_by_user.id",
        "po_updated_by_user.id",
        "po.assigned_to",
        "po.created_by",
        "po.updated_by",
        "po.id",
        "pol_created_by_user.id",
        "pol_updated_by_user.id",
        "pol.agreement_id",
        "pol.created_by",
        "pol.fund_distribution[*]->encumbrance",
        "pol.fund_distribution[*]->fund_id",
        "pol.fund_distribution[*]->expense_class_id",
        "pol.updated_by",
        "pol.id",
        "vendor_organization.id"
      )
    ),
    Map.entry(
      UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"),
      Set.of("groups.id", "users.created_by_user_id", "users.updated_by_user_id", "users.id")
    )
  );

  @Override
  public String getLabel() {
    return "V5 -> V6 UUID $ne operator removal (MODFQMMGR-599)";
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
    Set<String> fieldsToTarget = UUID_FIELDS.getOrDefault(query.entityTypeId(), Set.of());

    List<Warning> warnings = new ArrayList<>(query.warnings());

    return query
      .withFqlQuery(
        MigrationUtils.migrateFql(
          query.fqlQuery(),
          (result, key, value) -> {
            if (!fieldsToTarget.contains(key)) {
              result.set(key, value); // no-op
              return;
            }

            ObjectNode conditions = (ObjectNode) value;

            if (conditions.has($NE)) {
              warnings.add(
                OperatorBreakingWarning
                  .builder()
                  .field(key)
                  .operator($NE)
                  // everything else is left behind
                  .fql(new ObjectMapper().valueToTree(Map.of($NE, conditions.get($NE))).toPrettyString())
                  .build()
              );
              conditions.remove($NE);
            }

            if (conditions.size() != 0) {
              result.set(key, conditions);
            }
          }
        )
      )
      .withWarnings(warnings)
      .withHadBreakingChanges(query.hadBreakingChanges() || !warnings.isEmpty());
  }
}
