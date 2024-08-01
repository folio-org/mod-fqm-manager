package org.folio.fqm.migration.warnings;

import javax.annotation.CheckForNull;
import lombok.RequiredArgsConstructor;
import org.folio.spring.i18n.service.TranslationService;

@RequiredArgsConstructor
public class RemovedEntityWarning implements EntityTypeWarning {

  public static final WarningType TYPE = WarningType.REMOVED_ENTITY;

  private final String entityType;

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
    return Warning.getDescriptionByAlternativeAndFql(translationService, TYPE, entityType, fql, alternative);
  }
}
