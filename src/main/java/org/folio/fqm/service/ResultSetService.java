package org.folio.fqm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.ConfigurationClient;
import org.folio.fqm.repository.ResultSetRepository;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.querytool.domain.dto.EntityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
  private static final DateTimeFormatter DATE_TIME_OFFSET_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'+00:00'");
  private static final String DATE_TIME_REGEX = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$";
  private static final String DATE_TIME_OFFSET_REGEX = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{2}:\\d{2}$";
  private final ResultSetRepository resultSetRepository;
  private final EntityTypeFlatteningService entityTypeFlatteningService;
  private final ConfigurationClient configurationClient;

  public List<Map<String, Object>> getResultSet(UUID entityTypeId,
                                                List<String> fields,
                                                List<List<String>> ids, List<String> tenantsToQuery, boolean localize) {
    List<Map<String, Object>> unsortedResults = resultSetRepository.getResultSet(entityTypeId, fields, ids, tenantsToQuery);

    // Sort the contents in Java code as sorting in DB views run very slow intermittently
    return getSortedContents(entityTypeId, ids, unsortedResults, localize);
  }

  private List<Map<String, Object>> getSortedContents(UUID entityTypeId, List<List<String>> contentIds, List<Map<String, Object>> unsortedResults, boolean localize) {
    EntityType entityType = entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null);
    List<String> idColumnNames = EntityTypeUtils.getIdColumnNames(entityType);
    Map<List<String>, Map<String, Object>> contentsMap = unsortedResults.stream()
      .collect(Collectors.toMap(content -> {
            List<String> keys = new ArrayList<>();
            idColumnNames.forEach(columnName -> keys.add(content.containsKey(columnName) ? content.get(columnName).toString() : "NULL"));
            return keys;
          },
          Function.identity())
      );

    List<String> dateFields = localize ? EntityTypeUtils.getDateFields(entityType) : List.of();
    ZoneOffset zoneOffset = localize ? getZoneOffset() : null;

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
          localizeContent(copiedContents, dateFields, zoneOffset);
        }
        return copiedContents;
      })
      .toList();
  }

  private void localizeContent(Map<String, Object> contents, List<String> dateFields, ZoneOffset zoneOffset) {
    for (String fieldName : dateFields) {
      contents.computeIfPresent(fieldName, (key, value) -> {
        if (value instanceof Timestamp ts) {
          return adjustDate(ts.toLocalDateTime(), zoneOffset);
        } else if (value instanceof String s) {
          return parseAndAdjustDate(s, zoneOffset);
        }
        return value;
      });
    }
  }

  private static String adjustDate(LocalDateTime dateTime, ZoneOffset zoneOffset) {
    return dateTime.plusSeconds(zoneOffset.getTotalSeconds()).toLocalDate().toString();
  }

  private static String parseAndAdjustDate(String contentItem, ZoneOffset zoneOffset) {
    LocalDateTime dateTime = null;
    if (contentItem.matches(DATE_TIME_REGEX)) {
      dateTime = LocalDateTime.parse(contentItem, DATE_TIME_FORMATTER);
    } else if (contentItem.matches(DATE_TIME_OFFSET_REGEX)) {
      dateTime = LocalDateTime.parse(contentItem, DATE_TIME_OFFSET_FORMATTER);
    }
    return dateTime != null ? adjustDate(dateTime, zoneOffset) : contentItem;
  }

  private ZoneOffset getZoneOffset() {
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      String localeSettingsResponse = configurationClient.getLocaleSettings();
      JsonNode localeSettingsNode = objectMapper.readTree(localeSettingsResponse);
      String valueString = localeSettingsNode
        .path("configs")
        .get(0)
        .path("value")
        .asText();
      JsonNode valueNode = objectMapper.readTree(valueString);
      ZoneId zoneId = ZoneId.of(valueNode.path("timezone").asText());
      ZonedDateTime zonedDateTime = ZonedDateTime.now(zoneId);
      return zonedDateTime.getOffset();
    } catch (JsonProcessingException | FeignException.Unauthorized | FeignException.NotFound | NullPointerException e) {
      log.error("Failed to retrieve locale information from mod-configuration. Defaulting to UTC.");
      return ZoneOffset.UTC;
    }
  }
}
