package org.folio.fqm.utils.flattening;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.folio.querytool.domain.dto.ArrayType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.NestedObjectProperty;
import org.folio.querytool.domain.dto.ObjectType;
import org.folio.querytool.domain.dto.StringType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import wiremock.com.fasterxml.jackson.core.JsonProcessingException;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;

class SourceUtilsInjectionTest {

  static List<Arguments> injectIntoViewExtractorCases() {
    // expect :foo -> bar and :foo.bar -> baz
    return List.of(
      Arguments.of("", ""),
      Arguments.of("foo", "foo"),
      Arguments.of(":foo", "\"bar\""),
      Arguments.of(":foo.bar", "\"baz\""),
      Arguments.of("foo:bar", "foo:bar"),
      Arguments.of(":foo:bar", "\"bar\":bar")
    );
  }

  @ParameterizedTest
  @MethodSource("injectIntoViewExtractorCases")
  void testInjectIntoViewExtractor(String input, String expected) {
    String result = SourceUtils.injectSourceAliasIntoViewExtractor(input, Map.of("foo.bar", "baz", "foo", "bar"));
    assertThat(result, is(expected));
  }

  static List<Arguments> injectSourceAliasLegacyCases() {
    // expect :sourceAlias -> foo
    // input, finalPass, expected
    return List.of(
      Arguments.of("", false, ""),
      Arguments.of("", true, ""),
      Arguments.of("foo", false, "foo"),
      Arguments.of("foo", true, "foo"),
      Arguments.of(":foo", false, ":[foo]"),
      Arguments.of(":foo", true, "\"foo\""),
      Arguments.of(":sourceAlias", false, ":[foo]"),
      Arguments.of(":sourceAlias", true, "\"foo\""),
      Arguments.of("foo->>:sourceAlias::uuid", false, "foo->>:[foo]::uuid"),
      Arguments.of("foo->>:sourceAlias::uuid", true, "foo->>\"foo\"::uuid")
    );
  }

  @ParameterizedTest
  @MethodSource("injectSourceAliasLegacyCases")
  void testInjectSourceAliasLegacy(String input, boolean finalPass, String expected) {
    EntityTypeColumn col = new EntityTypeColumn().valueGetter(input).filterValueGetter(input).valueFunction(input);
    EntityTypeColumn result = SourceUtils.injectSourceAlias(col, Map.of("foo", "foo"), "foo", finalPass);

    assertThat(result.getValueGetter(), is(expected));
    assertThat(result.getFilterValueGetter(), is(expected));
    assertThat(result.getValueFunction(), is(expected));
  }

  static List<Arguments> injectSourceAliasWithMapCases() {
    // expect :foo -> bar, :foo.bar -> baz
    // input, finalPass, expected
    return List.of(
      Arguments.of("", false, ""),
      Arguments.of("", true, ""),
      Arguments.of("foo", false, "foo"),
      Arguments.of("foo", true, "foo"),
      Arguments.of(":foo.bar:foo", false, ":[baz]:[bar]"),
      Arguments.of(":foo.bar:foo", true, "\"baz\"\"bar\""),
      Arguments.of("foo->>:foo::uuid", false, "foo->>:[bar]::uuid"),
      Arguments.of("foo->>:foo::uuid", true, "foo->>\"bar\"::uuid")
    );
  }

  @ParameterizedTest
  @MethodSource("injectSourceAliasWithMapCases")
  void testInjectSourceAliasWithMap(String input, boolean finalPass, String expected) {
    EntityTypeColumn col = new EntityTypeColumn()
      .valueGetter("VG " + input)
      .filterValueGetter("FVG " + input)
      .valueFunction("VF " + input);
    EntityTypeColumn result = SourceUtils.injectSourceAlias(
      col,
      Map.of("foo", "bar", "foo.bar", "baz"),
      null,
      finalPass
    );

    assertThat(result.getValueGetter(), is("VG " + expected));
    assertThat(result.getFilterValueGetter(), is("FVG " + expected));
    assertThat(result.getValueFunction(), is("VF " + expected));
  }

  @ParameterizedTest
  @MethodSource("injectSourceAliasWithMapCases")
  void testIgnoresSourceAliasIfNotMapped(String input, boolean finalPass, String expected) {
    EntityTypeColumn col = new EntityTypeColumn()
      .valueGetter("VG " + input)
      .filterValueGetter("FVG " + input)
      .valueFunction("VF " + input);
    EntityTypeColumn result = SourceUtils.injectSourceAlias(
      col,
      Map.of("foo", "bar", "foo.bar", "baz"),
      "somethingElse",
      finalPass
    );

    assertThat(result.getValueGetter(), is("VG " + expected));
    assertThat(result.getFilterValueGetter(), is("FVG " + expected));
    assertThat(result.getValueFunction(), is("VF " + expected));
  }

  @Test
  void testInjectSourceAliasIntoFilterConditions() {
    List<String> filterConditions = List.of(":foo.field1 != 'abc'", ":bar.field2 != 'xyz'");
    Map<String, String> renamedAliases = Map.of("foo", "source1", "bar", "source2");

    List<String> expectedNonFinalPass = List.of(":[source1].field1 != 'abc'", ":[source2].field2 != 'xyz'");
    List<String> actualNonFinalPass = SourceUtils.injectSourceAliasIntoFilterConditions(
      filterConditions,
      renamedAliases,
      false
    );
    assertEquals(expectedNonFinalPass, actualNonFinalPass);

    List<String> expectedFinalPass = List.of("\"source1\".field1 != 'abc'", "\"source2\".field2 != 'xyz'");
    List<String> actualFinalPass = SourceUtils.injectSourceAliasIntoFilterConditions(
      filterConditions,
      renamedAliases,
      true
    );
    assertEquals(expectedFinalPass, actualFinalPass);
  }

  @Test
  void testNullFilterValueGetterAndValueFunction() {
    EntityTypeColumn col = new EntityTypeColumn().valueGetter(":foo->>'field'");
    EntityTypeColumn result = SourceUtils.injectSourceAlias(col, Map.of("foo", "bar"), null, true);

    assertThat(result.getValueGetter(), is("\"bar\"->>'field'"));
    assertThat(result.getFilterValueGetter(), is(nullValue()));
    assertThat(result.getValueFunction(), is(nullValue()));
  }

  @Test
  void testHandlesPrimitiveArray() {
    EntityTypeColumn col = new EntityTypeColumn()
      .valueGetter(":foo->>'field'")
      .dataType(new ArrayType().itemDataType(new StringType()));
    EntityTypeColumn result = SourceUtils.injectSourceAlias(col, Map.of("foo", "bar"), null, true);

    assertThat(result.getValueGetter(), is("\"bar\"->>'field'"));
  }

  @Test
  void testHandlesNestedObjectsAndArrays() throws JsonProcessingException {
    EntityTypeColumn col = new EntityTypeColumn()
      .valueGetter(":foo->>'field'")
      .dataType(
        new ArrayType()
          .itemDataType(
            new ArrayType()
              .itemDataType(
                new ObjectType()
                  .properties(
                    List.of(
                      new NestedObjectProperty()
                        .valueGetter(":foo->>'nested field'")
                        .dataType(
                          new ObjectType()
                            .properties(
                              List.of(
                                new NestedObjectProperty()
                                  .valueGetter(":foo->>'nested nested field array'")
                                  .dataType(
                                    new ArrayType()
                                      .itemDataType(
                                        new ObjectType()
                                          .properties(
                                            List.of(
                                              new NestedObjectProperty()
                                                .valueGetter(":foo->>'this is ridiculous'")
                                                .dataType(new StringType())
                                            )
                                          )
                                      )
                                  )
                              )
                            )
                        )
                    )
                  )
              )
          )
      );
    EntityTypeColumn result = SourceUtils.injectSourceAlias(col, Map.of("foo", "bar"), null, true);

    // easiest way to test this mess, lol
    DocumentContext json = JsonPath.parse(new ObjectMapper().writeValueAsString(result));
    assertThat(
      json.read("$.dataType.itemDataType.itemDataType.properties[0].valueGetter"),
      is("\"bar\"->>'nested field'")
    );
    assertThat(
      json.read("$.dataType.itemDataType.itemDataType.properties[0].dataType.properties[0].valueGetter"),
      is("\"bar\"->>'nested nested field array'")
    );
    assertThat(
      json.read(
        "$.dataType.itemDataType.itemDataType.properties[0].dataType.properties[0].dataType.itemDataType.properties[0].valueGetter"
      ),
      is("\"bar\"->>'this is ridiculous'")
    );
  }
}
