package org.folio.fqm.migration.warnings;

import java.util.function.Function;
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
        "name",
        entityType,
        "alternative",
        alternative
      );
    } else {
      return translationService.format(
        LocalizationService.MIGRATION_WARNING_TRANSLATION_TEMPLATE.formatted(this.getType().toString()) +
        ".withoutAlternative",
        "name",
        entityType
      );
    }
  }

  public static Function<String, EntityTypeWarning> withoutAlternative(String entityType) {
    return (String fql) -> new DeprecatedEntityWarning(entityType, null);
  }

  public static Function<String, EntityTypeWarning> withAlternative(String entityType, String alternative) {
    return (String fql) -> new DeprecatedEntityWarning(entityType, alternative);
  }
}
