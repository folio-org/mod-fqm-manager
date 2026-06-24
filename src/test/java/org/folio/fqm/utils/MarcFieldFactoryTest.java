package org.folio.fqm.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.folio.fql.model.AndCondition;
import org.folio.fql.model.ContainsCondition;
import org.folio.fql.model.field.FqlField;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.fqm.utils.MarcFieldFactory.MarcQueryContext;
import org.folio.querytool.domain.dto.EntityDataType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.MarcType;
import org.junit.jupiter.api.Test;

class MarcFieldFactoryTest {

  // Mirrors the production simple_srs_record config: MARC is correlated on record_lb.id (the per-generation
  // id stored in marc_indexers.marc_id), NOT matched_id.
  private static final String MARC_RECORD_ID_GETTER = "\"record_lb\".id";

  @Test
  void shouldRecognizeSupportedMarcFieldNames() {
    assertTrue(MarcFieldFactory.isMarcFieldName("marc_245_a"));
    assertTrue(MarcFieldFactory.isMarcFieldName("marc_650_0"));
  }

  @Test
  void shouldAcceptUppercaseFieldNamesAndNormalizeSubfield() {
    assertTrue(MarcFieldFactory.isMarcFieldName("MARC_245_A"));

    MarcFieldFactory.MarcFieldName parsed = MarcFieldFactory.parse("MARC_245_A").orElseThrow();
    // Original name is preserved (so it matches the name referenced in the query)...
    assertEquals("MARC_245_A", parsed.fieldName());
    // ...but the subfield is normalized to lower case to match marc_indexers storage.
    assertEquals("245", parsed.tag());
    assertEquals("a", parsed.subfield());
    assertEquals("245$a", parsed.labelAlias());
  }

  @Test
  void shouldRejectUnsupportedMarcFieldNames() {
    assertFalse(MarcFieldFactory.isMarcFieldName("marc_245"));
    assertFalse(MarcFieldFactory.isMarcFieldName("marc_245_ind1"));
    assertFalse(MarcFieldFactory.isMarcFieldName("marc_245_aa"));
    assertFalse(MarcFieldFactory.isMarcFieldName("245_a"));
  }

  @Test
  void shouldCreateSubfieldSyntheticColumn() {
    EntityTypeColumn column = MarcFieldFactory.createSyntheticColumn(entityTypeWithMarcSupport(), "marc_245_a").orElseThrow();

    assertEquals("marc_245_a", column.getName());
    assertEquals("245$a", column.getLabelAlias());
    assertInstanceOf(MarcType.class, column.getDataType());
    assertEquals("lower(:value)", column.getValueFunction());
    assertSqlEquals(expectedSubfieldValueGetter("245", "a"), column.getValueGetter());
    assertEquals("lower(marc.value)", column.getFilterValueGetter());
  }

  @Test
  void shouldInterpolateTenantIdWhenProvided() {
    EntityTypeColumn column = MarcFieldFactory.createSyntheticColumn(
      entityTypeWithMarcSupport(),
      "marc_245_a",
      "diku"
    ).orElseThrow();

    assertTrue(column.getValueGetter().contains("diku_mod_fqm_manager.src_srs_marc_indexers"));
    assertFalse(column.getValueGetter().contains("${tenant_id}"));
  }

  @Test
  void shouldReturnEmptyForInvalidMarcFieldName() {
    assertEquals(Optional.empty(), MarcFieldFactory.createSyntheticColumn(entityTypeWithMarcSupport(), "marc_245"));
  }

  @Test
  void shouldAddSyntheticColumnsForReferencedMarcFields() {
    EntityType entityType = MarcFieldFactory.addSyntheticColumns(
      entityTypeWithMarcSupport(),
      List.of("marc_245_a", "field_that_is_not_marc"),
      "diku"
    );

    assertEquals(
      List.of("matched_id", "marc", "marc_245_a"),
      entityType.getColumns().stream().map(EntityTypeColumn::getName).toList()
    );
  }

  @Test
  void shouldNotMutateOriginalEntityTypeWhenAddingSyntheticColumns() {
    EntityType originalEntityType = entityTypeWithMarcSupport();

    EntityType augmentedEntityType = MarcFieldFactory.addSyntheticColumns(
      originalEntityType,
      List.of("marc_245_a"),
      "diku"
    );

    assertEquals(
      List.of("matched_id", "marc"),
      originalEntityType.getColumns().stream().map(EntityTypeColumn::getName).toList()
    );
    assertEquals(
      List.of("matched_id", "marc", "marc_245_a"),
      augmentedEntityType.getColumns().stream().map(EntityTypeColumn::getName).toList()
    );
  }

  @Test
  void shouldCollectReferencedFieldNamesFromFqlCondition() {
    Set<String> fieldNames = MarcFieldFactory.getReferencedFieldNames(new AndCondition(List.of(
      new ContainsCondition(new FqlField("marc_245_a"), "Shakespeare"),
      new ContainsCondition(new FqlField("record_type"), "MARC_BIB")
    )));

    assertEquals(Set.of("marc_245_a", "record_type"), fieldNames);
  }

  @Test
  void shouldCollectReferencedMarcFieldNamesFromRawQuery() {
    String fqlQuery = """
      {"$and": [{"marc_245_a": {"$contains": "Shakespeare"}}, {"external_hrid": {"$eq": "123"}}]}
      """;

    assertEquals(Set.of("marc_245_a"), MarcFieldFactory.getReferencedMarcFieldNames(fqlQuery));
  }

  @Test
  void shouldThrowWhenMarcPlaceholderOmitsCorrelationValueGetter() {
    EntityType entityType = new EntityType()
      .id(UUID.randomUUID().toString())
      .name("test")
      .columns(List.of(
        new EntityTypeColumn()
          .name("marc")
          .dataType(new MarcType().dataType("marcType"))
      ));

    assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> MarcFieldFactory.createSyntheticColumn(entityType, "marc_245_a")
    );
  }

  @Test
  void shouldAddSyntheticColumnsFromFqlConditionWithoutTenant() {
    EntityType entityType = MarcFieldFactory.addSyntheticColumns(
      entityTypeWithMarcSupport(),
      new ContainsCondition(new FqlField("marc_245_a"), "Shakespeare"),
      null // no tenant
    );

    EntityTypeColumn marc245a = entityType.getColumns().stream()
      .filter(column -> "marc_245_a".equals(column.getName()))
      .findFirst()
      .orElseThrow();
    // No tenant was supplied, so the placeholder remains un-interpolated.
    assertTrue(marc245a.getValueGetter().contains("${tenant_id}"));
  }

  @Test
  void shouldAddSyntheticColumnsFromCollectionWithoutTenant() {
    EntityType entityType = MarcFieldFactory.addSyntheticColumns(
      entityTypeWithMarcSupport(),
      List.of("marc_245_a"),
      null // no tenant
    );

    EntityTypeColumn marc245a = entityType.getColumns().stream()
      .filter(column -> "marc_245_a".equals(column.getName()))
      .findFirst()
      .orElseThrow();
    assertTrue(marc245a.getValueGetter().contains("${tenant_id}"));
  }

  @Test
  void shouldAddSyntheticColumnsFromRawQueryString() {
    EntityType entityType = MarcFieldFactory.addSyntheticColumns(
      entityTypeWithMarcSupport(),
      "{\"marc_245_a\": {\"$contains\": \"Shakespeare\"}}",
      "diku"
    );

    assertEquals(
      List.of("matched_id", "marc", "marc_245_a"),
      entityType.getColumns().stream().map(EntityTypeColumn::getName).toList()
    );
  }

  @Test
  void shouldReturnEmptySetForNullOrBlankRawQuery() {
    assertEquals(Set.of(), MarcFieldFactory.getReferencedMarcFieldNames(null));
    assertEquals(Set.of(), MarcFieldFactory.getReferencedMarcFieldNames("   "));
  }

  @Test
  void shouldReturnEmptySetForNullOrUnsupportedFqlCondition() {
    // null is neither a FieldCondition nor an AndCondition, exercising the fall-through return.
    assertEquals(Set.of(), MarcFieldFactory.getReferencedFieldNames(null));
  }

  @Test
  void shouldReturnEntityTypeUnchangedWhenNoFieldNamesToAdd() {
    EntityType entityType = entityTypeWithMarcSupport();

    // null collection
    assertEquals(entityType, MarcFieldFactory.addSyntheticColumns(entityType, (Collection<String>) null, "diku"));
    // empty collection
    assertEquals(entityType, MarcFieldFactory.addSyntheticColumns(entityType, List.of(), "diku"));

    // entity type with null columns
    EntityType noColumns = new EntityType().id(UUID.randomUUID().toString()).name("no-columns").columns(null);
    EntityType result = MarcFieldFactory.addSyntheticColumns(noColumns, List.of("marc_245_a"), "diku");
    assertNull(result.getColumns());
  }

  @Test
  void shouldSkipNullAndAlreadyPresentFieldNames() {
    // "marc" is already present (the placeholder), null should be skipped, only marc_245_a is added.
    EntityType entityType = MarcFieldFactory.addSyntheticColumns(
      entityTypeWithMarcSupport(),
      Arrays.asList("marc", null, "marc_245_a"),
      "diku"
    );

    assertEquals(
      List.of("matched_id", "marc", "marc_245_a"),
      entityType.getColumns().stream().map(EntityTypeColumn::getName).toList()
    );
  }

  @Test
  void shouldReturnEmptyWhenEntityTypeHasNoMarcPlaceholder() {
    EntityType entityType = new EntityType()
      .id(UUID.randomUUID().toString())
      .name("no-marc")
      .columns(List.of(
        new EntityTypeColumn().name("matched_id").dataType(new EntityDataType().dataType("rangedUUIDType"))
      ));

    assertEquals(Optional.empty(), MarcFieldFactory.createSyntheticColumn(entityType, "marc_245_a"));
  }

  @Test
  void shouldThrowWhenMarcPlaceholderValueGetterIsBlank() {
    EntityType entityType = new EntityType()
      .id(UUID.randomUUID().toString())
      .name("blank-getter")
      .columns(List.of(
        new EntityTypeColumn().name("marc").dataType(new MarcType().dataType("marcType")).valueGetter("   ")
      ));

    assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> MarcFieldFactory.createSyntheticColumn(entityType, "marc_245_a")
    );
  }

  @Test
  void shouldKeepPlaceholderUninterpolatedForBlankTenant() {
    EntityTypeColumn column = MarcFieldFactory.createSyntheticColumn(
      entityTypeWithMarcSupport(),
      "marc_245_a",
      "   "
    ).orElseThrow();

    assertTrue(column.getValueGetter().contains("${tenant_id}"));
  }

  @Test
  void shouldBuildQueryContextAndItsClauses() {
    EntityType entityType = MarcFieldFactory.addSyntheticColumns(entityTypeWithMarcSupport(), List.of("marc_245_a"), "diku");

    MarcQueryContext context = MarcFieldFactory.createQueryContext(entityType, "marc_245_a").orElseThrow();

    assertEquals("245", context.marcField().tag());
    assertEquals("a", context.marcField().subfield());
    assertEquals("diku_mod_fqm_manager.src_srs_marc_indexers", context.tableName());
    assertEquals(MARC_RECORD_ID_GETTER, context.marcIdGetter());
    assertEquals("lower(marc.value)", context.filterValueGetter());

    assertEquals(
      "marc.marc_id = \"record_lb\".id and marc.field_no = '245' and marc.subfield_no = 'a'",
      context.whereClause()
    );
    assertEquals(
      "exists (select 1 from diku_mod_fqm_manager.src_srs_marc_indexers marc where "
        + "marc.marc_id = \"record_lb\".id and marc.field_no = '245' and marc.subfield_no = 'a' "
        + "and lower(marc.value) = {0})",
      context.existsClause("=", true)
    );
    assertEquals(
      "not exists (select 1 from diku_mod_fqm_manager.src_srs_marc_indexers marc where "
        + "marc.marc_id = \"record_lb\".id and marc.field_no = '245' and marc.subfield_no = 'a' "
        + "and lower(marc.value) like {0})",
      context.existsClause("like", false)
    );
    assertEquals(
      "exists (select 1 from diku_mod_fqm_manager.src_srs_marc_indexers marc where "
        + "marc.marc_id = \"record_lb\".id and marc.field_no = '245' and marc.subfield_no = 'a' "
        + "and lower(marc.value) is not null and lower(marc.value) <> '')",
      context.presenceClause()
    );
  }

  @Test
  void shouldReturnEmptyQueryContextForInvalidName() {
    assertEquals(Optional.empty(), MarcFieldFactory.createQueryContext(entityTypeWithMarcSupport(), "marc_245"));
  }

  @Test
  void shouldReturnEmptyQueryContextWhenNoPlaceholder() {
    EntityType entityType = new EntityType()
      .id(UUID.randomUUID().toString())
      .name("no-marc")
      .columns(List.of(
        new EntityTypeColumn().name("marc_245_a").dataType(new MarcType().dataType("marcType")).valueGetter("x")
      ));

    assertEquals(Optional.empty(), MarcFieldFactory.createQueryContext(entityType, "marc_245_a"));
  }

  @Test
  void shouldReturnEmptyQueryContextWhenSyntheticFieldNotPresent() {
    // Placeholder present, but marc_245_a has not been synthesized onto the entity type.
    assertEquals(Optional.empty(), MarcFieldFactory.createQueryContext(entityTypeWithMarcSupport(), "marc_245_a"));
  }

  @Test
  void shouldReturnEmptyQueryContextWhenCorrelationOrValueGetterMissing() {
    String validSyntheticGetter = "(SELECT 1 FROM x marc WHERE marc.marc_id = id)";

    // marcIdGetter null (placeholder has no valueGetter)
    assertEquals(Optional.empty(),
      MarcFieldFactory.createQueryContext(entityTypeForContext(null, validSyntheticGetter), "marc_245_a"));
    // marcIdGetter blank
    assertEquals(Optional.empty(),
      MarcFieldFactory.createQueryContext(entityTypeForContext("   ", validSyntheticGetter), "marc_245_a"));
    // synthetic valueGetter null
    assertEquals(Optional.empty(),
      MarcFieldFactory.createQueryContext(entityTypeForContext("getter", null), "marc_245_a"));
    // synthetic valueGetter blank
    assertEquals(Optional.empty(),
      MarcFieldFactory.createQueryContext(entityTypeForContext("getter", "   "), "marc_245_a"));
  }

  @Test
  void shouldReturnEmptyQueryContextWhenTableNameNotExtractable() {
    // valueGetter is present and non-blank but has no "FROM <table> marc", so the table can't be extracted.
    assertEquals(Optional.empty(),
      MarcFieldFactory.createQueryContext(entityTypeForContext("getter", "SELECT 1"), "marc_245_a"));
  }

  @Test
  void shouldIdentifyGenericMarcPlaceholder() {
    assertFalse(MarcFieldFactory.isGenericMarcPlaceholder(null));
    assertTrue(MarcFieldFactory.isGenericMarcPlaceholder(
      new EntityTypeColumn().name("marc").dataType(new MarcType().dataType("marcType"))));
    // wrong name
    assertFalse(MarcFieldFactory.isGenericMarcPlaceholder(
      new EntityTypeColumn().name("not_marc").dataType(new MarcType().dataType("marcType"))));
    // right name, wrong data type
    assertFalse(MarcFieldFactory.isGenericMarcPlaceholder(
      new EntityTypeColumn().name("marc").dataType(new EntityDataType().dataType("stringType"))));
  }

  private static EntityType entityTypeWithMarcSupport() {
    return new EntityType()
      .id(UUID.randomUUID().toString())
      .name("simple_srs_record")
      .columns(List.of(
        new EntityTypeColumn()
          .name("matched_id")
          .dataType(new EntityDataType().dataType("rangedUUIDType"))
          .valueGetter(MARC_RECORD_ID_GETTER),
        new EntityTypeColumn()
          .name("marc")
          .dataType(new MarcType().dataType("marcType"))
          .valueGetter(MARC_RECORD_ID_GETTER)
      ));
  }

  private static EntityType entityTypeForContext(String placeholderValueGetter, String syntheticValueGetter) {
    EntityTypeColumn placeholder = new EntityTypeColumn().name("marc").dataType(new MarcType().dataType("marcType"));
    if (placeholderValueGetter != null) {
      placeholder.valueGetter(placeholderValueGetter);
    }
    EntityTypeColumn synthetic = new EntityTypeColumn().name("marc_245_a").dataType(new MarcType().dataType("marcType"));
    if (syntheticValueGetter != null) {
      synthetic.valueGetter(syntheticValueGetter);
    }
    return new EntityType()
      .id(UUID.randomUUID().toString())
      .name("context-fixture")
      .columns(List.of(placeholder, synthetic));
  }

  private static String expectedSubfieldValueGetter(String tag, String subfield) {
    return """
      (
        SELECT jsonb_agg(marc.value) FILTER (WHERE marc.value IS NOT NULL)
        FROM ${tenant_id}_mod_fqm_manager.src_srs_marc_indexers marc
        WHERE marc.marc_id = "record_lb".id
          AND marc.field_no = '%s'
          AND marc.subfield_no = '%s'
      )
      """.formatted(tag, subfield).trim();
  }

  private static void assertSqlEquals(String expected, String actual) {
    assertEquals(normalizeSql(expected), normalizeSql(actual));
  }

  private static String normalizeSql(String sql) {
    return sql.trim().replaceAll("\\s+", " ");
  }
}
