package org.folio.fqm.service;

import org.folio.fqm.model.QuerySuggestionEntityTypeContext;
import org.folio.fqm.model.QuerySuggestionField;
import org.folio.fqm.model.QuerySuggestionIntent;
import org.folio.fqm.model.QuerySuggestionMetadataContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StubIntentInterpreterTest {

  private final StubIntentInterpreter interpreter = new StubIntentInterpreter();

  @Test
  void shouldInferOrdersIntentFromCommonReportPhrase() {
    UUID ordersId = UUID.randomUUID();
    UUID invoicesId = UUID.randomUUID();
    QuerySuggestionMetadataContext metadataContext = new QuerySuggestionMetadataContext(List.of(
      new QuerySuggestionEntityTypeContext(
        ordersId,
        "orders",
        "Orders",
        List.of(
          new QuerySuggestionField("status", "Status", null, null, true, false, false, false, null, List.of()),
          new QuerySuggestionField("invoiceNumber", "Invoice number", null, null, true, false, false, false, null, List.of()),
          new QuerySuggestionField("createdDate", "Created date", null, null, true, false, false, false, null, List.of())
        )
      ),
      new QuerySuggestionEntityTypeContext(
        invoicesId,
        "invoices",
        "Invoices",
        List.of()
      )
    ));

    QuerySuggestionIntent result = interpreter.interpret(
      "show me open orders older than 30 days without invoices",
      null,
      metadataContext
    );

    assertEquals(ordersId, result.entityTypeId());
    assertEquals(3, result.filters().size());
    assertEquals("status", result.filters().get(0).fieldName());
    assertEquals("$eq", result.filters().get(0).operator());
    assertEquals("Open", result.filters().get(0).value());
    assertTrue(result.assumptions().contains("Entity type was inferred as Orders."));
    assertTrue(result.clarificationQuestions().isEmpty());
  }

  @Test
  void shouldRespectPreselectedEntityType() {
    UUID selectedId = UUID.randomUUID();
    QuerySuggestionMetadataContext metadataContext = new QuerySuggestionMetadataContext(List.of(
      new QuerySuggestionEntityTypeContext(
        selectedId,
        "invoices",
        "Invoices",
        List.of(new QuerySuggestionField("status", "Status", null, null, true, false, false, false, null, List.of()))
      )
    ));

    QuerySuggestionIntent result = interpreter.interpret("open invoices", selectedId, metadataContext);

    assertEquals(selectedId, result.entityTypeId());
    assertTrue(result.assumptions().isEmpty());
  }
}
