package org.folio.fqm.migration.strategies;

import com.fasterxml.jackson.databind.node.TextNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.service.FqlService;
import org.folio.fqm.client.ConfigurationClient;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationUtils;

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
public class V4DateFieldTimezones implements MigrationStrategy {

  public static final String SOURCE_VERSION = "4";
  public static final String TARGET_VERSION = "5";

  // must snapshot this point in time, as the entity types stored within may change past this migration
  private static final Map<UUID, Set<String>> DATE_FIELDS = Map.ofEntries(
    Map.entry(
      UUID.fromString("8418e512-feac-4a6a-a56d-9006aab31e33"),
      Set.of("holdings.created_at", "holdings.updated_at")
    ),
    Map.entry(
      UUID.fromString("6b08439b-4f8e-4468-8046-ea620f5cfb74"),
      Set.of("instance.cataloged_date", "instance.created_at", "instance.updated_at")
    ),
    Map.entry(
      UUID.fromString("d0213d22-32cf-490f-9196-d81c3c66e53f"),
      Set.of(
        "holdings.created_at",
        "holdings.updated_at",
        "instances.cataloged_date",
        "instances.created_at",
        "instances.updated_at",
        "items.circulation_notes[*]->date",
        "items.created_date",
        "items.last_check_in_date_time",
        "items.updated_date"
      )
    ),
    Map.entry(
      UUID.fromString("d6729885-f2fb-4dc7-b7d0-a865a7f461e4"),
      Set.of(
        "holdings.created_at",
        "holdings.updated_at",
        "instance.cataloged_date",
        "instance.created_at",
        "instance.updated_at",
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
        "users.expiration_date"
      )
    ),
    Map.entry(
      UUID.fromString("b5ffa2e9-8080-471a-8003-a8c5a1274503"),
      Set.of("changelogs[*]->timestamp", "created_at", "edi_job_scheduling_date", "updated_at")
    ),
    Map.entry(
      UUID.fromString("abc777d3-2a45-43e6-82cb-71e8c96d13d2"),
      Set.of(
        "assigned_to_user.user_created_date",
        "assigned_to_user.user_updated_date",
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
        "pol.updated_at"
      )
    ),
    Map.entry(
      UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"),
      Set.of(
        "users.created_date",
        "users.date_of_birth",
        "users.enrollment_date",
        "users.expiration_date",
        "users.updated_date",
        "users.user_created_date",
        "users.user_updated_date"
      )
    )
  );

  private final ConfigurationClient configurationClient;

  @Override
  public String getLabel() {
    return "V4 -> V5 Date field time addition (MODFQMMGR-594)";
  }

  @Override
  public boolean applies(String version) {
    return SOURCE_VERSION.equals(version);
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation query) {
    Set<String> fieldsToMigrate = DATE_FIELDS.getOrDefault(query.entityTypeId(), Set.of());

    // avoid initializing this if we don't actually need it
    AtomicReference<ZoneId> timezone = new AtomicReference<>();

    return query.withFqlQuery(
      MigrationUtils.migrateFql(
        query.fqlQuery(),
        _v -> TARGET_VERSION,
        (result, key, value) -> {
          if (
            !fieldsToMigrate.contains(key) || // not a date field
            value.textValue().contains("T") // already has time component
          ) {
            result.set(key, value); // no-op
            return;
          }

          if (timezone.get() == null) {
            timezone.set(configurationClient.getTenantTimezone());
          }

          try {
            result.set(
              key,
              new TextNode(LocalDate.parse(value.textValue()).atStartOfDay(timezone.get()).toInstant().toString())
            );
          } catch (DateTimeParseException e) {
            log.warn("Could not migrate date {}", value, e);
            result.set(key, value); // no-op
          }
        }
      )
    );
  }
}
