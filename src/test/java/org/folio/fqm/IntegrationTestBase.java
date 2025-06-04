package org.folio.fqm;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.restassured.RestAssured;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.folio.spring.integration.XOkapiHeaders;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;

public class IntegrationTestBase {

  private static final Logger LOG = LoggerFactory.getLogger(IntegrationTestBase.class);
  private static final boolean IS_LOG_ENABLED = false;  // for debugging set to true!
  private static final Network network = Network.newNetwork();

  public static final String TENANT_ID = "beeuni";

  protected static final MockWebServer mokapi = new MockWebServer();

  static {
    Testcontainers.exposeHostPorts(mokapi.getPort());
  }

  @SuppressWarnings("resource")
  @Container
  private static final PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>("postgres:16-alpine")
      .withNetwork(network)
      .withNetworkAliases("mypostgres")
      .withExposedPorts(5432)
      .withUsername("myusername")
      .withPassword("mypassword")
      .withDatabaseName("mypostgres")
      .withStartupAttempts(3);

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
      .withEnv("okapi_url", "http://host.testcontainers.internal:" + mokapi.getPort())
      .withEnv("mod-fqm-manager.entity-type-cache-timeout-seconds", "0")
      .withStartupTimeout(Duration.ofMinutes(3))
      .dependsOn(postgres);


  @BeforeAll
  public static void beforeAll() {
    // General integration test config
    mokapi.setDispatcher(new MockOkapiDispatcher());

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

    createDummyViews();

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
//      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .headers(getOkapiHeaders())
      .contentType("application/json")
      .body(body)
      .when()
      .post("/_/tenant")
      .then()
      .statusCode(204);
  }

  private static void createDummyViews() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(getDataSource());
    // Create a src_user_custom_fields view, since the Users entity type depends on it for custom fields. Without this, the smoke test will fail.
    jdbcTemplate.execute("CREATE VIEW src_user_custom_fields AS SELECT NULL::uuid AS id, '{}'::jsonb AS jsonb LIMIT 1");
    jdbcTemplate.execute("CREATE VIEW src_purchase_order_custom_fields AS SELECT NULL::uuid AS id, '{}'::jsonb AS jsonb LIMIT 1");
    jdbcTemplate.execute("CREATE VIEW src_purchase_order_line_custom_fields AS SELECT NULL::uuid AS id, '{}'::jsonb AS jsonb LIMIT 1");
  }

  private static void smokeTest() {
    given()
      .headers(getOkapiHeaders())
      .contentType("application/json")
      .when()
      .get("/entity-types?includeInaccessible=true")
      .then()
      .statusCode(200)
      .body("entityTypes.size()", greaterThan(0));
  }

  protected static Map<String, String> getOkapiHeaders() {
    return Map.of(
      XOkapiHeaders.TENANT, TENANT_ID,
      XOkapiHeaders.USER_ID, UUID.randomUUID().toString(),
      XOkapiHeaders.URL, "http://host.testcontainers.internal:" + mokapi.getPort());
  }

  private static class MockOkapiDispatcher extends Dispatcher {
    @NotNull
    @Override
    public MockResponse dispatch(@NotNull RecordedRequest recordedRequest) {
      if (recordedRequest.getPath().matches("/perms/users/[-0-9a-f]+/permissions\\?expanded=true&indexField=userId")) {
        return new MockResponse().setBody("""
          {
            "permissionNames": [ "organizations.organizations.collection.get",
                                 "organizations-storage.organization-types.collection.get",
                                 "acquisitions-units.units.collection.get"
            ],
            "totalRecords": 3
          }
          """).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
      }
      if (recordedRequest.getPath().matches("/user-tenants.*")) {
        return new MockResponse().setBody("{\"userTenants\": [], \"totalRecords\": 0}").setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
      }
      throw new RuntimeException("Unexpected request: " + recordedRequest.getPath());
    }
  }
}
