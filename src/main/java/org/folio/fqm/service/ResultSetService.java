package org.folio.fqm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.ConfigurationClient;
import org.folio.fqm.repository.ResultSetRepository;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.querytool.domain.dto.DateType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.Field;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

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

    // Get list of date fields and zone offset
    List<String> dateFields;
    ZoneOffset zoneOffset;
    if (localize) {
      dateFields = entityType
        .getColumns()
        .stream()
        .filter(col -> col.getDataType() instanceof DateType)
        .map(Field::getName)
        .toList();
      zoneOffset = getZoneOffset();
    } else {
      zoneOffset = null;
      dateFields = List.of();
    }

    boolean doLocalization = localize && !ZoneOffset.UTC.equals(zoneOffset);
    return contentIds
      .stream()
      .map(id -> {
        Map<String, Object> contents = contentsMap.get(id);
        if (contents == null) {
          // Record has been deleted. Populate the idColumns of the record, and add a _deleted key to indicate deletion.
          Map<String, Object> deletedRecordMap = new HashMap<>();
          AtomicInteger columnCount = new AtomicInteger(0);
          deletedRecordMap.put("_deleted", true);
          idColumnNames.forEach(idColumnName -> deletedRecordMap.put(idColumnName, id.get(columnCount.getAndIncrement())));
          return deletedRecordMap;
        }
        // here
        if (doLocalization) {
          for (Map.Entry<String, Object> entry : contents.entrySet()) {
            String contentItemName = entry.getKey();
            if (dateFields.contains(contentItemName)) {
              // Cast should be ok since this is limited to date types
              String contentItem = (String) entry.getValue();
              LocalDateTime currentDate = LocalDateTime.parse(contentItem, DATE_TIME_FORMATTER);
              LocalDateTime adjustedDate = currentDate.plusSeconds(zoneOffset.getTotalSeconds());
              contents.put(contentItemName, adjustedDate.toLocalDate());
            }
          }
        }
        return contents;
      })
      .toList();
  }

  private ZoneOffset getZoneOffset() {
    try {
      String localeSettingsResponse = configurationClient.getLocaleSettings();
      ObjectMapper objectMapper = new ObjectMapper();
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
    } catch (JsonProcessingException | FeignException.Unauthorized | NullPointerException e) {
      log.error("Failed to retrieve locale information from mod-configuration. Defaulting to UTC.");
      return ZoneOffset.UTC;
    }
  }
}
