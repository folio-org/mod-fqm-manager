package org.folio.fqm.utils.flattening;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.UUID;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabaseJoin;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.querytool.domain.dto.Join;
import org.folio.querytool.domain.dto.JoinCustom;
import org.folio.querytool.domain.dto.JoinEqualityCastUUID;
import org.folio.querytool.domain.dto.JoinEqualitySimple;
import org.hibernate.query.sqm.EntityTypeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FromClauseUtilsComputeTest {

  private static final EntityTypeColumn COLUMN_A = new EntityTypeColumn()
    .name("a")
    .valueGetter("A")
    .originalEntityTypeId(UUID.fromString("00000000-0000-0000-0000-000000000000"));
  private static final EntityTypeColumn COLUMN_B = new EntityTypeColumn()
    .name("b")
    .valueGetter("B")
    .originalEntityTypeId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

  private static final EntityTypeColumn COLUMN_A_WITH_JOINS_TO_B = COLUMN_A
    .toBuilder()
    .joinsTo(
      List.of(
        new JoinCustom()
          .sql(":this -> :that")
          .targetId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
          .targetField("b")
          .direction(Join.DirectionEnum.LEFT)
      )
    )
    .build();
  private static final EntityTypeColumn COLUMN_A_WITH_JOINS_TO_WRONG_FIELD = COLUMN_A
    .toBuilder()
    .joinsTo(
      List.of(
        new JoinCustom()
          .sql(":this -> :that")
          .targetId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
          .targetField("zzzz")
          .direction(Join.DirectionEnum.LEFT)
      )
    )
    .build();
  private static final EntityTypeColumn COLUMN_A_WITH_JOINS_TO_WRONG_ET = COLUMN_A
    .toBuilder()
    .joinsTo(
      List.of(
        new JoinCustom()
          .sql(":this -> :that")
          .targetId(UUID.fromString("6061b4b1-b188-5e31-a431-eeb4dd35eca4"))
          .targetField("b")
          .direction(Join.DirectionEnum.LEFT)
      )
    )
    .build();
  private static final EntityTypeColumn COLUMN_A_WITH_JOINS_TO_WRONG_EVERYTHING = COLUMN_A
    .toBuilder()
    .joinsTo(
      List.of(
        new JoinCustom()
          .sql(":this -> :that")
          .targetId(UUID.fromString("6061b4b1-b188-5e31-a431-eeb4dd35eca4"))
          .targetField("zzzz")
          .direction(Join.DirectionEnum.LEFT)
      )
    )
    .build();
  private static final EntityTypeColumn COLUMN_B_WITH_JOINS_TO_A = COLUMN_B
    .toBuilder()
    .joinsTo(
      List.of(
        new JoinCustom()
          .sql(":this -> :that")
          .targetId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
          .targetField("a")
          .direction(Join.DirectionEnum.LEFT)
      )
    )
    .build();

  static List<Arguments> computeJoinDirectionCases() {
    return List.of(
      // starting direction, if reversed, expected direction
      Arguments.of(Join.DirectionEnum.LEFT, false, "LEFT JOIN"),
      Arguments.of(Join.DirectionEnum.RIGHT, false, "RIGHT JOIN"),
      Arguments.of(Join.DirectionEnum.INNER, false, "INNER JOIN"),
      Arguments.of(Join.DirectionEnum.FULL, false, "FULL JOIN"),
      Arguments.of(Join.DirectionEnum.LEFT, true, "RIGHT JOIN"),
      Arguments.of(Join.DirectionEnum.RIGHT, true, "LEFT JOIN"),
      Arguments.of(Join.DirectionEnum.INNER, true, "INNER JOIN"),
      Arguments.of(Join.DirectionEnum.FULL, true, "FULL JOIN")
    );
  }

  @ParameterizedTest
  @MethodSource("computeJoinDirectionCases")
  void testComputeJoinDirection(Join.DirectionEnum direction, boolean reversed, String expected) {
    EntityTypeSourceDatabaseJoin join = FromClauseUtils.computeJoin(
      COLUMN_A,
      COLUMN_B,
      new JoinCustom().sql(":this -> :that").direction(direction),
      reversed
    );

    assertThat(join.getType(), is(expected));
  }

  static List<Arguments> computeJoinConditionCases() {
    return List.of(
      // join, expected (with A and B as :this and :that)
      Arguments.of(new JoinCustom().sql("custom from :this to :that"), "custom from A to B"),
      Arguments.of(new JoinEqualitySimple(), "A = B"),
      Arguments.of(new JoinEqualityCastUUID(), "(A)::uuid = (B)::uuid")
    );
  }

  @ParameterizedTest
  @MethodSource("computeJoinConditionCases")
  void testComputeJoinCondition(Join join, String expected) {
    EntityTypeSourceDatabaseJoin computed = FromClauseUtils.computeJoin(
      COLUMN_A,
      COLUMN_B,
      join.direction(Join.DirectionEnum.INNER),
      false
    );

    assertThat(computed.getCondition(), is(expected));
  }

  @Test
  void testComputeJoinUnknownType() {
    Join join = new Join() {};
    assertThrows(EntityTypeException.class, () -> FromClauseUtils.computeJoin(COLUMN_A, COLUMN_B, join, false));
  }

  @Test
  void testComputeJoinCorrectDirection() {
    EntityTypeSourceDatabaseJoin join = FromClauseUtils.computeJoin(
      EntityType.builder().columns(List.of(COLUMN_A_WITH_JOINS_TO_B, COLUMN_B)).build(),
      EntityTypeSourceEntityType.builder().sourceField("a").targetField("b").build()
    );

    assertThat(join.getCondition(), is("A -> B"));
    assertThat(join.getType(), is("LEFT JOIN"));
  }

  @Test
  void testComputeJoinReversedDirection() {
    EntityTypeSourceDatabaseJoin join = FromClauseUtils.computeJoin(
      EntityType.builder().columns(List.of(COLUMN_A_WITH_JOINS_TO_B, COLUMN_B)).build(),
      EntityTypeSourceEntityType.builder().sourceField("b").targetField("a").build()
    );

    assertThat(join.getCondition(), is("A -> B"));
    assertThat(join.getType(), is("RIGHT JOIN"));
  }

  static List<Arguments> invalidColumnPairs() {
    return List.of(
      Arguments.of(COLUMN_A, COLUMN_B),
      Arguments.of(COLUMN_A_WITH_JOINS_TO_WRONG_FIELD, COLUMN_B),
      Arguments.of(COLUMN_A_WITH_JOINS_TO_WRONG_ET, COLUMN_B),
      Arguments.of(COLUMN_A_WITH_JOINS_TO_WRONG_EVERYTHING, COLUMN_B),
      Arguments.of(COLUMN_A_WITH_JOINS_TO_B, COLUMN_B_WITH_JOINS_TO_A)
    );
  }

  @ParameterizedTest
  @MethodSource("invalidColumnPairs")
  void testComputeJoinInvalid(EntityTypeColumn a, EntityTypeColumn b) {
    EntityType entityType = EntityType.builder().columns(List.of(a, b)).build();
    EntityTypeSourceEntityType source = EntityTypeSourceEntityType.builder().sourceField("a").targetField("b").build();

    assertThrows(InvalidEntityTypeDefinitionException.class, () -> FromClauseUtils.computeJoin(entityType, source));
  }
}
