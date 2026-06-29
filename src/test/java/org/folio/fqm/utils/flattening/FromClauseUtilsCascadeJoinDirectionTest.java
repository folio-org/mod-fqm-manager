package org.folio.fqm.utils.flattening;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.util.List;
import java.util.UUID;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.querytool.domain.dto.Join;
import org.folio.querytool.domain.dto.JoinDirection;
import org.folio.querytool.domain.dto.JoinEqualitySimple;
import org.folio.querytool.domain.dto.StringType;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link FromClauseUtils#getFromClause} for the nested-composite LEFT-join scenario:
 * an outer composite entity type LEFT-joins an inner composite entity type, and the inner's
 * own joins must not be flipped to RIGHT during resolution.
 */
class FromClauseUtilsCascadeJoinDirectionTest {

  private static final String TENANT_ID = "tenant_01";
  private static final UUID SIMPLE_AOL_ID = UUID.fromString("00000000-0000-0000-0000-00000000a01a");
  private static final UUID SIMPLE_PO_ID = UUID.fromString("00000000-0000-0000-0000-00000000d000");
  private static final UUID SIMPLE_POL_ID = UUID.fromString("00000000-0000-0000-0000-0000000d01a0");

  @Test
  void cascadeFlagPreservesLeftJoinThroughInnerComposite() {
    EntityType flattened = buildFlattened(true);

    String fromClause = FromClauseUtils.getFromClause(flattened, TENANT_ID);

    // AOL must be the base (un-joined) table.
    assertThat(fromClause, containsString("src_aol \"aol.aol\""));
    // POL is reached via the outer composite's LEFT override.
    assertThat(fromClause, containsString("LEFT JOIN " + TENANT_ID + "_mod_fqm_manager.src_pol"));
    // PO must also be LEFT JOINed — the cascade carries the LEFT through the inner composite.
    assertThat(
      "with cascadeJoinDirection=true, PO should be LEFT JOINed to preserve upstream nullability",
      fromClause,
      containsString("LEFT JOIN " + TENANT_ID + "_mod_fqm_manager.src_po")
    );
    assertThat(
      "with cascadeJoinDirection=true, PO must not be RIGHT JOINed",
      fromClause,
      not(containsString("RIGHT JOIN " + TENANT_ID + "_mod_fqm_manager.src_po"))
    );
  }

  @Test
  void noCascadeFlagPreservesLegacyRightJoinBehavior() {
    EntityType flattened = buildFlattened(false);

    String fromClause = FromClauseUtils.getFromClause(flattened, TENANT_ID);

    assertThat(fromClause, containsString("src_aol \"aol.aol\""));
    assertThat(fromClause, containsString("LEFT JOIN " + TENANT_ID + "_mod_fqm_manager.src_pol"));
    assertThat(
      "without cascadeJoinDirection, the legacy RIGHT JOIN on PO must remain (backward compat)",
      fromClause,
      containsString("RIGHT JOIN " + TENANT_ID + "_mod_fqm_manager.src_po")
    );
  }

  /**
   * Builds the post-flattening structure for:
   * <pre>
   *   outer composite (AOL + inner-composite[LEFT, cascade?])
   *      inner composite (PO + POL[LEFT joined to PO])
   * </pre>
   *
   * <p>Aliases follow the flattening conventions. The {@code cascade} flag is set on every entity-type source that
   * descends from a cascading parent, which mirrors what {@link SourceUtils#copySource} produces.
   */
  private static EntityType buildFlattened(boolean cascade) {
    EntityTypeSourceDatabase aolDb = new EntityTypeSourceDatabase()
      .type("db")
      .alias("aol.aol")
      .target("src_aol");

    EntityTypeSourceEntityType innerEt = new EntityTypeSourceEntityType()
      .type("entity-type")
      .alias("inner")
      .targetId(UUID.randomUUID())
      .sourceField("aol.pol_fk")
      .targetField("pol.id")
      .overrideJoinDirection(JoinDirection.LEFT)
      .cascadeJoinDirection(cascade);

    // inner.po — inner-composite root, no sourceField (un-joined inside inner).
    EntityTypeSourceEntityType innerPoEt = new EntityTypeSourceEntityType()
      .type("entity-type")
      .alias("inner.po")
      .targetId(SIMPLE_PO_ID)
      .joinedViaEntityType("inner")
      .cascadeJoinDirection(cascade);

    EntityTypeSourceDatabase innerPoDb = new EntityTypeSourceDatabase()
      .type("db")
      .alias("inner.po.po")
      .target("src_po")
      .joinedViaEntityType("inner.po");

    // inner.pol — LEFT-joined to inner.po inside the inner composite. Cascade is propagated
    // from the outer's `inner` source via SourceUtils.copySource.
    EntityTypeSourceEntityType innerPolEt = new EntityTypeSourceEntityType()
      .type("entity-type")
      .alias("inner.pol")
      .targetId(SIMPLE_POL_ID)
      .joinedViaEntityType("inner")
      .sourceField("inner.po.id")
      .targetField("po_fk")
      .overrideJoinDirection(JoinDirection.LEFT)
      .cascadeJoinDirection(cascade);

    EntityTypeSourceDatabase innerPolDb = new EntityTypeSourceDatabase()
      .type("db")
      .alias("inner.pol.pol")
      .target("src_pol")
      .joinedViaEntityType("inner.pol");

    EntityTypeColumn aolId = column("aol.id", "aol.aol", "\"aol.aol\".aol_id", SIMPLE_AOL_ID, null);
    EntityTypeColumn aolPolFk = column(
      "aol.pol_fk",
      "aol.aol",
      "\"aol.aol\".aol_pol_fk",
      SIMPLE_AOL_ID,
      joinsTo(SIMPLE_POL_ID, "id")
    );
    EntityTypeColumn innerPoId = column(
      "inner.po.id",
      "inner.po.po",
      "\"inner.po.po\".po_id",
      SIMPLE_PO_ID,
      null
    );
    EntityTypeColumn innerPolId = column(
      "inner.pol.id",
      "inner.pol.pol",
      "\"inner.pol.pol\".pol_id",
      SIMPLE_POL_ID,
      null
    );
    EntityTypeColumn innerPolPoFk = column(
      "inner.pol.po_fk",
      "inner.pol.pol",
      "\"inner.pol.pol\".pol_po_fk",
      SIMPLE_POL_ID,
      joinsTo(SIMPLE_PO_ID, "id")
    );

    return new EntityType()
      .id(UUID.randomUUID().toString())
      .name("composite_outer_agreement_to_orders")
      .sources(List.of(aolDb, innerEt, innerPoEt, innerPoDb, innerPolEt, innerPolDb))
      .columns(List.of(aolId, aolPolFk, innerPoId, innerPolId, innerPolPoFk));
  }

  private static EntityTypeColumn column(
    String name,
    String sourceAlias,
    String valueGetter,
    UUID originalEntityTypeId,
    List<Join> joinsTo
  ) {
    EntityTypeColumn column = new EntityTypeColumn()
      .name(name)
      .sourceAlias(sourceAlias)
      .valueGetter(valueGetter)
      .dataType(new StringType().dataType("stringType"))
      .originalEntityTypeId(originalEntityTypeId);
    if (joinsTo != null) {
      joinsTo.forEach(column::addJoinsToItem);
    }
    return column;
  }

  private static List<Join> joinsTo(UUID targetId, String targetField) {
    return List.of(
      new JoinEqualitySimple()
        .targetId(targetId)
        .targetField(targetField)
        .type("equality-simple")
    );
  }

}
