package org.folio.fqm.migration.warnings;

import javax.annotation.CheckForNull;
import org.folio.fqm.service.LocalizationService;
import org.folio.spring.i18n.service.TranslationService;

public class QueryBreakingWarning implements FieldWarning {

  public static final WarningType TYPE = WarningType.QUERY_BREAKING;

  private final String field;

  @CheckForNull
  private final String alternative;

  @CheckForNull
  private final String fql;

  public QueryBreakingWarning(String field, String alternative, String fql) {
    this.field = field;
    this.alternative = alternative;
    this.fql = fql;
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
        "field",
        field,
        "alternative",
        alternative,
        "fql",
        fql
      );
    } else {
      return translationService.format(
        LocalizationService.MIGRATION_WARNING_TRANSLATION_TEMPLATE.formatted(this.getType().toString()) +
        ".withoutAlternative",
        "field",
        field,
        "fql",
        fql
      );
    }
  }
}