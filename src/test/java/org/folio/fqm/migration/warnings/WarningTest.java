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
      Arguments.of(DeprecatedEntityWarning.withAlternative("old", "new").apply(null), WarningType.DEPRECATED_ENTITY),
      Arguments.of(DeprecatedFieldWarning.build().apply("old", "{}"), WarningType.DEPRECATED_FIELD),
      Arguments.of(OperatorBreakingWarning.builder().build(), WarningType.OPERATOR_BREAKING),
      Arguments.of(QueryBreakingWarning.withAlternative("new").apply("old", "{}"), WarningType.QUERY_BREAKING),
      Arguments.of(RemovedEntityWarning.withAlternative("old", "new").apply("{}"), WarningType.REMOVED_ENTITY),
      Arguments.of(RemovedFieldWarning.withAlternative("new").apply("old", "{}"), WarningType.REMOVED_FIELD)
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
        DeprecatedEntityWarning.withAlternative("old", "alt").apply(null),
        "mod-fqm-manager.migration.warning.DEPRECATED_ENTITY.withAlternative",
        List.of("name", "old", "alternative", "alt")
      ),
      Arguments.of(
        DeprecatedEntityWarning.withoutAlternative("old").apply(null),
        "mod-fqm-manager.migration.warning.DEPRECATED_ENTITY.withoutAlternative",
        List.of("name", "old")
      ),
      Arguments.of(
        DeprecatedFieldWarning.build().apply("old", null),
        "mod-fqm-manager.migration.warning.DEPRECATED_FIELD.field",
        List.of("name", "old")
      ),
      Arguments.of(
        DeprecatedFieldWarning.build().apply("old", "{}"),
        "mod-fqm-manager.migration.warning.DEPRECATED_FIELD.query",
        List.of("name", "old")
      ),
      Arguments.of(
        OperatorBreakingWarning.builder().field("old").operator("$ne").fql("{}").build(),
        "mod-fqm-manager.migration.warning.OPERATOR_BREAKING",
        List.of("name", "old", "operator", "$ne", "fql", "{}")
      ),
      Arguments.of(
        QueryBreakingWarning.withAlternative("alt").apply("old", "{}"),
        "mod-fqm-manager.migration.warning.QUERY_BREAKING.withAlternative",
        List.of("name", "old", "alternative", "alt", "fql", "{}")
      ),
      Arguments.of(
        QueryBreakingWarning.withoutAlternative().apply("old", "{}"),
        "mod-fqm-manager.migration.warning.QUERY_BREAKING.withoutAlternative",
        List.of("name", "old", "fql", "{}")
      ),
      Arguments.of(
        RemovedEntityWarning.withAlternative("old", "alt").apply("{}"),
        "mod-fqm-manager.migration.warning.REMOVED_ENTITY.withAlternative",
        List.of("name", "old", "alternative", "alt", "fql", "{}")
      ),
      Arguments.of(
        RemovedEntityWarning.withoutAlternative("old").apply("{}"),
        "mod-fqm-manager.migration.warning.REMOVED_ENTITY.withoutAlternative",
        List.of("name", "old", "fql", "{}")
      ),
      Arguments.of(
        RemovedFieldWarning.withAlternative("alt").apply("old", "{}"),
        "mod-fqm-manager.migration.warning.REMOVED_FIELD.withAlternative",
        List.of("name", "old", "alternative", "alt", "fql", "{}")
      ),
      Arguments.of(
        RemovedFieldWarning.withoutAlternative().apply("old", "{}"),
        "mod-fqm-manager.migration.warning.REMOVED_FIELD.withoutAlternative",
        List.of("name", "old", "fql", "{}")
      ),
      Arguments.of(
        ValueBreakingWarning.builder().field("old").value("val").fql("{}").build(),
        "mod-fqm-manager.migration.warning.VALUE_BREAKING",
        List.of("name", "old", "value", "val", "fql", "{}")
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
