package org.folio.fqm.migration.warnings;

import javax.annotation.CheckForNull;
import lombok.RequiredArgsConstructor;
import org.folio.fqm.service.LocalizationService;
import org.folio.spring.i18n.service.TranslationService;

@RequiredArgsConstructor
public class DeprecatedEntityWarning implements EntityTypeWarning {

  public static final WarningType TYPE = WarningType.DEPRECATED_ENTITY;

  private final String entityType;

  @CheckForNull
  private final String alternative;

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
