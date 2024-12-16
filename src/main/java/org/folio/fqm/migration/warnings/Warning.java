package org.folio.fqm.migration.warnings;

import jakarta.validation.constraints.NotNull;
import javax.annotation.CheckForNull;
import lombok.RequiredArgsConstructor;
import org.folio.fqm.service.LocalizationService;
import org.folio.spring.i18n.service.TranslationService;

public interface Warning {
  @NotNull
  WarningType getType();

  @NotNull
  String getDescription(TranslationService translationService);

  public static String getDescriptionByAlternativeAndFql(
    TranslationService translationService,
    WarningType type,
    String name,
    String fql,
    @CheckForNull String alternative
  ) {
    if (alternative != null) {
      return translationService.format(
        LocalizationService.MIGRATION_WARNING_TRANSLATION_TEMPLATE.formatted(type.toString()) + ".withAlternative",
        "name",
        name,
        "alternative",
        alternative,
        "fql",
        fql
      );
    } else {
      return translationService.format(
        LocalizationService.MIGRATION_WARNING_TRANSLATION_TEMPLATE.formatted(type.toString()) + ".withoutAlternative",
        "name",
        name,
        "fql",
        fql
      );
    }
  }

  @RequiredArgsConstructor
  public enum WarningType {
    /** Only warns the user. Query and fields are unaffected */
    DEPRECATED_FIELD("DEPRECATED_FIELD"),
    /** Only warns the user. Query and fields are unaffected */
    DEPRECATED_ENTITY("DEPRECATED_ENTITY"),
    /** Query is broken and will not work with this field; we will remove the field from the query */
    QUERY_BREAKING("QUERY_BREAKING"),
    /** Operator is broken and will not work with this field; we will remove the field from the query */
    OPERATOR_BREAKING("OPERATOR_BREAKING"),
    /** This field is completely gone from both fields and queries */
    REMOVED_FIELD("REMOVED_FIELD"),
    /** This entity type is completely gone */
    REMOVED_ENTITY("REMOVED_ENTITY"),
    /** Value is invalid/broken; we will remove this value from the query */
    VALUE_BREAKING("VALUE_BREAKING");

    private final String type;

    @Override
    public String toString() {
      return type;
    }
  }
}
