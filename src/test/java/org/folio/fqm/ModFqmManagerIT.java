package org.folio.fqm;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.restassured.RestAssured;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Check the shaded fat uber jar and Dockerfile:
 *
 * <p>Test /admin/health.
 *
 * <p>Test installation and migration with smoke test.
 */
@Testcontainers
class ModFqmManagerIT {

  private static final Logger LOG = LoggerFactory.getLogger(ModFqmManagerIT.class);
  private static final boolean IS_LOG_ENABLED = false;  // for debugging set to true!
  private static final Network network = Network.newNetwork();

  @Container
  public static final PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>("postgres:12-alpine")
      .withNetwork(network)
      .withNetworkAliases("mypostgres")
      .withExposedPorts(5432)
      .withUsername("myusername")
      .withPassword("mypassword")
      .withDatabaseName("mypostgres");

  @Container
  public static final GenericContainer<?> module =
    new GenericContainer<>(
      new ImageFromDockerfile("mod-fqm-manager").withFileFromPath(".", Path.of(".")))
      .withNetwork(network)
      .withExposedPorts(8081)
      .withEnv("DB_HOST", "mypostgres")
      .withEnv("DB_PORT", "5432")
      .withEnv("DB_USERNAME", "myusername")
      .withEnv("DB_PASSWORD", "mypassword")
      .withEnv("DB_DATABASE", "mypostgres")
      .withStartupTimeout(Duration.ofMinutes(2))
      .dependsOn(postgres);

  @BeforeAll
  public static void beforeAll() {
    RestAssured.reset();
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.baseURI = "http://" + module.getHost() + ":" + module.getFirstMappedPort();
    if (IS_LOG_ENABLED) {
      module.followOutput(new Slf4jLogConsumer(LOG).withSeparateOutputStreams());
    }
  }

  @BeforeEach
  public void beforeEach() {
    RestAssured.requestSpecification = null;
  }

  @Test
  void health() {
    // request without X-Okapi-Tenant
    when()
      .get("/admin/health")
      .then()
      .statusCode(200)
      .body(containsString("\"UP\""));
  }

  @Test
  void installAndUpgrade() {
    String body = """
                  {"module_to": "999999.0.0",
                   "parameters": [{"key": "loadReference", "value": "true"},
                                  {"key": "loadSample",    "value": "true"} ]
                  }
                  """;
    postTenant(body);

    // migrate from 0.0.0, migration should be idempotent
    body = """
           {"module_from": "0.0.0",
           """ + body.substring(1);
    postTenant(body);

    smokeTest();
  }

  private void postTenant(String body) {
    given()
      .header("X-Okapi-Tenant", "beeuni")
      .contentType("application/json")
      .body(body)
      .when()
      .post("/_/tenant")
      .then()
      .statusCode(204);
  }

  private void smokeTest() {
    given()
      .header("X-Okapi-Tenant", "beeuni")
      .contentType("application/json")
      .when()
      .get("/entity-types")
      .then()
      .statusCode(200)
      .body("$.size()", is(4));
  }
}
