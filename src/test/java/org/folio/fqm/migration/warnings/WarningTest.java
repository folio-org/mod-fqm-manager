package org.folio.fqm.migration.warnings;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.folio.fqm.migration.warnings.Warning.WarningType;
import org.folio.spring.i18n.service.TranslationService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WarningTest {

  @Mock
  TranslationService translationService;

  @Captor
  ArgumentCaptor<Object[]> varargCaptor;

  public static List<Arguments> getExpectedTypes() {
    return List.of(
      Arguments.of(new DeprecatedEntityWarning("old", "new"), WarningType.DEPRECATED_ENTITY),
      Arguments.of(new DeprecatedFieldWarning("old", "{}"), WarningType.DEPRECATED_FIELD),
      Arguments.of(new QueryBreakingWarning("old", "new", "{}"), WarningType.QUERY_BREAKING),
      Arguments.of(new RemovedEntityWarning("old", "new", "{}"), WarningType.REMOVED_ENTITY),
      Arguments.of(new RemovedFieldWarning("old", "new", "{}"), WarningType.REMOVED_FIELD)
    );
  }

  @ParameterizedTest(name = "{index}: {1}")
  @MethodSource("getExpectedTypes")
  void testTypes(Warning warning, WarningType expectedType) {
    assertThat(warning.getType(), is(expectedType));
  }

  public static List<Arguments> getExpectedTranslations() {
    return List.of(
      Arguments.of(
        new DeprecatedEntityWarning("old", "alt"),
        "mod-fqm-manager.migration.warning.DEPRECATED_ENTITY.withAlternative",
        List.of("entityType", "old", "alternative", "alt")
      ),
      Arguments.of(
        new DeprecatedEntityWarning("old", null),
        "mod-fqm-manager.migration.warning.DEPRECATED_ENTITY.withoutAlternative",
        List.of("entityType", "old")
      ),
      Arguments.of(
        new DeprecatedFieldWarning("old", null),
        "mod-fqm-manager.migration.warning.DEPRECATED_FIELD.field",
        List.of("field", "old")
      ),
      Arguments.of(
        new DeprecatedFieldWarning("old", "{}"),
        "mod-fqm-manager.migration.warning.DEPRECATED_FIELD.query",
        List.of("field", "old")
      ),
      Arguments.of(
        new QueryBreakingWarning("old", "alt", "{}"),
        "mod-fqm-manager.migration.warning.QUERY_BREAKING.withAlternative",
        List.of("field", "old", "alternative", "alt", "fql", "{}")
      ),
      Arguments.of(
        new QueryBreakingWarning("old", null, "{}"),
        "mod-fqm-manager.migration.warning.QUERY_BREAKING.withoutAlternative",
        List.of("field", "old", "fql", "{}")
      ),
      Arguments.of(
        new RemovedEntityWarning("old", "alt", "{}"),
        "mod-fqm-manager.migration.warning.REMOVED_ENTITY.withAlternative",
        List.of("entityType", "old", "alternative", "alt", "fql", "{}")
      ),
      Arguments.of(
        new RemovedEntityWarning("old", null, "{}"),
        "mod-fqm-manager.migration.warning.REMOVED_ENTITY.withoutAlternative",
        List.of("entityType", "old", "fql", "{}")
      ),
      Arguments.of(
        new RemovedFieldWarning("old", "alt", "{}"),
        "mod-fqm-manager.migration.warning.REMOVED_FIELD.withAlternative",
        List.of("field", "old", "alternative", "alt", "fql", "{}")
      ),
      Arguments.of(
        new RemovedFieldWarning("old", null, "{}"),
        "mod-fqm-manager.migration.warning.REMOVED_FIELD.withoutAlternative",
        List.of("field", "old", "fql", "{}")
      )
    );
  }

  @ParameterizedTest(name = "{index}: {1}")
  @MethodSource("getExpectedTranslations")
  void testTranslations(Warning warning, String expectedKey, List<String> expectedArgs) {
    when(translationService.format(eq(expectedKey), any(Object[].class))).thenReturn("formatted warning");

    assertThat(warning.getDescription(translationService), is("formatted warning"));

    verify(translationService, times(1)).format(eq(expectedKey), varargCaptor.capture());
    assertThat(Arrays.stream(varargCaptor.getValue()).map(String.class::cast).toList(), is(expectedArgs));
    verifyNoMoreInteractions(translationService);
  }
}
