package org.folio.fqm.service;

import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.SettingsClient;
import org.folio.fqm.repository.ResultSetRepository;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Log4j2
public class ResultSetService {

  private static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    .optionalStart().appendOffsetId() // optional Z/timezone at end
    .toFormatter().withZone(ZoneOffset.UTC); // force interpretation as UTC
  private static final String DATE_TIME_REGEX = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}([+-]\\d{2}:\\d{2}(:\\d{2})?|Z|)$";
  private final ResultSetRepository resultSetRepository;
  private final EntityTypeFlatteningService entityTypeFlatteningService;
  private final SettingsClient settingsClient;
  private final FolioExecutionContext executionContext;

  public List<Map<String, Object>> getResultSet(UUID entityTypeId,
                                                List<String> fields,
                                                List<List<String>> ids, List<String> tenantsToQuery, boolean localize) {
    List<Map<String, Object>> unsortedResults = resultSetRepository.getResultSet(entityTypeId, fields, ids, tenantsToQuery);

    // Sort the contents in Java code as sorting in DB views run very slow intermittently
    return getSortedContents(entityTypeId, ids, unsortedResults, localize);
  }

  private List<Map<String, Object>> getSortedContents(UUID entityTypeId, List<List<String>> contentIds, List<Map<String, Object>> unsortedResults, boolean localize) {
    EntityType entityType = entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, executionContext.getTenantId(), true);
    List<String> idColumnNames = EntityTypeUtils.getIdColumnNames(entityType);
    Map<List<String>, Map<String, Object>> contentsMap = unsortedResults.stream()
      .collect(Collectors.toMap(content -> {
            List<String> keys = new ArrayList<>();
            idColumnNames.forEach(columnName -> keys.add((content.containsKey(columnName) && content.get(columnName) != null) ? content.get(columnName).toString() : null));
            return keys;
          },
          Function.identity())
      );

    List<String> dateFields = localize ? EntityTypeUtils.getDateTimeFields(entityType) : List.of();
    ZoneId tenantTimezone = localize ? settingsClient.getTenantTimezone() : null;

    return contentIds
      .stream()
      .map(id -> {
        var contents = contentsMap.get(id);
        if (contents == null) {
          // Record has been deleted. Populate the idColumns of the record, and add a _deleted key to indicate deletion.
          Map<String, Object> deletedRecordMap = new HashMap<>();
          AtomicInteger columnCount = new AtomicInteger(0);
          deletedRecordMap.put("_deleted", true);
          idColumnNames.forEach(idColumnName -> deletedRecordMap.put(idColumnName, id.get(columnCount.getAndIncrement())));
          return deletedRecordMap;
        }

        Map<String, Object> copiedContents = new HashMap<>(contents);
        if (localize) {
          localizeContent(copiedContents, dateFields, tenantTimezone);
        }
        return copiedContents;
      })
      .toList();
  }

  private void localizeContent(Map<String, Object> contents, List<String> dateFields, ZoneId tenantTimezone) {
    for (String fieldName : dateFields) {
      contents.computeIfPresent(fieldName, (key, value) -> {
        if (value instanceof Timestamp ts) {
          return adjustDate(ts.toInstant(), tenantTimezone);
        } else if (value instanceof String s) {
          return parseAndAdjustDate(s, tenantTimezone);
        }
        return value;
      });
    }
  }

  private static String adjustDate(Instant instant, ZoneId tenantTimezone) {
    return instant.atZone(tenantTimezone).toLocalDate().toString();
  }

  private static String parseAndAdjustDate(String value, ZoneId tenantTimezone) {
    if (value.matches(DATE_TIME_REGEX)) {
      return adjustDate(Instant.from(DATE_TIME_FORMATTER.parse(value)), tenantTimezone);
    }

    log.warn("Database date value is in an unrecognized format: \"{}\"", value);
    return value;
  }
}
