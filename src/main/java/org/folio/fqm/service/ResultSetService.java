package org.folio.fqm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.fqm.client.LocaleClient;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
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
  private static final String COUNTRY_TRANSLATION_TEMPLATE = "mod-fqm-manager.countries.%s";
  private static final String NESTED_FIELD_MARKER = "[*]->";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final ResultSetRepository resultSetRepository;
  private final EntityTypeFlatteningService entityTypeFlatteningService;
  private final LocaleClient localeClient;
  private final FolioExecutionContext executionContext;
  private final TranslationService translationService;

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
    ZoneId tenantTimezone = localize ? localeClient.getLocaleSettings().getZoneId() : null;
    List<String> countryFields = EntityTypeUtils.getCountryLocalizationFieldPaths(entityType);
    Map<String, Object> defaultValues = EntityTypeUtils.getFieldDefaultValues(entityType);

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
        applyDefaultValues(copiedContents, defaultValues);
        localizeCountries(copiedContents, countryFields);
        if (localize) {
          localizeContent(copiedContents, dateFields, tenantTimezone);
        }
        return copiedContents;
      })
      .toList();
  }

  /**
   * For fields with a default value, applies that default value if the field is missing or null.
   */
  private void applyDefaultValues(Map<String, Object> contents, Map<String, Object> defaultValues) {
    if (defaultValues.isEmpty()) {
      return;
    }
    for (Map.Entry<String, Object> entry : defaultValues.entrySet()) {
      String fieldPath = entry.getKey();
      Object defaultValue = entry.getValue();
      applyDefaultValueToField(contents, fieldPath, defaultValue);
    }
  }

  private void applyDefaultValueToField(Map<String, Object> contents, String fieldPath, Object defaultValue) {
    if (StringUtils.isEmpty(fieldPath)) {
      return;
    }

    int markerIndex = fieldPath.indexOf(NESTED_FIELD_MARKER);
    if (markerIndex < 0) {
      applyDefaultValueToTopLevelField(contents, fieldPath, defaultValue);
      return;
    }

    String rootField = fieldPath.substring(0, markerIndex);
    String nestedField = fieldPath.substring(markerIndex + NESTED_FIELD_MARKER.length());
    processNestedArrayField(contents, rootField, nestedField,
      elementNode -> applyDefaultValueIfNull(elementNode, nestedField, defaultValue),
      "Unable to apply default value to field '{}[*]->{}' (unexpected JSON): {}");
  }

  @SuppressWarnings("java:S3824") // computeIfAbsent doesn't handle null values, only missing keys
  private void applyDefaultValueToTopLevelField(Map<String, Object> contents, String fieldName, Object defaultValue) {
    if (!contents.containsKey(fieldName) || contents.get(fieldName) == null) {
      contents.put(fieldName, defaultValue);
    }
  }

  private boolean applyDefaultValueIfNull(JsonNode elementNode, String fieldName, Object defaultValue) {
    if (!elementNode.isObject()) {
      return false;
    }

    ObjectNode objectNode = (ObjectNode) elementNode;
    JsonNode valueNode = objectNode.get(fieldName);

    // Apply default if field is missing or null
    if (valueNode == null || valueNode.isNull()) {
      objectNode.putPOJO(fieldName, defaultValue);
      return true;
    }

    return false;
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
    if (CollectionUtils.isEmpty(countryFieldPaths)) {
      return;
    }
    for (String fieldPath : countryFieldPaths) {
      localizeCountryField(contents, fieldPath);
    }
  }

  private void localizeCountryField(Map<String, Object> contents, String fieldPath) {
    if (StringUtils.isEmpty(fieldPath)) {
      return;
    }

    int markerIndex = fieldPath.indexOf(NESTED_FIELD_MARKER);
    if (markerIndex < 0) {
      localizeTopLevelCountryField(contents, fieldPath);
      return;
    }

    String rootField = fieldPath.substring(0, markerIndex);
    String nestedField = fieldPath.substring(markerIndex + NESTED_FIELD_MARKER.length());
    processNestedArrayField(contents, rootField, nestedField,
      elementNode -> localizeCountryCodeIfPresent(elementNode, nestedField),
      "Unable to localize country field '{}[*]->{}' (unexpected JSON): {}");
  }

  private void localizeTopLevelCountryField(Map<String, Object> contents, String fieldName) {
    Object value = contents.get(fieldName);
    if (!(value instanceof String code) || code.isBlank()) {
      return;
    }
    localizeCountryCode(code).ifPresent(translated -> contents.put(fieldName, translated));
  }

  /**
   * Generic method to process nested fields within JSON arrays.
   * Parses the root field as a JSON array, applies a transformation function to each element,
   * and updates the contents if any changes were made.
   *
   * @param contents The map containing the field values
   * @param rootField The name of the root field containing the JSON array
   * @param nestedField The name of the nested field within each array element
   * @param transformer Predicate that transforms an array element and returns true if modified
   * @param errorMessageTemplate Template for logging errors (with placeholders for rootField, nestedField, and error message)
   */
  private void processNestedArrayField(Map<String, Object> contents, String rootField, String nestedField,
                                       Predicate<JsonNode> transformer, String errorMessageTemplate) {
    if (rootField.isBlank() || nestedField.isBlank()) {
      return;
    }

    Object root = contents.get(rootField);
    if (!(root instanceof String rootJson) || rootJson.isBlank()) {
      return;
    }

    try {
      JsonNode node = OBJECT_MAPPER.readTree(rootJson);
      if (!node.isArray()) {
        return;
      }

      boolean changed = false;
      for (JsonNode elementNode : node) {
        changed |= transformer.test(elementNode);
      }
      if (changed) {
        contents.put(rootField, OBJECT_MAPPER.writeValueAsString(node));
      }
    } catch (Exception e) {
      log.debug(errorMessageTemplate, rootField, nestedField, e.getMessage());
    }
  }

  private boolean localizeCountryCodeIfPresent(JsonNode elementNode, String fieldName) {
    if (!elementNode.isObject()) {
      return false;
    }

    ObjectNode objectNode = (ObjectNode) elementNode;
    JsonNode valueNode = objectNode.get(fieldName);
    if (valueNode == null || !valueNode.isTextual()) {
      return false;
    }

    String code = valueNode.asText();
    Optional<String> localized = localizeCountryCode(code);
    if (localized.isEmpty()) {
      return false;
    }

    objectNode.put(fieldName, localized.get());
    return true;
  }

  private Optional<String> localizeCountryCode(String code) {
    String translationKey = COUNTRY_TRANSLATION_TEMPLATE.formatted(code);
    String localized = translationService.format(translationKey);

    // If translation is missing, don't modify the original value
    if (localized == null || localized.isBlank() || localized.equals(translationKey)) {
      return Optional.empty();
    }
    return Optional.of(localized);
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
