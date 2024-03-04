package org.folio.fqm;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

public class IntegrationTestBase {

  private static final Logger LOG = LoggerFactory.getLogger(IntegrationTestBase.class);
  private static final boolean IS_LOG_ENABLED = false;  // for debugging set to true!
  private static final Network network = Network.newNetwork();

  public static final String TENANT_ID = "beeuni";

  @Container
  private static final PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>("postgres:12-alpine")
      .withNetwork(network)
      .withNetworkAliases("mypostgres")
      .withExposedPorts(5432)
      .withUsername("myusername")
      .withPassword("mypassword")
      .withDatabaseName("mypostgres");

  @Container
  private static final GenericContainer<?> module =
    new GenericContainer<>(
      new ImageFromDockerfile("mod-fqm-manager").withFileFromPath(".", Path.of(".")))
      .withNetwork(network)
      .withExposedPorts(8081)
      .withEnv("DB_HOST", "mypostgres")
      .withEnv("DB_PORT", "5432")
      .withEnv("DB_USERNAME", "myusername")
      .withEnv("DB_PASSWORD", "mypassword")
      .withEnv("DB_DATABASE", "mypostgres")
      .withStartupTimeout(Duration.ofMinutes(3))
      .dependsOn(postgres);

  @BeforeAll
  public static void beforeAll() {
    // General integration test config
    RestAssured.reset();
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.baseURI = "http://" + module.getHost() + ":" + module.getFirstMappedPort();
    if (IS_LOG_ENABLED) {
      module.followOutput(new Slf4jLogConsumer(LOG).withSeparateOutputStreams());
    }

    // Bootstrap the module's DB
    String body = """
                  {"module_to": "999999.0.0",
                   "parameters": [{"key": "loadReference", "value": "true"},
                                  {"key": "loadSample",    "value": "true"} ]
                  }
                  """;
    postTenant(body);

    // Run some quick tests. These are done outside actual @Tests, since we need to make sure the service is up and
    // running before any other integration tests run

    // Test: migrate from 0.0.0, migration should be idempotent
    body = """
           {"module_from": "0.0.0",
           """ + body.substring(1);
    postTenant(body);

    // Test: smoke test the /entity-types endpoint to verify DB interactions are working
    smokeTest();
  }

  @BeforeEach
  public void beforeEach() {
    RestAssured.requestSpecification = null;
  }

  protected static DataSource getDataSource() {
    final HikariConfig config = new HikariConfig();
    config.setJdbcUrl(postgres.getJdbcUrl());
    config.setUsername(postgres.getUsername());
    config.setPassword(postgres.getPassword());
    return new HikariDataSource(config);
  }

  private static void postTenant(String body) {
    given()
      .header("X-Okapi-Tenant", TENANT_ID)
      .contentType("application/json")
      .body(body)
      .when()
      .post("/_/tenant")
      .then()
      .statusCode(204);
  }

  private static void smokeTest() {
    given()
      .header("X-Okapi-Tenant", TENANT_ID)
      .contentType("application/json")
      .when()
      .get("/entity-types")
      .then()
      .statusCode(200)
      .body("$.size()", is(6));
  }

}
