package org.folio.fqm.utils.flattening;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabaseJoin;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class FromClauseUtilsBuildTest {

  private static final EntityTypeSourceDatabase DATABASE_A_WITHOUT_JOIN = new EntityTypeSourceDatabase()
    .target("src_a")
    .alias("db_a");

  private static final EntityTypeSourceDatabase DATABASE_A_JOINED_VIA_ET = new EntityTypeSourceDatabase()
    .target("src_a")
    .alias("db_a")
    .joinedViaEntityType("root");

  private static final EntityTypeSourceDatabase DATABASE_B_JOINED_TO_A = new EntityTypeSourceDatabase()
    .alias("db_b_joined_to_a")
    .target("src_b")
    .join(new EntityTypeSourceDatabaseJoin().joinTo("db_a").type("introspective join").condition("b->a"));

  private static final EntityTypeSourceDatabase DATABASE_C_JOINED_TO_A = new EntityTypeSourceDatabase()
    .alias("db_c_joined_to_a_without_condition")
    .target("src_c")
    .join(new EntityTypeSourceDatabaseJoin().joinTo("db_a").type("retrospective join"));

  private static final EntityTypeSourceEntityType ENTITY_TYPE_ROOT = new EntityTypeSourceEntityType().alias("root");

  static List<Arguments> invalidSourceSetCases() {
    return List.of(
      // no non-joined sources
      Arguments.of(List.of()),
      Arguments.of(List.of(DATABASE_B_JOINED_TO_A)),
      // too many non-joined sources
      Arguments.of(List.of(DATABASE_A_WITHOUT_JOIN, DATABASE_B_JOINED_TO_A, DATABASE_C_JOINED_TO_A, ENTITY_TYPE_ROOT))
    );
  }

  @ParameterizedTest
  @MethodSource("invalidSourceSetCases")
  void testGetFromClauseWithInvalidSourceSet(List<EntityTypeSource> sources) {
    EntityType entityType = new EntityType().sources(sources);
    InvalidEntityTypeDefinitionException e = assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> FromClauseUtils.getFromClause(entityType, "")
    );
    assertThat(e.getMessage(), startsWith("Flattened entity type should have exactly 1 source without joins"));
  }

  @Test
  void testGetFromClause() {
    try (MockedStatic<FromClauseUtils> mocked = mockStatic(FromClauseUtils.class, Mockito.CALLS_REAL_METHODS)) {
      mocked
        .when(() -> FromClauseUtils.resolveJoins(any(), any()))
        .thenAnswer(i ->
          ((EntityType) i.getArgument(0)).getSources()
            .stream()
            .filter(EntityTypeSourceDatabase.class::isInstance)
            .toList()
        );

      String clause = FromClauseUtils.getFromClause(
        new EntityType()
          .sources(List.of(DATABASE_A_JOINED_VIA_ET, DATABASE_B_JOINED_TO_A, DATABASE_C_JOINED_TO_A, ENTITY_TYPE_ROOT)),
        null
      );

      assertThat(
        clause,
        is(
          "src_a \"db_a\" introspective join src_b \"db_b_joined_to_a\" ON b->a retrospective join src_c \"db_c_joined_to_a_without_condition\""
        )
      );
    }
  }

  @Test
  void testGetFromClauseCrossTenant() {
    try (MockedStatic<FromClauseUtils> mocked = mockStatic(FromClauseUtils.class, Mockito.CALLS_REAL_METHODS)) {
      mocked
        .when(() -> FromClauseUtils.resolveJoins(any(), any()))
        .thenAnswer(i ->
          ((EntityType) i.getArgument(0)).getSources()
            .stream()
            .filter(EntityTypeSourceDatabase.class::isInstance)
            .toList()
        );

      String clause = FromClauseUtils.getFromClause(
        new EntityType()
          .sources(List.of(DATABASE_A_JOINED_VIA_ET, DATABASE_B_JOINED_TO_A, DATABASE_C_JOINED_TO_A, ENTITY_TYPE_ROOT)),
        "member1"
      );

      assertThat(
        clause,
        is(
          "member1_mod_fqm_manager.src_a \"db_a\" introspective join member1_mod_fqm_manager.src_b \"db_b_joined_to_a\" ON b->a retrospective join member1_mod_fqm_manager.src_c \"db_c_joined_to_a_without_condition\""
        )
      );
    }
  }
}
