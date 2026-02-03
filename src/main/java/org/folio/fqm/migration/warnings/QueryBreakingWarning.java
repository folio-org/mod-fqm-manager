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
public class QueryBreakingWarning implements FieldWarning {

  public static final WarningType TYPE = WarningType.QUERY_BREAKING;

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

  public static FieldWarningFactory withoutAlternative() {
    return (String fieldPrefix, String field, String fql) -> new QueryBreakingWarning(fieldPrefix + field, null, fql);
  }

  public static FieldWarningFactory withAlternative(String alternative) {
    return (String fieldPrefix, String field, String fql) ->
      new QueryBreakingWarning(fieldPrefix + field, fieldPrefix + alternative, fql);
  }
}
