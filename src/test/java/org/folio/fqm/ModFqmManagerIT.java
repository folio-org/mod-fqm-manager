package org.folio.fqm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

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
    var request = HttpRequest.newBuilder()
      .uri(URI.create(RestAssured.baseURI + "/admin/health"))
      .GET()
      .build();

    HttpResponse<String> response;
    try {
      response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to request /admin/health", e);
    }

    assertThat(response.statusCode(), is(200));
    assertThat(response.body(), containsString("\"UP\""));
  }
}
