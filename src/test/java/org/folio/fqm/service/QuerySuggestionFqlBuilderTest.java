package org.folio.fqm.service;

import org.folio.fql.service.FqlValidationService;
import org.folio.fqm.exception.InvalidFqlException;
import org.folio.fqm.model.QuerySuggestionBuildResult;
import org.folio.fqm.model.QuerySuggestionIntent;
import org.folio.fqm.model.QuerySuggestionIntentFilter;
import org.folio.querytool.domain.dto.DateTimeType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuerySuggestionFqlBuilderTest {

  private TestClockService clockService;

  @BeforeEach
  void setUp() {
    clockService = new TestClockService();
  }

  @Test
  void shouldBuildAndValidateFqlFromIntent() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .columns(List.of(
        new EntityTypeColumn().name("status").dataType(new StringType()),
        new EntityTypeColumn().name("invoiceNumber").dataType(new StringType()),
        new EntityTypeColumn().name("createdDate").dataType(new DateTimeType())
      ));
    QuerySuggestionIntent intent = new QuerySuggestionIntent(
      entityTypeId,
      "Orders",
      List.of(
        new QuerySuggestionIntentFilter("status", "Status", "$eq", "Open"),
        new QuerySuggestionIntentFilter("invoiceNumber", "Invoice number", "$isNull", "true"),
        new QuerySuggestionIntentFilter("createdDate", "Created date", "$olderThanDays", "30")
      ),
      List.of(),
      List.of()
    );

    String expectedFql = "{\"status\":{\"$eq\":\"Open\"},\"invoiceNumber\":{\"$empty\":true},\"createdDate\":{\"$lt\":\"2026-03-16T12:00:00Z\"}}";

    QuerySuggestionFqlBuilder builder = new QuerySuggestionFqlBuilder(
      new TestEntityTypeService(entityType),
      new TestFqlValidationService(Map.of()),
      clockService
    );

    QuerySuggestionBuildResult result = builder.build(intent, "show me open orders older than 30 days without invoices");

    assertEquals(expectedFql, result.fqlQuery());
    assertTrue(result.validated());
    assertTrue(result.assumptions().contains("Relative date filter was translated using UTC."));
  }

  @Test
  void shouldReturnPlaceholderWhenNoEntityTypeResolved() {
    QuerySuggestionIntent intent = new QuerySuggestionIntent(null, null, List.of(), List.of(), List.of());
    QuerySuggestionFqlBuilder builder = new QuerySuggestionFqlBuilder(
      new TestEntityTypeService(null),
      new TestFqlValidationService(Map.of()),
      clockService
    );

    QuerySuggestionBuildResult result = builder.build(intent, "show me something");

    assertFalse(result.validated());
    assertEquals("{\"_note\":{\"$eq\":\"Placeholder generated from natural-language request: show me something\"}}", result.fqlQuery());
  }

  @Test
  void shouldThrowForInvalidBuiltFql() {
    UUID entityTypeId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .columns(List.of(new EntityTypeColumn().name("status").dataType(new StringType())));
    QuerySuggestionIntent intent = new QuerySuggestionIntent(
      entityTypeId,
      "Orders",
      List.of(new QuerySuggestionIntentFilter("status", "Status", "$eq", "Open")),
      List.of(),
      List.of()
    );

    QuerySuggestionFqlBuilder builder = new QuerySuggestionFqlBuilder(
      new TestEntityTypeService(entityType),
      new TestFqlValidationService(Map.of("status", "bad")),
      clockService
    );

    assertThrows(InvalidFqlException.class, () -> builder.build(intent, "open orders"));
  }

  private static class TestClockService extends ClockService {
    @Override
    public Date now() {
      return Date.from(Instant.parse("2026-04-15T12:00:00Z"));
    }
  }

  private static class TestEntityTypeService extends EntityTypeService {
    private final EntityType entityType;

    TestEntityTypeService(EntityType entityType) {
      super(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
      this.entityType = entityType;
    }

    @Override
    public EntityType getEntityTypeDefinition(UUID entityTypeId, boolean includeHidden) {
      return entityType;
    }
  }

  private static class TestFqlValidationService extends FqlValidationService {
    private final Map<String, String> validationErrors;

    TestFqlValidationService(Map<String, String> validationErrors) {
      super(null);
      this.validationErrors = validationErrors;
    }

    @Override
    public Map<String, String> validateFql(EntityType entityType, String fqlQuery) {
      return validationErrors;
    }
  }
}
