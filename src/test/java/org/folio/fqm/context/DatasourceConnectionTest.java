package org.folio.fqm.context;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static java.lang.System.setProperty;
import static java.lang.System.getProperty;
import static java.lang.String.format;

@Testcontainers
@SpringBootTest
class DatasourceConnectionTest {
  @SuppressWarnings("resource")
  @Container
  public static PostgreSQLContainer<?> readerDbContainer = new PostgreSQLContainer<>("postgres:16-alpine")
      .withStartupAttempts(3);

  @SuppressWarnings("resource")
  @Container
  public static PostgreSQLContainer<?> writerDbContainer = new PostgreSQLContainer<>("postgres:16-alpine")
      .withStartupAttempts(3);

  @Autowired
  @Qualifier("readerDataSource")
  private DataSource readerDatasource;

  @Autowired
  private DataSource writerDatasource;

  @BeforeAll
  static void setup() {
    int postgresPort = 5432;
    setProperty("DB_HOST_READER", readerDbContainer.getHost());
    setProperty("DB_PORT_READER", "" + readerDbContainer.getMappedPort(postgresPort));
    setProperty("DB_DATABASE", readerDbContainer.getDatabaseName());
    setProperty("DB_USERNAME", readerDbContainer.getUsername());
    setProperty("DB_PASSWORD", readerDbContainer.getPassword());

    setProperty("DB_HOST", writerDbContainer.getHost());
    setProperty("DB_PORT", "" + writerDbContainer.getMappedPort(postgresPort));
  }

  @Test
  void testDbConnection() throws SQLException {
    assertNotNull(getProperty("DB_HOST"));
    assertNotNull(getProperty("DB_PORT"));
    assertNotNull(getProperty("DB_HOST_READER"));
    assertNotNull(getProperty("DB_PORT_READER"));
    assertNotNull(getProperty("DB_DATABASE"));

    String dbUrlFormat = "jdbc:postgresql://%s:%s/%s";
    String expectedReaderDbUrl = format(dbUrlFormat, getProperty("DB_HOST_READER"), getProperty("DB_PORT_READER"),
      getProperty("DB_DATABASE"));
    String expectedWriterDbUrl = format(dbUrlFormat, getProperty("DB_HOST"), getProperty("DB_PORT"),
      getProperty("DB_DATABASE"));

    assertEquals(expectedReaderDbUrl, readerDatasource.getConnection().getMetaData().getURL());
    assertEquals(expectedWriterDbUrl, writerDatasource.getConnection().getMetaData().getURL());
  }
}
