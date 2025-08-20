package org.folio.fqm.utils.flattening;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.stream.Stream;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.fqm.utils.flattening.FromClauseUtils.NecessaryJoin;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabaseJoin;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.querytool.domain.dto.JoinDirection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FromClauseUtilsFindNecessaryJoinsTest {

  static Stream<Arguments> noNecessaryJoinTestCases() {
    return List
      .of(
        // entity type sources never result in a necessary join by themselves
        new EntityTypeSourceEntityType(),
        new EntityTypeSourceEntityType().sourceField("foo").targetField("bar"),
        new EntityTypeSourceEntityType().joinedViaEntityType("other").sourceField("baz"),
        // db sources with joins are already joined, so no necessary resolution
        new EntityTypeSourceDatabase().join(new EntityTypeSourceDatabaseJoin()),
        // no join needed (from this side)
        new EntityTypeSourceDatabase().joinedViaEntityType(null),
        // parent is not joined, so no join needed (from this side)
        new EntityTypeSourceDatabase().joinedViaEntityType("parentWithNoSourceField")
      )
      .stream()
      .map(Arguments::of);
  }

  @ParameterizedTest
  @MethodSource("noNecessaryJoinTestCases")
  void testNoNecessaryJoins(EntityTypeSource source) {
    assertThat(
      FromClauseUtils.findNecessaryJoins(
        new EntityType().sources(List.of(source, new EntityTypeSourceEntityType().alias("parentWithNoSourceField")))
      ),
      is(empty())
    );
  }

  @Test
  void testNecessaryJoin() {
    EntityType entity = new EntityType()
      .addSourcesItem(
        new EntityTypeSourceEntityType()
          .alias("a")
          .sourceField("b.bar")
          .targetField("foo")
          .overrideJoinDirection(JoinDirection.LEFT)
      )
      .addSourcesItem(new EntityTypeSourceEntityType().alias("b"))
      .addSourcesItem(new EntityTypeSourceDatabase().alias("a.db").joinedViaEntityType("a"))
      .addSourcesItem(new EntityTypeSourceDatabase().alias("b.db").joinedViaEntityType("b"))
      .addColumnsItem(new EntityTypeColumn().name("a.foo").sourceAlias("a.db"))
      .addColumnsItem(new EntityTypeColumn().name("b.bar").sourceAlias("b.db"));

    List<NecessaryJoin> necessaryJoins = FromClauseUtils.findNecessaryJoins(entity);
    assertThat(necessaryJoins, hasSize(1));

    NecessaryJoin join = necessaryJoins.get(0);
    assertThat(join.columnA(), is(EntityTypeUtils.findColumnByName(entity, "b.bar")));
    assertThat(join.sourceA(), is(EntityTypeUtils.findSourceByAlias(entity, "b.db", null)));
    assertThat(join.columnB(), is(EntityTypeUtils.findColumnByName(entity, "a.foo")));
    assertThat(join.sourceB(), is(EntityTypeUtils.findSourceByAlias(entity, "a.db", null)));
    assertThat(join.overrideJoinDirection(), is(JoinDirection.LEFT));
    assertThat(join.toString(), is("b.bar[b.db] <-> a.foo[a.db]"));

    assertThat(join.flip().columnA(), is(EntityTypeUtils.findColumnByName(entity, "a.foo")));
    assertThat(join.flip().sourceA(), is(EntityTypeUtils.findSourceByAlias(entity, "a.db", null)));
    assertThat(join.flip().columnB(), is(EntityTypeUtils.findColumnByName(entity, "b.bar")));
    assertThat(join.flip().sourceB(), is(EntityTypeUtils.findSourceByAlias(entity, "b.db", null)));
    assertThat(join.flip().overrideJoinDirection(), is(JoinDirection.RIGHT));
  }
}
