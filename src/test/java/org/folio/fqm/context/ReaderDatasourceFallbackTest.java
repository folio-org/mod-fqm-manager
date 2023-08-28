package org.folio.fqm.context;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ActiveProfiles("db-test")
@SpringBootTest
class ReaderDatasourceFallbackTest {
  @Autowired
  @Qualifier("readerDataSource")
  private DataSource readerDatasource;

  @Autowired
  private DataSource writerDatasource;

  @Disabled("Disabled due to race condition on Jenkins")
  @Test
  void readerShouldFallbackToWriterDbWhenReaderNotAvailable() throws SQLException {
    assertNotNull(System.getProperty("DB_HOST"));
    assertNotNull(System.getProperty("DB_PORT"));
    assertNotNull(System.getProperty("DB_DATABASE"));

    String expectedDbUrl = "jdbc:postgresql://" + System.getProperty("DB_HOST")
      + ":" + System.getProperty("DB_PORT") + "/" + System.getProperty("DB_DATABASE");

    assertEquals(expectedDbUrl, readerDatasource.getConnection().getMetaData().getURL());
    assertEquals(expectedDbUrl, writerDatasource.getConnection().getMetaData().getURL());
  }
}
