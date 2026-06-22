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
import org.folio.fql.model.field.FqlField;
import org.folio.fqm.exception.InvalidEntityTypeDefinitionException;
import org.folio.querytool.domain.dto.EntityDataType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.MarcType;
import org.junit.jupiter.api.Test;

class MarcFieldFactoryTest {

  private static final String MARC_RECORD_ID_GETTER = "\"record_lb\".matched_id";

  @Test
  void shouldRecognizeSupportedMarcFieldNames() {
    assertTrue(MarcFieldFactory.isMarcFieldName("marc_245_a"));
    assertTrue(MarcFieldFactory.isMarcFieldName("marc_650_0"));
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

    assertTrue(column.getValueGetter().contains("diku_mod_source_record_storage.marc_indexers"));
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

  private static void assertSqlEquals(String expected, String actual) {
    assertEquals(normalizeSql(expected), normalizeSql(actual));
  }

  private static String normalizeSql(String sql) {
    return sql.trim().replaceAll("\\s+", " ");
  }
}
