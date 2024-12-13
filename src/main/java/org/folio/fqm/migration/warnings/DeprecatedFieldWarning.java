package org.folio.fqm.migration.warnings;

import java.util.function.BiFunction;
import javax.annotation.CheckForNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.folio.fqm.service.LocalizationService;
import org.folio.spring.i18n.service.TranslationService;

@Builder
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
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

  public static BiFunction<String, String, FieldWarning> build() {
    return (String field, String fql) -> new DeprecatedFieldWarning(field, fql);
  }
}
