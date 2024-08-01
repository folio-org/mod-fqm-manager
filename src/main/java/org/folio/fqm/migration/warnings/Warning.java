package org.folio.fqm.migration.warnings;

import lombok.RequiredArgsConstructor;
import org.folio.spring.i18n.service.TranslationService;

public interface Warning {
  WarningType getType();
  String getDescription(TranslationService translationService);

  @RequiredArgsConstructor
  public enum WarningType {
    /** Only warns the user. Query and fields are unaffected */
    DEPRECATED_FIELD("DEPRECATED_FIELD"),
    /** Only warns the user. Query and fields are unaffected */
    DEPRECATED_ENTITY("DEPRECATED_ENTITY"),
    /** Query is broken and will not work with this field; we will remove the field from the query */
    QUERY_BREAKING("QUERY_BREAKING"),
    /** This field is completely gone from both fields and queries */
    REMOVED_FIELD("REMOVED_FIELD"),
    /** This entity type is completely gone */
    REMOVED_ENTITY("REMOVED_ENTITY");

    private final String type;

    @Override
    public String toString() {
      return type;
    }
  }
}
