package org.folio.fqm.service;

import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.folio.spring.i18n.service.TranslationService;

final class LanguageLocalizationUtils {

  static final String LANGUAGE_DISAMBIGUATION_TEMPLATE = "mod-fqm-manager.languages.disambiguated";
  private static final String LANGUAGES_FILEPATH = "languages.json5";
  private static final LanguageMetadata LANGUAGE_METADATA = loadLanguageMetadata();

  private LanguageLocalizationUtils() {}

  static List<ValueWithLabel> getLanguageValues(Set<String> codes, Locale folioLocale, TranslationService translationService) {
    Map<String, String> displayMap = getLanguageDisplayMap(codes, folioLocale, translationService);

    return codes.stream()
      .filter(StringUtils::isNotEmpty)
      .map(code -> new ValueWithLabel().value(code).label(displayMap.getOrDefault(code, code)))
      .toList();
  }

  static Map<String, String> getLanguageDisplayMap(Iterable<String> codes, Locale folioLocale, TranslationService translationService) {
    List<LocalizedLanguageValue> localizedValues = toLocalizedLanguageValues(codes, folioLocale);

    Map<String, Long> distinctRawValueCountsByLabel = localizedValues.stream()
      .filter(item -> item.localizedValue() != null)
      .map(LocalizedLanguageValue::localizedValue)
      .distinct()
      .collect(Collectors.toMap(
        label -> label,
        label -> localizedValues.stream()
          .filter(other -> label.equals(other.localizedValue()))
          .map(LocalizedLanguageValue::rawValue)
          .filter(Objects::nonNull)
          .distinct()
          .count()
      ));

    return localizedValues.stream()
      .collect(Collectors.toMap(
        LocalizedLanguageValue::rawValue,
        item -> {
          long distinctCount = distinctRawValueCountsByLabel.getOrDefault(item.localizedValue(), 0L);
          if (distinctCount > 1) {
            return translationService.format(
              LANGUAGE_DISAMBIGUATION_TEMPLATE,
              "label", item.localizedValue(),
              "code", item.rawValue()
            );
          }
          return item.localizedValue();
        }
      ));
  }

  static String localizeLanguageCode(String code, Locale folioLocale) {
    String a2Code = LANGUAGE_METADATA.codeToA2Map().get(code);
    String name = LANGUAGE_METADATA.codeToNameMap().get(code);
    if (StringUtils.isNotEmpty(a2Code)) {
      Locale languageLocale = Locale.of(a2Code);
      String label = languageLocale.getDisplayLanguage(folioLocale);
      if (StringUtils.isNotEmpty(label) && !"Undetermined".equals(label)) {
        return label;
      }
    }
    if (StringUtils.isNotEmpty(name) && !"Undetermined".equals(name)) {
      return name;
    }
    return code;
  }

  private static List<LocalizedLanguageValue> toLocalizedLanguageValues(Iterable<String> codes, Locale folioLocale) {
    return toDistinctNonEmptyCodes(codes).stream()
      .map(code -> new LocalizedLanguageValue(code, localizeLanguageCode(code, folioLocale)))
      .toList();
  }

  private static List<String> toDistinctNonEmptyCodes(Iterable<String> codes) {
    return codes == null ? List.of() : org.apache.commons.collections4.IterableUtils.toList(codes).stream()
      .filter(StringUtils::isNotEmpty)
      .distinct()
      .toList();
  }

  private static LanguageMetadata loadLanguageMetadata() {
    ObjectMapper mapper = JsonMapper
      .builder()
      .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
      .enable(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES)
      .build();

    try (InputStream input = LanguageLocalizationUtils.class.getClassLoader().getResourceAsStream(LANGUAGES_FILEPATH)) {
      return loadLanguageMetadata(input, mapper);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load language metadata", e);
    }
  }

  static LanguageMetadata loadLanguageMetadata(InputStream input, ObjectMapper mapper) {
    try {
      List<Map<String, String>> languages = mapper.readValue(input, new TypeReference<>() {});
      Map<String, String> codeToNameMap = new HashMap<>();
      Map<String, String> codeToA2Map = new HashMap<>();

      for (Map<String, String> language : languages) {
        String alpha3 = language.get("alpha3");
        String alpha2 = language.get("alpha2");
        String name = language.get("name");

        codeToA2Map.put(alpha3, alpha2);
        codeToNameMap.put(alpha3, name);

        if (StringUtils.isNotEmpty(alpha2)) {
          codeToA2Map.put(alpha2, alpha2);
          codeToNameMap.put(alpha2, name);
        }
      }
      return new LanguageMetadata(codeToNameMap, codeToA2Map);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load language metadata", e);
    }
  }

  static record LanguageMetadata(Map<String, String> codeToNameMap, Map<String, String> codeToA2Map) {}

  private record LocalizedLanguageValue(String rawValue, String localizedValue) {}
}
