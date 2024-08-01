package org.folio.fqm.migration.warnings;

import javax.annotation.CheckForNull;
import org.folio.fqm.service.LocalizationService;
import org.folio.spring.i18n.service.TranslationService;

public class DeprecatedFieldWarning implements FieldWarning {

  public static final WarningType TYPE = WarningType.DEPRECATED_FIELD;

  private final String field;

  @CheckForNull
  private final String fql;

  public DeprecatedFieldWarning(String field, String fql) {
    this.field = field;
    this.fql = fql;
  }

  @Override
  public WarningType getType() {
    return TYPE;
  }

  @Override
  public String getDescription(TranslationService translationService) {
    if (fql != null) {
      return translationService.format(
        LocalizationService.MIGRATION_WARNING_TRANSLATION_TEMPLATE.formatted(this.getType().toString()) + ".query",
        "field",
        field
      );
    } else {
      return translationService.format(
        LocalizationService.MIGRATION_WARNING_TRANSLATION_TEMPLATE.formatted(this.getType().toString()) + ".field",
        "field",
        field
      );
    }
  }
}
