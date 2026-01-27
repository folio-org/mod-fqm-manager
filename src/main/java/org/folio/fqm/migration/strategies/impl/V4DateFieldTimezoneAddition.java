package org.folio.fqm.migration.strategies.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.SettingsClient;
import org.folio.fqm.migration.MigrationUtils;
import org.folio.fqm.migration.strategies.AbstractRegularMigrationStrategy;
import org.folio.fqm.migration.types.MigratableFqlFieldAndCondition;
import org.folio.fqm.migration.types.MigrationResult;
import org.folio.fqm.migration.types.SingleFieldMigrationResult;
import org.folio.fqm.service.FqlToSqlConverterService;

/**
 * Version 4 -> 5, handles addition of time component to date queries. These are not required for the query to run,
 * however, the query builder now expects/requires this form. Additionally, we need to calculate the "midnight" for
 * these dates, to conform them to the tenant timezone (previously we always used UTC).
 *
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-594 for the addition of this migration script
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-466 for the original support
 * @see https://folio-org.atlassian.net/browse/UIPQB-126 for the query builder addition
 * @see https://folio-org.atlassian.net/browse/MODFQMMGR-573 for the addition of tenant TZ logic in FQM
 */
@Log4j2
@RequiredArgsConstructor
public class V4DateFieldTimezoneAddition extends AbstractRegularMigrationStrategy<AtomicReference<ZoneId>> {

  @Override
  public String getMaximumApplicableVersion() {
    return "4";
  }

  // must snapshot this point in time, as the entity types stored within may change past this migration
  // these names are unique enough that we don't need a per-ET listing
  private static final Set<String> DATE_FIELDS = Set.of(
    "assigned_to_user.user_created_date",
    "assigned_to_user.user_updated_date",
    "changelogs[*]->timestamp",
    "created_at",
    "edi_job_scheduling_date",
    "holdings.created_at",
    "holdings.updated_at",
    "instance.cataloged_date",
    "instance.created_at",
    "instance.updated_at",
    "instances.cataloged_date",
    "instances.created_at",
    "instances.updated_at",
    "items.circulation_notes[*]->date",
    "items.created_date",
    "items.last_check_in_date_time",
    "items.updated_date",
    "loans.checkout_date",
    "loans.claimed_returned_date",
    "loans.created_at",
    "loans.declared_lost_date",
    "loans.due_date",
    "loans.return_date",
    "po_created_by_user.user_created_date",
    "po_created_by_user.user_updated_date",
    "po_updated_by_user.user_created_date",
    "po_updated_by_user.user_updated_date",
    "po.created_at",
    "po.updated_at",
    "pol_created_by_user.user_created_date",
    "pol_created_by_user.user_updated_date",
    "pol_updated_by_user.user_created_date",
    "pol_updated_by_user.user_updated_date",
    "pol.created_at",
    "pol.eresource_expected_activation",
    "pol.physical_expected_receipt_date",
    "pol.receipt_date",
    "pol.updated_at",
    "updated_at",
    "users.created_date",
    "users.date_of_birth",
    "users.enrollment_date",
    "users.expiration_date",
    "users.updated_date",
    "users.user_created_date",
    "users.user_updated_date"
  );

  private final SettingsClient settingsClient;

  @Override
  public String getLabel() {
    return "V4 -> V5 Date field time addition (MODFQMMGR-594)";
  }

  @Override
  public AtomicReference<ZoneId> getStartingState() {
    return new AtomicReference<>();
  }

  @Override
  public SingleFieldMigrationResult<MigratableFqlFieldAndCondition> migrateFql(
    AtomicReference<ZoneId> state,
    MigratableFqlFieldAndCondition cond
  ) {
    return MigrationUtils
      .migrateFqlValues(
        condition -> DATE_FIELDS.contains(condition.field()),
        (MigratableFqlFieldAndCondition condition, String value, Supplier<String> fql) -> {
          // no-op, we already have a time component
          if (value.contains("T")) {
            return MigrationResult.withResult(value);
          }

          if (state.get() == null) {
            state.set(settingsClient.getTenantTimezone());
          }

          try {
            return MigrationResult.withResult(
              FqlToSqlConverterService.DATE_TIME_FORMATTER.format(LocalDate.parse(value).atStartOfDay(state.get()))
            );
          } catch (DateTimeParseException e) {
            log.warn("Could not migrate date {}", value, e);
            return MigrationResult.withResult(value);
          }
        }
      )
      .apply(cond);
  }
}
