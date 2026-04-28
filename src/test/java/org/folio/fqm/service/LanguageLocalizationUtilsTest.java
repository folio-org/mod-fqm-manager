package org.folio.fqm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

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

@ExtendWith(MockitoExtension.class)
class LanguageLocalizationUtilsTest {

  @Mock
  private TranslationService translationService;

  @BeforeEach
  void setUp() {
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
  void shouldBuildValueWithLabelListFromLocalizedCodes() {
    Set<String> codes = new LinkedHashSet<>(List.of("de", "ger", "eng"));

    List<ValueWithLabel> values = LanguageLocalizationUtils.getLanguageValues(codes, Locale.ENGLISH, translationService);

    assertEquals(3, values.size());
    assertEquals("de", values.get(0).getValue());
    assertEquals("German [de]", values.get(0).getLabel());
    assertEquals("ger", values.get(1).getValue());
    assertEquals("German [ger]", values.get(1).getLabel());
    assertEquals("eng", values.get(2).getValue());
    assertEquals("English", values.get(2).getLabel());
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
}
