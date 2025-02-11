package org.folio.fqm.utils.flattening;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabaseJoin;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FromClauseUtilsOrderTest {

  private static final EntityTypeSourceDatabase DATABASE_A_WITHOUT_JOIN = new EntityTypeSourceDatabase().alias("db_a");

  private static final EntityTypeSourceDatabase DATABASE_B_JOINED_TO_A = new EntityTypeSourceDatabase()
    .alias("db_b_joined_to_a")
    .join(new EntityTypeSourceDatabaseJoin().joinTo("db_a"));

  private static final EntityTypeSourceDatabase DATABASE_C_JOINED_TO_A = new EntityTypeSourceDatabase()
    .alias("db_c_joined_to_a")
    .join(new EntityTypeSourceDatabaseJoin().joinTo("db_a"));

  // composite base ET
  private static final EntityTypeSourceEntityType ENTITY_TYPE_ROOT = new EntityTypeSourceEntityType()
    .alias("et_no_join");

  // single DB under base ET
  private static final EntityTypeSourceDatabase DATABASE_UNDER_ROOT_ET_WITHOUT_JOIN = new EntityTypeSourceDatabase()
    .alias("db_under_root_et_with_no_join")
    .joinedViaEntityType("et_no_join");

  // composite secondary ET
  private static final EntityTypeSourceEntityType ENTITY_TYPE_JOINED = new EntityTypeSourceEntityType()
    .alias("et_joined")
    .sourceField("et_no_join.foo");

  // this join came from an ET's join rather than the ET definition, and is missing the joinTo
  private static final EntityTypeSourceDatabase DATABASE_DERIVED_JOIN = new EntityTypeSourceDatabase()
    .alias("db_derived_join")
    .joinedViaEntityType("et_joined")
    .join(new EntityTypeSourceDatabaseJoin());

  // single DB under composite root ET
  private static final EntityTypeSourceDatabase DATABASE_UNDER_JOINED_ET = new EntityTypeSourceDatabase()
    .alias("db_under_joined_et")
    .joinedViaEntityType("et_joined");

  static List<Arguments> orderTestCases() {
    return List.of(
      // DB sources with joinTo MUST come after the joinTo'd source
      Arguments.of(List.of(DATABASE_A_WITHOUT_JOIN, DATABASE_B_JOINED_TO_A), List.of("db_a", "db_b_joined_to_a")),
      Arguments.of(List.of(DATABASE_B_JOINED_TO_A, DATABASE_A_WITHOUT_JOIN), List.of("db_a", "db_b_joined_to_a")),
      // DB sources B/C don't have any relative ordering
      Arguments.of(
        List.of(DATABASE_A_WITHOUT_JOIN, DATABASE_B_JOINED_TO_A, DATABASE_C_JOINED_TO_A),
        List.of("db_a", "db_b_joined_to_a", "db_c_joined_to_a")
      ),
      Arguments.of(
        List.of(DATABASE_B_JOINED_TO_A, DATABASE_A_WITHOUT_JOIN, DATABASE_C_JOINED_TO_A),
        List.of("db_a", "db_b_joined_to_a", "db_c_joined_to_a")
      ),
      Arguments.of(
        List.of(DATABASE_C_JOINED_TO_A, DATABASE_B_JOINED_TO_A, DATABASE_A_WITHOUT_JOIN),
        List.of("db_a", "db_c_joined_to_a", "db_b_joined_to_a")
      ),
      // entity types joined via another MUST come after the joinedVia
      Arguments.of(
        List.of(ENTITY_TYPE_ROOT, DATABASE_UNDER_ROOT_ET_WITHOUT_JOIN),
        List.of("et_no_join", "db_under_root_et_with_no_join")
      ),
      Arguments.of(
        List.of(DATABASE_UNDER_ROOT_ET_WITHOUT_JOIN, ENTITY_TYPE_ROOT),
        List.of("et_no_join", "db_under_root_et_with_no_join")
      ),
      // entity types joined to another MUST come after the other (sourceField)
      Arguments.of(
        List.of(DATABASE_UNDER_JOINED_ET, ENTITY_TYPE_JOINED, ENTITY_TYPE_ROOT),
        List.of("et_no_join", "et_joined", "db_under_joined_et")
      ),
      // all of it together
      Arguments.of(
        List.of(ENTITY_TYPE_ROOT, DATABASE_DERIVED_JOIN, ENTITY_TYPE_JOINED),
        List.of("et_no_join", "et_joined", "db_derived_join")
      )
    );
  }

  @ParameterizedTest
  @MethodSource("orderTestCases")
  void testOrderJoins(List<EntityTypeSource> sources, List<String> expected) {
    List<EntityTypeSource> ordered = FromClauseUtils.orderSources(sources);

    List<String> result = ordered.stream().map(EntityTypeSource::getAlias).toList();

    assertThat(result, is(expected));
  }
}
