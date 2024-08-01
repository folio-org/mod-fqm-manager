package org.folio.fqm.migration.warnings;

import javax.annotation.CheckForNull;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.folio.fqm.service.LocalizationService;
import org.folio.spring.i18n.service.TranslationService;

@ToString
@EqualsAndHashCode
@RequiredArgsConstructor
public class DeprecatedFieldWarning implements FieldWarning {

  public static final WarningType TYPE = WarningType.DEPRECATED_FIELD;

  private final String field;

  @CheckForNull
  private final String fql;

  @Override
  public WarningType getType() {
    return TYPE;
  }

  @Override
  public String getDescription(TranslationService translationService) {
    if (fql != null) {
      return translationService.format(
        // we do not share the query itself here since the field is not removed from the query.
        // the use of the `fql` parameter is just for a more informative warning, e.g. "in your query" vs "in your field list"
        LocalizationService.MIGRATION_WARNING_TRANSLATION_TEMPLATE.formatted(this.getType().toString()) + ".query",
        "name",
        field
      );
    } else {
      return translationService.format(
        LocalizationService.MIGRATION_WARNING_TRANSLATION_TEMPLATE.formatted(this.getType().toString()) + ".field",
        "name",
        field
      );
    }
  }
}
