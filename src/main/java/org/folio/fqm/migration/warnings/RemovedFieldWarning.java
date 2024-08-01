package org.folio.fqm.migration.warnings;

import javax.annotation.CheckForNull;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.folio.spring.i18n.service.TranslationService;

@ToString
@EqualsAndHashCode
@RequiredArgsConstructor
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
    return Warning.getDescriptionByAlternativeAndFql(translationService, this.getType(), field, fql, alternative);
  }
}
