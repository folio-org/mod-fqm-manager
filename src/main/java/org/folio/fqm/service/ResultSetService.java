package org.folio.fqm.service;

import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.SettingsClient;
import org.folio.fqm.repository.ResultSetRepository;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.i18n.service.TranslationService;
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
  private static final String COUNTRY_TRANSLATION_TEMPLATE = "mod-fqm-manager.countries.%s";
  private static final String DATE_TIME_REGEX = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}([+-]\\d{2}:\\d{2}(:\\d{2})?|Z|)$";
  private final ResultSetRepository resultSetRepository;
  private final EntityTypeFlatteningService entityTypeFlatteningService;
  private final SettingsClient settingsClient;
  private final FolioExecutionContext executionContext;
  private final TranslationService translationService;

  // Reuse mapper; parsing/serializing per record is expensive.
  private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

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
    List<String> countryFields = localize ? getCountryLocalizationFieldPaths(entityType) : List.of();

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
          localizeCountries(copiedContents, countryFields);
        }
        return copiedContents;
      })
      .toList();
  }

  private static List<String> getCountryLocalizationFieldPaths(EntityType entityType) {
    List<String> paths = new ArrayList<>();

    // Find all fields whose configured source is FQM("countries"). For nested fields, this yields paths like "addresses[*]->countryId".
    EntityTypeUtils.runOnEveryField(entityType, (field, parentPath) -> {
      if (!(field instanceof org.folio.querytool.domain.dto.Field f)) {
        return;
      }
      if (f.getSource() == null || f.getSource().getType() != org.folio.querytool.domain.dto.SourceColumn.TypeEnum.FQM) {
        return;
      }
      if (!"countries".equals(f.getSource().getName())) {
        return;
      }

      // Use the underlying JSON property name for the leaf when available (nested fields).
      String leaf = (field instanceof org.folio.querytool.domain.dto.NestedObjectProperty prop
        && prop.getProperty() != null
        && !prop.getProperty().isBlank())
        ? prop.getProperty()
        : f.getName();

      paths.add(parentPath + leaf);
    });

    return paths;
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

  private void localizeCountries(Map<String, Object> contents, List<String> countryFieldPaths) {
    if (countryFieldPaths == null || countryFieldPaths.isEmpty()) {
      return;
    }

    for (String fieldPath : countryFieldPaths) {
      localizeCountryField(contents, fieldPath);
    }
  }

  /**
   * Localizes a single field path that points to a nested element inside a JSON-string field.
   * Currently supports the array-of-objects shape: "<root>[*]-><leaf>", e.g. "addresses[*]->countryId".
   */
  private void localizeCountryField(Map<String, Object> contents, String fieldPath) {
    if (fieldPath == null || fieldPath.isBlank()) {
      return;
    }

    String marker = "[*]->";
    int markerIdx = fieldPath.indexOf(marker);
    if (markerIdx <= 0) {
      return;
    }

    String rootField = fieldPath.substring(0, markerIdx);
    String leafField = fieldPath.substring(markerIdx + marker.length());
    if (rootField.isBlank() || leafField.isBlank()) {
      return;
    }

    Object rootObj = contents.get(rootField);
    if (!(rootObj instanceof String rootJson) || rootJson.isBlank()) {
      return;
    }

    try {
      var node = OBJECT_MAPPER.readTree(rootJson);
      if (!node.isArray()) {
        return;
      }

      boolean changed = false;
      for (com.fasterxml.jackson.databind.JsonNode elementNode : node) {
        if (!elementNode.isObject()) {
          continue;
        }
        var objNode = (com.fasterxml.jackson.databind.node.ObjectNode) elementNode;

        // TODO: check
        var valueNode = objNode.get(leafField);
        if (valueNode == null || !valueNode.isTextual()) {
          continue;
        }

        String code = valueNode.asText();
        if (code == null || code.isBlank()) {
          continue;
        }

        String translationKey = COUNTRY_TRANSLATION_TEMPLATE.formatted(code);
        String translated = translationService.format(translationKey);
        if (translated != null && !translated.isBlank() && !translated.equals(code)) {
          objNode.put(leafField, translated);
          changed = true;
        }
      }

      if (changed) {
        contents.put(rootField, OBJECT_MAPPER.writeValueAsString(node));
      }
    } catch (Exception e) {
      log.debug("Unable to localize country field '{}' (unexpected JSON): {}", fieldPath, e.getMessage());
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
