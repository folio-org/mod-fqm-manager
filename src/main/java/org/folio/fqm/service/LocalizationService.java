package org.folio.fqm.service;

import org.folio.spring.i18n.service.TranslationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Small wrapper class for {@link TranslationService TranslationService} to provide reusable templates for
 * translations, particularly for entity type definitions.
 */
@Service
public class LocalizationService {

  private static final String ENTITY_TYPE_LABEL_TRANSLATION_TEMPLATE =
    "mod-fqm-manager.entityType.%s";

  private TranslationService translationService;

  @Autowired
  public LocalizationService(TranslationService translationService) {
    this.translationService = translationService;
  }

  public String getEntityTypeLabel(String tableName) {
    return translationService.format(
      ENTITY_TYPE_LABEL_TRANSLATION_TEMPLATE.formatted(tableName)
    );
  }
}
