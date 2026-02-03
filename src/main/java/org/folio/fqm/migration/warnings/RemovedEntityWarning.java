package org.folio.fqm.migration.warnings;

import javax.annotation.CheckForNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.folio.spring.i18n.service.TranslationService;

@Builder
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
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

  public static EntityTypeWarningFactory withoutAlternative(String entityType) {
    return (String fql) -> new RemovedEntityWarning(entityType, null, fql);
  }

  public static EntityTypeWarningFactory withAlternative(String entityType, String alternative) {
    return (String fql) -> new RemovedEntityWarning(entityType, alternative, fql);
  }
}
