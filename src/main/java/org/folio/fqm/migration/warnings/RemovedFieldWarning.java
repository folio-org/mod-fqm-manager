package org.folio.fqm.migration.warnings;

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
public class RemovedFieldWarning implements FieldWarning {

  public static final WarningType TYPE = WarningType.REMOVED_FIELD;

  private final String field;

  @CheckForNull
  private final String alternative;

  @CheckForNull
  private final String fql;

  @Override
  public WarningType getType() {
    return TYPE;
  }

  @Override
  public String getDescription(TranslationService translationService) {
    String translationKey = LocalizationService.MIGRATION_WARNING_TRANSLATION_TEMPLATE.formatted(TYPE.toString());

    if (fql == null) {
      translationKey += ".field";
    } else {
      translationKey += ".query";
    }
    if (alternative == null) {
      translationKey += ".withoutAlternative";
    } else {
      translationKey += ".withAlternative";
    }

    return translationService.format(translationKey, "name", field, "alternative", alternative, "fql", fql);
  }

  public static FieldWarningFactory withoutAlternative() {
    return (String fieldPrefix, String field, String fql) -> new RemovedFieldWarning(fieldPrefix + field, null, fql);
  }

  public static FieldWarningFactory withAlternative(String alternative) {
    return (String fieldPrefix, String field, String fql) ->
      new RemovedFieldWarning(fieldPrefix + field, fieldPrefix + alternative, fql);
  }
}
