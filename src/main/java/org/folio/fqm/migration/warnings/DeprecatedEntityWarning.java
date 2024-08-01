package org.folio.fqm.migration.warnings;

import org.folio.fqm.service.LocalizationService;
import org.folio.spring.i18n.service.TranslationService;

public class DeprecatedEntityWarning implements EntityTypeWarning {

  public static final WarningType TYPE = WarningType.DEPRECATED_ENTITY;

  private final String entityType;
  private final String alternative;

  public DeprecatedEntityWarning(String entityType, String alternative) {
    this.entityType = entityType;
    this.alternative = alternative;
  }

  @Override
  public WarningType getType() {
    return TYPE;
  }

  @Override
  public String getDescription(TranslationService translationService) {
    if (alternative != null) {
      return translationService.format(
        LocalizationService.MIGRATION_WARNING_TRANSLATION_TEMPLATE.formatted(this.getType().toString()) +
        ".withAlternative",
        "entityType",
        entityType,
        "alternative",
        alternative
      );
    } else {
      return translationService.format(
        LocalizationService.MIGRATION_WARNING_TRANSLATION_TEMPLATE.formatted(this.getType().toString()) +
        ".withoutAlternative",
        "entityType",
        entityType
      );
    }
  }
}
