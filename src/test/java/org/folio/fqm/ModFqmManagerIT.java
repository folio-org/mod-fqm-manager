package org.folio.fqm;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;

/**
 * Check the shaded fat uber jar and Dockerfile:
 *
 * <p>Test /admin/health.
 *
 * <p>Test installation and migration with smoke test.
 */
@Testcontainers
class ModFqmManagerIT extends IntegrationTestBase {

  @Test
  void health() {
    // Given a bootstrapped instance of mod-fqm-manager (provided statically by the parent class)
    when()
      .get("/admin/health") // No X-Okapi-Tenant header needed
      .then()
      .statusCode(200)
      .body(containsString("\"UP\""));
  }
}
