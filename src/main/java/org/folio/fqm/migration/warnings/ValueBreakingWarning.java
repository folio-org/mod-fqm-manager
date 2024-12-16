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
public class ValueBreakingWarning implements FieldWarning {

  public static final WarningType TYPE = WarningType.VALUE_BREAKING;

  private final String field;
  private final String value;

  @CheckForNull
  private final String fql;

  @Override
  public WarningType getType() {
    return TYPE;
  }

  @Override
  public String getDescription(TranslationService translationService) {
    return translationService.format(
      LocalizationService.MIGRATION_WARNING_TRANSLATION_TEMPLATE.formatted(this.getType().toString()),
      "name",
      field,
      "value",
      value,
      "fql",
      fql
    );
  }
}
