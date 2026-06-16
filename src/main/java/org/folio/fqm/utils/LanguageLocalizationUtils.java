package org.folio.fqm.utils;

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

import static org.apache.commons.collections4.IterableUtils.toList;

public final class LanguageLocalizationUtils {

  public static final String LANGUAGE_DISAMBIGUATION_TEMPLATE = "mod-fqm-manager.languages.disambiguated";
  public static final String LANGUAGE_TRANSLATION_TEMPLATE = "mod-fqm-manager.languages.%s";
  private static final String LANGUAGES_FILEPATH = "languages.json5";
  private static final LanguageMetadata LANGUAGE_METADATA = loadLanguageMetadata();

  private LanguageLocalizationUtils() {
  }

  public static List<ValueWithLabel> getLanguageValues(Set<String> codes, Locale folioLocale, TranslationService translationService) {
    Map<String, String> displayMap = getLanguageDisplayMap(codes, folioLocale, translationService);

    return codes.stream()
      .filter(StringUtils::isNotEmpty)
      .map(code -> new ValueWithLabel().value(code).label(displayMap.getOrDefault(code, code)))
      .toList();
  }

  public static Map<String, String> getLanguageDisplayMap(Iterable<String> codes, Locale folioLocale, TranslationService translationService) {
    List<LocalizedLanguageValue> localizedValues = getLocalizedLanguageNames(codes, folioLocale, translationService);

    // Count how many distinct raw codes collapse to each localized label so we only append
    // the code for labels that would otherwise be ambiguous, like "German" for both "de" and "ger".
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

  public static String localizeLanguageCode(String code, Locale folioLocale) {
    return localizeLanguageCode(code, folioLocale, null);
  }

  public static String localizeLanguageCode(String code, Locale folioLocale, TranslationService translationService) {
    String translation = getLanguageTranslation(code, translationService);
    if (StringUtils.isNotEmpty(translation)) {
      return translation;
    }

    String a2Code = LANGUAGE_METADATA.codeToA2Map().get(code);
    String name = LANGUAGE_METADATA.codeToNameMap().get(code);
    if (StringUtils.isNotEmpty(name)) {
      return name;
    }
    if (StringUtils.isNotEmpty(a2Code)) {
      Locale languageLocale = Locale.of(a2Code);
      String label = languageLocale.getDisplayLanguage(folioLocale);
      if (StringUtils.isNotEmpty(label)) {
        return label;
      }
    }
    return code;
  }

  private static String getLanguageTranslation(String code, TranslationService translationService) {
    if (translationService == null) {
      return null;
    }
    String canonicalCode = LANGUAGE_METADATA.codeToCanonicalCodeMap().get(code);
    if (StringUtils.isEmpty(canonicalCode)) {
      return null;
    }

    String translationKey = LANGUAGE_TRANSLATION_TEMPLATE.formatted(canonicalCode);
    String translation = translationService.format(translationKey);
    if (StringUtils.isBlank(translation) || translation.equals(translationKey)) {
      return null;
    }
    return translation;
  }

  private static List<LocalizedLanguageValue> getLocalizedLanguageNames(Iterable<String> codes, Locale folioLocale,
                                                                        TranslationService translationService) {
    return getDistinctLanguageCodes(codes).stream()
      .map(code -> new LocalizedLanguageValue(code, localizeLanguageCode(code, folioLocale, translationService)))
      .toList();
  }

  private static List<String> getDistinctLanguageCodes(Iterable<String> codes) {
    return codes == null ? List.of() : toList(codes).stream()
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
      List<Map<String, String>> languages = mapper.readValue(input, new TypeReference<>() {
      });
      Map<String, String> codeToNameMap = new HashMap<>();
      Map<String, String> codeToA2Map = new HashMap<>();
      Map<String, String> codeToCanonicalCodeMap = new HashMap<>();

      for (Map<String, String> language : languages) {
        String alpha3 = language.get("alpha3");
        String alpha2 = language.get("alpha2");
        String name = language.get("name");

        codeToA2Map.put(alpha3, alpha2);
        codeToNameMap.put(alpha3, name);
        codeToCanonicalCodeMap.put(alpha3, alpha3);

        if (StringUtils.isNotEmpty(alpha2)) {
          codeToA2Map.put(alpha2, alpha2);
          codeToNameMap.put(alpha2, name);
          codeToCanonicalCodeMap.put(alpha2, alpha3);
        }
      }
      return new LanguageMetadata(codeToNameMap, codeToA2Map, codeToCanonicalCodeMap);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load language metadata", e);
    }
  }

  static record LanguageMetadata(Map<String, String> codeToNameMap, Map<String, String> codeToA2Map,
                                 Map<String, String> codeToCanonicalCodeMap) {
  }

  private record LocalizedLanguageValue(String rawValue, String localizedValue) {
  }
}
