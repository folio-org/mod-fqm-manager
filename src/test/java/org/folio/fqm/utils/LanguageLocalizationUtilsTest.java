package org.folio.fqm.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.folio.querytool.domain.dto.ValueWithLabel;
import org.folio.spring.i18n.service.TranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(MockitoExtension.class)
class LanguageLocalizationUtilsTest {

  @Mock
  private TranslationService translationService;

  @BeforeEach
  void setUp() {
    lenient().when(translationService.format(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(translationService.format(
      eq(LanguageLocalizationUtils.LANGUAGE_DISAMBIGUATION_TEMPLATE),
      eq("label"), anyString(),
      eq("code"), anyString()
    )).thenAnswer(invocation -> "%s [%s]".formatted(invocation.getArgument(2), invocation.getArgument(4)));
  }

  @Test
  void shouldLocalizeAlpha2AndAlpha3CodesToTheSameLanguage() {
    assertEquals("German", LanguageLocalizationUtils.localizeLanguageCode("de", Locale.ENGLISH));
    assertEquals("German", LanguageLocalizationUtils.localizeLanguageCode("ger", Locale.ENGLISH));
  }

  @Test
  void shouldFallBackToRawCodeForUnknownLanguage() {
    assertEquals("zzz", LanguageLocalizationUtils.localizeLanguageCode("zzz", Locale.ENGLISH));
  }

  @Test
  void shouldDisambiguateDuplicateLocalizedLabels() {
    Set<String> codes = new LinkedHashSet<>(List.of("de", "ger", "eng"));

    Map<String, String> displayMap = LanguageLocalizationUtils.getLanguageDisplayMap(codes, Locale.ENGLISH, translationService);

    assertEquals(Map.of(
      "de", "German [de]",
      "ger", "German [ger]",
      "eng", "English"
    ), displayMap);
  }

  @Test
  void shouldFallBackToJsonLanguageNamesBeforeJavaLocaleWhenTranslationsAreMissing() {
    Set<String> codes = new LinkedHashSet<>(List.of("de", "ger", "eng"));
    Map<String, String> expected = Map.of(
      "de", "German [de]",
      "ger", "German [ger]",
      "eng", "English"
    );

    Map<String, String> actual = LanguageLocalizationUtils.getLanguageDisplayMap(codes, Locale.GERMAN, translationService);

    assertEquals(expected, actual);
  }

  @Test
  void shouldUseTranslationServiceValueBeforeJsonNameAndJavaLocaleWhenAvailable() {
    when(translationService.format("mod-fqm-manager.languages.dut")).thenReturn("Niederlandisch; Flamisch");

    assertEquals("Niederlandisch; Flamisch", LanguageLocalizationUtils.localizeLanguageCode("dut", Locale.GERMAN, translationService));
    assertEquals("Niederlandisch; Flamisch", LanguageLocalizationUtils.localizeLanguageCode("nl", Locale.GERMAN, translationService));
  }

  @Test
  void shouldFallBackToJsonLanguageNameBeforeJavaLocaleWhenTranslationIsMissing() {
    assertEquals("Dutch; Flemish", LanguageLocalizationUtils.localizeLanguageCode("dut", Locale.ENGLISH, translationService));
    assertEquals("Dutch; Flemish", LanguageLocalizationUtils.localizeLanguageCode("nl", Locale.GERMAN, translationService));
  }

  @Test
  void shouldBuildValueWithLabelListFromLocalizedCodes() {
    Set<String> codes = new LinkedHashSet<>(List.of("de", "ger", "eng"));

    List<ValueWithLabel> values = LanguageLocalizationUtils.getLanguageValues(codes, Locale.ENGLISH, translationService);

    assertEquals(
      List.of(
        Map.entry("de", "German [de]"),
        Map.entry("ger", "German [ger]"),
        Map.entry("eng", "English")
      ),
      values.stream()
        .map(value -> Map.entry(value.getValue(), value.getLabel()))
        .toList()
    );
  }

  @Test
  void shouldThrowWhenLanguageMetadataCannotBeLoaded() {
    ByteArrayInputStream invalidJson = new ByteArrayInputStream("not valid json".getBytes());
    ObjectMapper objectMapper = new ObjectMapper();

    assertThrows(
      IllegalStateException.class,
      () -> LanguageLocalizationUtils.loadLanguageMetadata(invalidJson, objectMapper)
    );
  }

  @Test
  void shouldReturnEmptyMapWhenCodesIsEmptyOrNull() {
    // TestMate-860a6f82ed50629b8646504f5a043e0b
    // Given
    Iterable<String> nullCodes = null;
    Iterable<String> emptyCodes = Collections.emptyList();
    Locale locale = Locale.ENGLISH;
    // When
    Map<String, String> resultForNull = LanguageLocalizationUtils.getLanguageDisplayMap(nullCodes, locale, translationService);
    Map<String, String> resultForEmpty = LanguageLocalizationUtils.getLanguageDisplayMap(emptyCodes, locale, translationService);
    // Then
    assertThat(resultForNull).isEmpty();
    assertThat(resultForEmpty).isEmpty();
  }

  @Test
  void shouldReturnUniqueLabelsWithoutDisambiguation() {
    // TestMate-7834fb2aa204bb36101681c7c475092b
    // Given
    Set<String> codes = new LinkedHashSet<>(List.of("en", "fr", "es"));
    Locale locale = Locale.ENGLISH;
    // The internal metadata (languages.json5) maps "es" to "Spanish; Castilian".
    // We update the expected map to align with the actual project metadata.
    Map<String, String> expected = Map.of(
      "en", "English",
      "fr", "French",
      "es", "Spanish; Castilian"
    );
    // When
    Map<String, String> actual = LanguageLocalizationUtils.getLanguageDisplayMap(codes, locale, translationService);
    // Then
    assertThat(actual)
      .hasSize(3)
      .isEqualTo(expected)
      .noneSatisfy((code, label) -> assertThat(label).contains("[", "]"));
  }

  @Test
  void shouldFilterEmptyStringsAndDuplicatesInInput() {
    // TestMate-ed47301eddb7b0966aa21a38a484ea7c
    // Given
    List<String> codes = List.of("en", "", "en", "fr");
    Locale locale = Locale.ENGLISH;
    // When
    Map<String, String> actual = LanguageLocalizationUtils.getLanguageDisplayMap(codes, locale, translationService);
    // Then
    assertThat(actual)
      .hasSize(2)
      .containsOnlyKeys("en", "fr")
      .containsEntry("en", "English")
      .containsEntry("fr", "French");
    assertThat(actual.keySet()).doesNotContain("");
  }

  @Test
  void shouldHandleMultipleAmbiguousGroups() {
    // TestMate-2a792cf556512ab41d580c8f0a0b8655
    // Given
    Set<String> codes = new LinkedHashSet<>(List.of("de", "ger", "nl", "dut"));
    Locale locale = Locale.ENGLISH;
    Map<String, String> expected = Map.of(
      "de", "German [de]",
      "ger", "German [ger]",
      "nl", "Dutch; Flemish [nl]",
      "dut", "Dutch; Flemish [dut]"
    );
    // When
    Map<String, String> actual = LanguageLocalizationUtils.getLanguageDisplayMap(codes, locale, translationService);
    // Then
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void shouldDisambiguateWhenRawCodeMatchesAnotherLocalizedLabel() {
    // TestMate-baf018b930cd007d2ba2d94a3ef852b0
    // Given
    Set<String> codes = new LinkedHashSet<>(List.of("en", "English"));
    Locale locale = Locale.ENGLISH;
    // When
    Map<String, String> displayMap = LanguageLocalizationUtils.getLanguageDisplayMap(codes, locale, translationService);
    // Then
    assertThat(displayMap)
      .hasSize(2)
      .containsEntry("en", "English [en]")
      .containsEntry("English", "English [English]");
  }

  @Test
  void shouldFallbackWhenTranslationIsBlankOrKey() {
    // TestMate-49360f7b2c7aa245f3a07646c337bbf6
    // Given
    String code = "eng";
    String translationKey = "mod-fqm-manager.languages.eng";
    String blankTranslation = " ";
    Locale locale = Locale.ENGLISH;
    String expectedFallbackValue = "English";
    // When
    when(translationService.format(translationKey)).thenReturn(translationKey);
    String resultForKeyFallback = LanguageLocalizationUtils.localizeLanguageCode(code, locale, translationService);
    when(translationService.format(translationKey)).thenReturn(blankTranslation);
    String resultForBlankFallback = LanguageLocalizationUtils.localizeLanguageCode(code, locale, translationService);
    // Then
    assertEquals(expectedFallbackValue, resultForKeyFallback);
    assertEquals(expectedFallbackValue, resultForBlankFallback);
  }
}
