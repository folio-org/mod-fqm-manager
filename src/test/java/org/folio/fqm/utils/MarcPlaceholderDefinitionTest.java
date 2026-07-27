package org.folio.fqm.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import org.folio.fql.service.MarcFieldFactory;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.junit.jupiter.api.Test;

/**
 * Guards the invariant that composite MARC support silently depends on: the generic {@code marc} placeholder in
 * {@code simple_srs_record} must stay {@code essential: true}.
 *
 * <p>When that source is embedded in a composite (e.g. {@code composite_instance_srs_bib}), the query path
 * flattens with {@code preserveAllColumns=false}, which drops non-essential columns. If the placeholder is
 * dropped, {@link MarcFieldFactory#findMarcPlaceholder} can't locate it and composite MARC fields stop
 * validating/synthesizing — with no error, they just quietly fail to resolve. A plain unit test wouldn't catch a
 * flag flip in the real definition, so this asserts against the actual {@code .json5} on the classpath.
 */
class MarcPlaceholderDefinitionTest {

  private static final String SIMPLE_SRS_RECORD = "/entity-types/srs/simple_srs_record.json5";
  private static final ObjectMapper JSON5 = JSON5ObjectMapperFactory.create();

  @Test
  void marcPlaceholderIsEssentialSoItSurvivesCompositeFlattening() {
    EntityTypeColumn marcPlaceholder = loadMarcPlaceholderColumn();

    assertEquals(
      Boolean.TRUE,
      marcPlaceholder.getEssential(),
      "The 'marc' placeholder in simple_srs_record must stay essential:true. Composite entity types flatten with "
        + "preserveAllColumns=false, which drops non-essential columns; dropping this placeholder makes composite "
        + "MARC fields silently fail to validate/synthesize."
    );

    assertTrue(MarcFieldFactory.isGenericMarcPlaceholder(marcPlaceholder));
    assertTrue(marcPlaceholder.getValueGetter() != null && !marcPlaceholder.getValueGetter().isBlank());
  }

  private static EntityTypeColumn loadMarcPlaceholderColumn() {
    String placeholder = MarcFieldFactory.GENERIC_MARC_COLUMN_NAME;
    try (InputStream in = MarcPlaceholderDefinitionTest.class.getResourceAsStream(SIMPLE_SRS_RECORD)) {
      assertNotNull(in, "Missing entity type resource: " + SIMPLE_SRS_RECORD);
      EntityType entityType = JSON5.readValue(in, EntityType.class);
      return entityType.getColumns().stream()
        .filter(column -> placeholder.equals(column.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No '" + placeholder + "' column in " + SIMPLE_SRS_RECORD));
    } catch (java.io.IOException e) {
      throw new AssertionError("Failed to read " + SIMPLE_SRS_RECORD, e);
    }
  }
}
