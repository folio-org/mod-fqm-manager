package org.folio.fqm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.folio.spring.i18n.service.TranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalizationServiceTest {

  @Mock
  private TranslationService translationService;

  @InjectMocks
  private LocalizationService localizationService;

  @Test
  void testTableNameFormatting() {
    String tableName = "table_name";
    String expectedTranslationKey = "mod-fqm-manager.entityType.table_name";
    String expectedTranslation = "Table Name";

    when(translationService.format(expectedTranslationKey))
      .thenReturn(expectedTranslation);

    String actualTranslation = localizationService.getEntityTypeLabel(
      tableName
    );

    assertEquals(expectedTranslation, actualTranslation);

    verify(translationService, times(1)).format(expectedTranslationKey);
    verifyNoMoreInteractions(translationService);
  }

  @Test
  void testColumnNameFormatting() {
    String tableName = "table_name";
    String columnName = "column_name";
    String expectedTranslationKey =
      "mod-fqm-manager.entityType.table_name.column_name";
    String expectedTranslation = "Column Name";

    when(translationService.format(expectedTranslationKey))
      .thenReturn(expectedTranslation);

    String actualTranslation = localizationService.getEntityTypeColumnLabel(
      tableName,
      columnName
    );

    assertEquals(expectedTranslation, actualTranslation);

    verify(translationService, times(1)).format(expectedTranslationKey);
    verifyNoMoreInteractions(translationService);
  }
}
