package org.folio.fqm.utils.flattening;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Map;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabaseJoin;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.querytool.domain.dto.JoinDirection;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SourceUtilsMiscTest {

  static List<Arguments> flipDirectionTestCases() {
    // input, expected
    return List.of(
      Arguments.of(null, null),
      Arguments.of(JoinDirection.INNER, JoinDirection.INNER),
      Arguments.of(JoinDirection.LEFT, JoinDirection.RIGHT),
      Arguments.of(JoinDirection.RIGHT, JoinDirection.LEFT)
    );
  }

  @ParameterizedTest
  @MethodSource("flipDirectionTestCases")
  void testFlipDirection(JoinDirection input, JoinDirection expected) {
    assertThat(SourceUtils.flipDirection(input), is(expected));
  }

  static List<Arguments> isJoinedTestCases() {
    // input, expected
    return List.of(
      // joinedViaEntityType always means joined
      Arguments.of(new EntityTypeSourceDatabase().joinedViaEntityType("foo"), true),
      Arguments.of(new EntityTypeSourceEntityType().joinedViaEntityType("foo"), true),
      // db is joined if join is present
      Arguments.of(new EntityTypeSourceDatabase().join(new EntityTypeSourceDatabaseJoin()), true),
      Arguments.of(new EntityTypeSourceDatabase().join(null), false),
      // et is joined if sourceField is present (sourceField and targetField must be present together)
      Arguments.of(new EntityTypeSourceEntityType().sourceField("foo"), true),
      Arguments.of(new EntityTypeSourceEntityType().sourceField(null), false)
    );
  }

  @ParameterizedTest
  @MethodSource("isJoinedTestCases")
  void testIsJoined(EntityTypeSource source, boolean expected) {
    assertThat(SourceUtils.isJoined(source), is(expected));
  }

  static List<Arguments> findJoiningEntityTypeTestCases() {
    // input, expected
    return List.of(
      Arguments.of(new EntityTypeSourceDatabase().joinedViaEntityType("notJoined"), "notJoined"),
      Arguments.of(
        new EntityTypeSourceDatabase().joinedViaEntityType("joinedButHasSourceField"),
        "joinedButHasSourceField"
      ),
      Arguments.of(new EntityTypeSourceDatabase().joinedViaEntityType("joinedWithoutSourceField"), "sourceFieldOnly")
    );
  }

  @ParameterizedTest
  @MethodSource("findJoiningEntityTypeTestCases")
  void testFindJoiningEntityType(EntityTypeSourceDatabase source, String expected) {
    Map<String, EntityTypeSourceEntityType> sourceMap = Map.of(
      "notJoined",
      new EntityTypeSourceEntityType(),
      "joinedButHasSourceField",
      new EntityTypeSourceEntityType().joinedViaEntityType("other").sourceField("foo"),
      "joinedWithoutSourceField",
      new EntityTypeSourceEntityType().joinedViaEntityType("sourceFieldOnly"),
      "sourceFieldOnly",
      new EntityTypeSourceEntityType().sourceField("foo")
    );

    assertThat(SourceUtils.findJoiningEntityType(source, sourceMap), is(sourceMap.get(expected)));
  }
}
