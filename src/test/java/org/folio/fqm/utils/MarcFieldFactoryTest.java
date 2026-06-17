package org.folio.fqm.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.folio.fql.model.AndCondition;
import org.folio.fql.model.ContainsCondition;
import org.folio.fql.model.EqualsCondition;
import org.folio.fql.model.field.FqlField;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.MarcDataType;
import org.folio.querytool.domain.dto.RangedUUIDType;
import org.folio.querytool.domain.dto.StringType;
import org.junit.jupiter.api.Test;

class MarcFieldFactoryTest {

  private static final String MARC_RECORD_ID_GETTER = "\"record_lb\".matched_id";

  @Test
  void shouldRecognizeSupportedMarcFieldNames() {
    assertTrue(MarcFieldFactory.isMarcFieldName("marc_001"));
    assertTrue(MarcFieldFactory.isMarcFieldName("marc_245"));
    assertTrue(MarcFieldFactory.isMarcFieldName("marc_245_ind1"));
    assertTrue(MarcFieldFactory.isMarcFieldName("marc_245_ind2"));
    assertTrue(MarcFieldFactory.isMarcFieldName("marc_245_a"));
    assertTrue(MarcFieldFactory.isMarcFieldName("marc_245_ind1_7_a"));
    assertTrue(MarcFieldFactory.isMarcFieldName("marc_650_0"));
    assertTrue(MarcFieldFactory.isMarcFieldName("MARC_245_A"));
    assertTrue(MarcFieldFactory.isMarcFieldName("MARC_245_IND1"));
    assertTrue(MarcFieldFactory.isMarcFieldName("MARC_245_IND1_7_A"));
  }

  @Test
  void shouldRejectUnsupportedMarcFieldNames() {
    assertFalse(MarcFieldFactory.isMarcFieldName("245"));
    assertFalse(MarcFieldFactory.isMarcFieldName("marc_24"));
    assertFalse(MarcFieldFactory.isMarcFieldName("marc_245_ind3"));
    assertFalse(MarcFieldFactory.isMarcFieldName("marc_245_aa"));
    assertFalse(MarcFieldFactory.isMarcFieldName("marc_245_a_ind1"));
    assertFalse(MarcFieldFactory.isMarcFieldName("marc_245_ind1_a"));
  }

  @Test
  void shouldCreateTagSyntheticColumn() {
    EntityTypeColumn column = MarcFieldFactory.createSyntheticColumn(entityTypeWithMarcSupport(), "marc_245").orElseThrow();

    assertEquals("marc_245", column.getName());
    assertEquals("245", column.getLabelAlias());
    assertInstanceOf(MarcDataType.class, column.getDataType());
    assertEquals("lower(:value)", column.getValueFunction());
    assertSqlEquals(expectedTagValueGetter("245"), column.getValueGetter());
    assertSqlEquals(expectedTagFilterValueGetter("245"), column.getFilterValueGetter());
  }

  @Test
  void shouldCreateIndicatorSyntheticColumn() {
    EntityTypeColumn column = MarcFieldFactory.createSyntheticColumn(entityTypeWithMarcSupport(), "marc_245_ind1").orElseThrow();

    assertEquals("marc_245_ind1", column.getName());
    assertEquals("245 ind1", column.getLabelAlias());
    assertInstanceOf(MarcDataType.class, column.getDataType());
    assertSqlEquals(expectedIndicatorValueGetter("245", "ind1"), column.getValueGetter());
    assertSqlEquals(expectedIndicatorFilterValueGetter("245", "ind1"), column.getFilterValueGetter());
  }

  @Test
  void shouldCreateSubfieldSyntheticColumn() {
    EntityTypeColumn column = MarcFieldFactory.createSyntheticColumn(entityTypeWithMarcSupport(), "marc_245_a").orElseThrow();

    assertEquals("marc_245_a", column.getName());
    assertEquals("245$a", column.getLabelAlias());
    assertInstanceOf(MarcDataType.class, column.getDataType());
    assertSqlEquals(expectedSubfieldValueGetter("245", "a"), column.getValueGetter());
    assertSqlEquals(expectedSubfieldFilterValueGetter("245", "a"), column.getFilterValueGetter());
  }

  @Test
  void shouldCreateConstrainedSubfieldSyntheticColumn() {
    EntityTypeColumn column = MarcFieldFactory.createSyntheticColumn(entityTypeWithMarcSupport(), "marc_245_ind1_7_a").orElseThrow();

    assertEquals("marc_245_ind1_7_a", column.getName());
    assertEquals("245 ind1=7 $a", column.getLabelAlias());
    assertInstanceOf(MarcDataType.class, column.getDataType());
    assertSqlEquals(expectedConstrainedSubfieldValueGetter("245", "ind1", "7", "a"), column.getValueGetter());
    assertSqlEquals(expectedSubfieldFilterValueGetter("245", "a"), column.getFilterValueGetter());
  }

  @Test
  void shouldNormalizeUppercaseInputForMarcFields() {
    EntityTypeColumn indicatorColumn = MarcFieldFactory.createSyntheticColumn(entityTypeWithMarcSupport(), "MARC_245_IND1").orElseThrow();
    EntityTypeColumn subfieldColumn = MarcFieldFactory.createSyntheticColumn(entityTypeWithMarcSupport(), "MARC_245_A").orElseThrow();
    EntityTypeColumn constrainedSubfieldColumn =
      MarcFieldFactory.createSyntheticColumn(entityTypeWithMarcSupport(), "MARC_245_IND1_7_A").orElseThrow();

    assertEquals("MARC_245_IND1", indicatorColumn.getName());
    assertEquals("245 ind1", indicatorColumn.getLabelAlias());
    assertSqlEquals(expectedIndicatorValueGetter("245", "ind1"), indicatorColumn.getValueGetter());
    assertSqlEquals(expectedIndicatorFilterValueGetter("245", "ind1"), indicatorColumn.getFilterValueGetter());

    assertEquals("MARC_245_A", subfieldColumn.getName());
    assertEquals("245$a", subfieldColumn.getLabelAlias());
    assertSqlEquals(expectedSubfieldValueGetter("245", "a"), subfieldColumn.getValueGetter());
    assertSqlEquals(expectedSubfieldFilterValueGetter("245", "a"), subfieldColumn.getFilterValueGetter());

    assertEquals("MARC_245_IND1_7_A", constrainedSubfieldColumn.getName());
    assertEquals("245 ind1=7 $a", constrainedSubfieldColumn.getLabelAlias());
    assertSqlEquals(
      expectedConstrainedSubfieldValueGetter("245", "ind1", "7", "a"),
      constrainedSubfieldColumn.getValueGetter()
    );
    assertSqlEquals(expectedSubfieldFilterValueGetter("245", "a"), constrainedSubfieldColumn.getFilterValueGetter());
  }

  @Test
  void shouldInterpolateTenantIdWhenProvided() {
    EntityTypeColumn column = MarcFieldFactory.createSyntheticColumn(
      entityTypeWithMarcSupport(),
      "marc_245",
      "diku"
    ).orElseThrow();

    assertTrue(column.getValueGetter().contains("diku_mod_source_record_storage.marc_indexers"));
    assertFalse(column.getValueGetter().contains("${tenant_id}"));
    assertEquals("lower(marc.value)", column.getFilterValueGetter());
  }

  @Test
  void shouldReturnEmptyWhenEntityTypeDoesNotSupportMarcFields() {
    EntityType entityType = new EntityType()
      .id(UUID.randomUUID().toString())
      .name("test")
      .columns(List.of(
        new EntityTypeColumn()
          .name("matched_id")
          .dataType(new RangedUUIDType().dataType("rangedUUIDType"))
          .valueGetter(MARC_RECORD_ID_GETTER)
      ));

    assertEquals(Optional.empty(), MarcFieldFactory.createSyntheticColumn(entityType, "marc_245"));
  }

  @Test
  void shouldReturnEmptyForInvalidMarcFieldName() {
    assertEquals(Optional.empty(), MarcFieldFactory.createSyntheticColumn(entityTypeWithMarcSupport(), "marc_245_a_ind1"));
  }

  @Test
  void shouldAddSyntheticColumnsForReferencedMarcFields() {
    EntityType entityType = MarcFieldFactory.addSyntheticColumns(
      entityTypeWithMarcSupport(),
      List.of("marc_245", "marc_245_ind1", "field_that_is_not_marc")
    );

    assertEquals(
      List.of("matched_id", "content", "marc", "marc_245", "marc_245_ind1"),
      entityType.getColumns().stream().map(EntityTypeColumn::getName).toList()
    );
  }

  @Test
  void shouldCollectReferencedFieldNamesFromFqlCondition() {
    Set<String> fieldNames = MarcFieldFactory.getReferencedFieldNames(new AndCondition(List.of(
      new ContainsCondition(new FqlField("marc_245"), "Shakespeare"),
      new EqualsCondition(new FqlField("record_type"), "MARC_BIB"),
      new ContainsCondition(new FqlField("marc_245"), "Japanese")
    )));

    assertEquals(Set.of("marc_245", "record_type"), fieldNames);
  }

  @Test
  void shouldThrowWhenMarcPlaceholderOmitsCorrelationValueGetter() {
    EntityType entityType = new EntityType()
      .id(UUID.randomUUID().toString())
      .name("test")
      .columns(List.of(
        new EntityTypeColumn()
          .name("marc")
          .dataType(new MarcDataType().dataType("marcDataType"))
      ));

    assertThrows(
      InvalidEntityTypeDefinitionException.class,
      () -> MarcFieldFactory.createSyntheticColumn(entityType, "marc_245")
    );
  }

  private static EntityType entityTypeWithMarcSupport() {
    return new EntityType()
      .id(UUID.randomUUID().toString())
      .name("simple_srs_record")
      .columns(List.of(
        new EntityTypeColumn()
          .name("matched_id")
          .dataType(new RangedUUIDType().dataType("rangedUUIDType"))
          .valueGetter(MARC_RECORD_ID_GETTER),
        new EntityTypeColumn()
          .name("content")
          .dataType(new StringType().dataType("stringType"))
          .valueGetter("\"marc_record_lb\".content::text"),
        new EntityTypeColumn()
          .name("marc")
          .dataType(new MarcDataType().dataType("marcDataType"))
          .valueGetter(MARC_RECORD_ID_GETTER)
      ));
  }

  private static String expectedTagValueGetter(String tag) {
    return """
      (
        SELECT jsonb_agg(marc.value) FILTER (WHERE marc.value IS NOT NULL)
        FROM ${tenant_id}_mod_source_record_storage.marc_indexers marc
        WHERE marc.marc_id = "record_lb".matched_id
          AND marc.field_no = '%s'
      )
      """.formatted(tag).trim();
  }

  private static String expectedTagFilterValueGetter(String tag) {
    return "lower(marc.value)";
  }

  private static String expectedIndicatorValueGetter(String tag, String indicator) {
    return """
      (
        SELECT jsonb_agg(DISTINCT marc.%s) FILTER (WHERE marc.%s IS NOT NULL)
        FROM ${tenant_id}_mod_source_record_storage.marc_indexers marc
        WHERE marc.marc_id = "record_lb".matched_id
          AND marc.field_no = '%s'
      )
      """.formatted(indicator, indicator, tag).trim();
  }

  private static String expectedIndicatorFilterValueGetter(String tag, String indicator) {
    return "lower(marc.%s)".formatted(indicator);
  }

  private static String expectedSubfieldValueGetter(String tag, String subfield) {
    return """
      (
        SELECT jsonb_agg(marc.value) FILTER (WHERE marc.value IS NOT NULL)
        FROM ${tenant_id}_mod_source_record_storage.marc_indexers marc
        WHERE marc.marc_id = "record_lb".matched_id
          AND marc.field_no = '%s'
          AND marc.subfield_no = '%s'
      )
      """.formatted(tag, subfield).trim();
  }

  private static String expectedSubfieldFilterValueGetter(String tag, String subfield) {
    return "lower(marc.value)";
  }

  private static String expectedConstrainedSubfieldValueGetter(String tag, String indicator, String indicatorValue, String subfield) {
    return """
      (
        SELECT jsonb_agg(marc.value) FILTER (WHERE marc.value IS NOT NULL)
        FROM ${tenant_id}_mod_source_record_storage.marc_indexers marc
        WHERE marc.marc_id = "record_lb".matched_id
          AND marc.field_no = '%s'
          AND lower(marc.%s) = '%s'
          AND marc.subfield_no = '%s'
      )
      """.formatted(tag, indicator, indicatorValue, subfield).trim();
  }

  private static void assertSqlEquals(String expected, String actual) {
    assertEquals(normalizeSql(expected), normalizeSql(actual));
  }

  private static String normalizeSql(String sql) {
    return sql.trim().replaceAll("\\s+", " ");
  }
}
