package org.folio.fqm.utils.flattening;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabaseJoin;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SourceUtilsCopyTest {

  static List<Arguments> copyColumnsIdColumnCases() {
    // parent, isIdColumn, expected
    return List.of(
      Arguments.of(null, null, null),
      Arguments.of(null, true, true),
      Arguments.of(null, false, false),
      Arguments.of(new EntityTypeSourceEntityType().useIdColumns(true), null, null),
      Arguments.of(new EntityTypeSourceEntityType().useIdColumns(true), true, true),
      Arguments.of(new EntityTypeSourceEntityType().useIdColumns(true), false, false),
      Arguments.of(new EntityTypeSourceEntityType().useIdColumns(false), null, null),
      Arguments.of(new EntityTypeSourceEntityType().useIdColumns(false), true, false),
      Arguments.of(new EntityTypeSourceEntityType().useIdColumns(false), false, false)
    );
  }

  @ParameterizedTest
  @MethodSource("copyColumnsIdColumnCases")
  void testCopyColumnsIdColumn(EntityTypeSourceEntityType parent, Boolean isIdColumn, Boolean expected) {
    EntityTypeColumn result = SourceUtils
      .copyColumns(parent, Stream.of(new EntityTypeColumn().isIdColumn(isIdColumn).valueGetter("")), Map.of())
      .findAny()
      .orElseThrow();

    assertThat(result, hasProperty("isIdColumn", is(expected)));
  }

  static List<Arguments> copyDatabaseSourceCases() {
    // parent, source, expected properties => values
    // can assume renamedAliases=[foo => bar]
    return List.of(
      // basic property changes
      Arguments.of(
        null,
        new EntityTypeSourceDatabase().alias("foo"),
        Map.of("alias", "bar", "joinedViaEntityType", "null", "join", "null")
      ),
      Arguments.of(
        new EntityTypeSourceEntityType().alias("foo"),
        new EntityTypeSourceDatabase().alias("foo"),
        Map.of("joinedViaEntityType", "foo") // we don't map the parent
      ),
      Arguments.of(
        null,
        new EntityTypeSourceDatabase().alias("foo").joinedViaEntityType("foo"),
        Map.of("joinedViaEntityType", "bar")
      ),
      Arguments.of(
        new EntityTypeSourceEntityType().alias("other"),
        new EntityTypeSourceDatabase().alias("foo").joinedViaEntityType("foo"),
        Map.of("joinedViaEntityType", "bar")
      ),
      Arguments.of(
        null,
        new EntityTypeSourceDatabase()
          .alias("foo")
          .join(new EntityTypeSourceDatabaseJoin().type("type").condition("cond").joinTo("foo")),
        Map.of("join", new EntityTypeSourceDatabaseJoin().type("type").condition("cond").joinTo("bar"))
      )
    );
  }

  static List<Arguments> copyEntityTypeSourceCases() {
    // parent, source, expected properties => values
    // can assume renamedAliases=[foo => bar]
    return List.of(
      // basic property changes
      Arguments.of(
        null,
        new EntityTypeSourceEntityType().alias("foo"),
        Map.of("alias", "bar", "joinedViaEntityType", "null")
      ),
      Arguments.of(
        new EntityTypeSourceEntityType().alias("foo"),
        new EntityTypeSourceEntityType().alias("foo"),
        Map.of("joinedViaEntityType", "foo") // we don't rename the parent
      ),
      Arguments.of(
        null,
        new EntityTypeSourceEntityType().alias("foo").joinedViaEntityType("foo"),
        Map.of("joinedViaEntityType", "bar")
      ),
      Arguments.of(
        new EntityTypeSourceEntityType().alias("other"),
        new EntityTypeSourceEntityType().alias("foo").joinedViaEntityType("foo"),
        Map.of("joinedViaEntityType", "bar")
      ),
      // source field
      Arguments.of(
        null,
        new EntityTypeSourceEntityType().alias("foo").sourceField("other.field"),
        Map.of("sourceField", "other.field")
      ),
      // we only map the left side
      Arguments.of(
        null,
        new EntityTypeSourceEntityType().alias("foo").sourceField("foo.foo"),
        Map.of("sourceField", "bar.foo")
      ),
      Arguments.of(
        null,
        new EntityTypeSourceEntityType().alias("foo").sourceField("foo.long.field"),
        Map.of("sourceField", "foo.long.field")
      )
    );
  }

  @ParameterizedTest
  @MethodSource({ "copyDatabaseSourceCases", "copyEntityTypeSourceCases" })
  void testCopySource(EntityTypeSourceEntityType parent, EntityTypeSource source, Map<String, Object> expected) {
    EntityTypeSource result = SourceUtils.copySource(parent, source, Map.of("foo", "bar"));

    expected.forEach((k, v) -> {
      if (v == "null") { // can't put null directly in the Map.of
        assertThat(result, hasProperty(k, is(nullValue())));
      } else {
        assertThat(result, hasProperty(k, is(v)));
      }
    });
  }

  @Test
  void testCopySourceUnknownType() {
    // java 21 switch statements would make this obsolete :(
    EntityTypeSource s = new EntityTypeSource() {};
    Map<String, String> map = Map.of();
    assertThrows(IllegalStateException.class, () -> SourceUtils.copySource(null, s, map));
  }
}
