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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MarcFieldFactoryTest {

  private static final String MARC_RECORD_ID_GETTER = "\"record_lb\".id";

  @ParameterizedTest(name = "{0} -> valid={1} ({2})")
  @CsvSource({
    "marc_245_a,    true,  subfield",
    "marc_650_0,    true,  subfield with numeric code",
    "marc_245,      true,  tag-only",
    "marc_001,      true,  control field (tag-only)",
    "marc_245_ind1, true,  indicator 1",
    "marc_245_ind2, true,  indicator 2",
    "marc_24_a,     false, tag must be exactly 3 digits",
    "marc_2451_a,   false, tag must be exactly 3 digits",
    "marc_abc_a,    false, tag must be numeric",
    "marc_245_aa,   false, subfield must be a single character",
    "245_a,         false, missing marc_ prefix",
    "marc_001_a,    false, control field has no subfields",
    "marc_008_ind1, false, control field has no indicators",
  })
  void shouldRecognizeMarcFieldNames(String fieldName, boolean valid, String why) {
    assertEquals(valid, MarcFieldFactory.isMarcFieldName(fieldName), why);
  }

  @Test
  void shouldTreatControlFieldAsTagOnly() {
    // A control field is queryable only as a whole-string, tag-only field
    MarcFieldFactory.MarcFieldName parsed = MarcFieldFactory.parse("marc_008").orElseThrow();
    assertEquals("008", parsed.tag());
    assertNull(parsed.subfield());
  }

  @Test
  void shouldSupportIndicatorFields() {
    MarcFieldFactory.MarcFieldName parsed = MarcFieldFactory.parse("marc_245_ind1").orElseThrow();
    assertEquals("245", parsed.tag());
    assertNull(parsed.subfield());
    assertEquals("1", parsed.indicator());
    assertTrue(parsed.isIndicator());
    assertEquals("ind1", parsed.targetColumn());
    assertEquals("MARC 245 ind1", parsed.labelAlias());
    // Indicators target the ind1/ind2 column, matched case-insensitively like other MARC values.
    assertEquals("lower(marc.ind1)", parsed.filterValueGetter());
    assertEquals("lower(:value)", parsed.valueFunction());

    EntityTypeColumn column = MarcFieldFactory.createSyntheticColumn(entityTypeWithMarcSupport(), "marc_245_ind1", "diku").orElseThrow();
    assertEquals("lower(marc.ind1)", column.getFilterValueGetter());
    assertEquals("lower(:value)", column.getValueFunction());
    // Indicators are deduplicated (DISTINCT) so a multi-subfield field doesn't return repeated values.
    assertTrue(column.getValueGetter().contains("jsonb_agg(DISTINCT marc.ind1)"));
    assertFalse(column.getValueGetter().contains("subfield_no"));

    EntityType entityType = MarcFieldFactory.addSyntheticColumns(entityTypeWithMarcSupport(), List.of("marc_245_ind1"), "diku");
    MarcQueryContext context = MarcFieldFactory.createQueryContext(entityType, "marc_245_ind1").orElseThrow();
    assertEquals("lower(marc.ind1)", context.filterValueGetter());
    // No subfield_no constraint; comparison is against the indicator column.
    assertEquals("marc.marc_id = " + MARC_RECORD_ID_GETTER + " and marc.field_no = '245'", context.whereClause());
    assertEquals(
      "exists (select 1 from diku_mod_fqm_manager.src_srs_marc_indexers marc where "
        + "marc.marc_id = " + MARC_RECORD_ID_GETTER + " and marc.field_no = '245' and lower(marc.ind1) = {0})",
      context.existsClause("=", true)
    );
  }

  @Test
  void shouldParseTagOnlyFieldNameWithNoSubfield() {
    MarcFieldFactory.MarcFieldName parsed = MarcFieldFactory.parse("marc_245").orElseThrow();

    assertEquals("marc_245", parsed.fieldName());
    assertEquals("245", parsed.tag());
    assertNull(parsed.subfield());
    // Label and the synthesized SQL omit the subfield, so the predicate matches any subfield of the tag.
    assertEquals("MARC 245", parsed.labelAlias());
    assertEquals("", parsed.subfieldClause());
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
    assertEquals("MARC 245$a", parsed.labelAlias());
  }

  @Test
  void shouldCreateSubfieldSyntheticColumn() {
    EntityTypeColumn column = MarcFieldFactory.createSyntheticColumn(entityTypeWithMarcSupport(), "marc_245_a", "diku").orElseThrow();

    assertEquals("marc_245_a", column.getName());
    assertEquals("MARC 245$a", column.getLabelAlias());
    assertInstanceOf(MarcType.class, column.getDataType());
    assertEquals("lower(:value)", column.getValueFunction());
    assertSqlEquals(expectedSubfieldValueGetter("diku", "245", "a"), column.getValueGetter());
    assertEquals("lower(marc.value)", column.getFilterValueGetter());
  }

  @Test
  void shouldCreateTagOnlySyntheticColumn() {
    EntityTypeColumn column = MarcFieldFactory.createSyntheticColumn(entityTypeWithMarcSupport(), "marc_245", "diku").orElseThrow();

    assertEquals("marc_245", column.getName());
    assertEquals("MARC 245", column.getLabelAlias());
    assertInstanceOf(MarcType.class, column.getDataType());
    assertEquals("lower(marc.value)", column.getFilterValueGetter());
    // Tag-only: the value-getter filters on the tag but not a subfield, so it matches any subfield.
    assertTrue(column.getValueGetter().contains("marc.field_no = '245'"));
    assertFalse(column.getValueGetter().contains("subfield_no"));
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
    assertEquals(Optional.empty(), MarcFieldFactory.createSyntheticColumn(entityTypeWithMarcSupport(), "marc_24_a", null));
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
      () -> MarcFieldFactory.createSyntheticColumn(entityType, "marc_245_a", null)
    );
  }

  @Test
  void shouldThrowWhenSynthesizingFromFqlConditionWithoutTenant() {
    EntityType entityType = entityTypeWithMarcSupport();
    ContainsCondition condition = new ContainsCondition(new FqlField("marc_245_a"), "Shakespeare");

    assertThrows(
      IllegalArgumentException.class,
      () -> MarcFieldFactory.addSyntheticColumns(entityType, condition, null)
    );
  }

  @Test
  void shouldThrowWhenSynthesizingFromCollectionWithoutTenant() {
    EntityType entityType = entityTypeWithMarcSupport();
    List<String> fieldNames = List.of("marc_245_a");

    assertThrows(
      IllegalArgumentException.class,
      () -> MarcFieldFactory.addSyntheticColumns(entityType, fieldNames, null)
    );
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

    assertEquals(Optional.empty(), MarcFieldFactory.createSyntheticColumn(entityType, "marc_245_a", null));
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
      () -> MarcFieldFactory.createSyntheticColumn(entityType, "marc_245_a", null)
    );
  }

  @Test
  void shouldThrowWhenSynthesizingMarcColumnWithBlankTenant() {
    EntityType entityType = entityTypeWithMarcSupport();

    assertThrows(
      IllegalArgumentException.class,
      () -> MarcFieldFactory.createSyntheticColumn(entityType, "marc_245_a", "   ")
    );
  }

  @Test
  void shouldBuildQueryContextAndItsClauses() {
    EntityType entityType = MarcFieldFactory.addSyntheticColumns(entityTypeWithMarcSupport(), List.of("marc_245_a"), "diku");

    MarcQueryContext context = MarcFieldFactory.createQueryContext(entityType, "marc_245_a").orElseThrow();

    String table = "diku_mod_fqm_manager.src_srs_marc_indexers";
    String where = "marc.marc_id = " + MARC_RECORD_ID_GETTER + " and marc.field_no = '245' and marc.subfield_no = 'a'";
    String subqueryPrefix = "select 1 from " + table + " marc where " + where;

    assertEquals("245", context.marcField().tag());
    assertEquals("a", context.marcField().subfield());
    assertEquals(table, context.tableName());
    assertEquals(MARC_RECORD_ID_GETTER, context.marcIdGetter());
    assertEquals("lower(marc.value)", context.filterValueGetter());

    assertEquals(where, context.whereClause());
    assertEquals(
      "exists (" + subqueryPrefix + " and lower(marc.value) = {0})",
      context.existsClause("=", true)
    );
    assertEquals(
      "not exists (" + subqueryPrefix + " and lower(marc.value) like {0})",
      context.existsClause("like", false)
    );
    assertEquals(
      "exists (" + subqueryPrefix + " and lower(marc.value) is not null and lower(marc.value) <> '')",
      context.presenceClause()
    );
  }

  @Test
  void shouldBuildTagOnlyQueryContextWithoutSubfieldPredicate() {
    EntityType entityType = MarcFieldFactory.addSyntheticColumns(entityTypeWithMarcSupport(), List.of("marc_245"), "diku");

    MarcQueryContext context = MarcFieldFactory.createQueryContext(entityType, "marc_245").orElseThrow();

    assertEquals("245", context.marcField().tag());
    assertNull(context.marcField().subfield());
    // No subfield_no constraint -> matches any subfield of the tag.
    assertEquals("marc.marc_id = " + MARC_RECORD_ID_GETTER + " and marc.field_no = '245'", context.whereClause());
    assertFalse(context.existsClause("=", true).contains("subfield_no"));
  }

  @Test
  void shouldReturnEmptyQueryContextForInvalidName() {
    assertEquals(Optional.empty(), MarcFieldFactory.createQueryContext(entityTypeWithMarcSupport(), "marc_24_a"));
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

  private static String expectedSubfieldValueGetter(String tenantId, String tag, String subfield) {
    return """
      (
        SELECT jsonb_agg(marc.value) FILTER (WHERE marc.value IS NOT NULL)
        FROM %s_mod_fqm_manager.src_srs_marc_indexers marc
        WHERE marc.marc_id = "record_lb".id
          AND marc.field_no = '%s'
          AND marc.subfield_no = '%s'
      )
      """.formatted(tenantId, tag, subfield).trim();
  }

  private static void assertSqlEquals(String expected, String actual) {
    assertEquals(normalizeSql(expected), normalizeSql(actual));
  }

  private static String normalizeSql(String sql) {
    return sql.trim().replaceAll("\\s+", " ");
  }
}
