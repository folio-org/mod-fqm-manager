package org.folio.fqm.context;

import org.folio.fqm.repository.QueryResultsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@ContextConfiguration(classes = ReadWriteSplitTestConfiguration.class)
@ActiveProfiles("ReadWriteSplit")
class ReadWriteSplitTest {

  @Autowired
  @Qualifier("readerDataSource")
  private DataSource readerDataSource;

  @Autowired
  @Qualifier("dataSource")
  private DataSource dataSource;

  @Autowired
  private QueryResultsRepository queryResultsRepository;

  @Test
  @DirtiesContext
  void shouldUseReaderForReadOperations() throws SQLException {
    UUID queryId = UUID.randomUUID();
    assertThrows(Exception.class, () -> queryResultsRepository.getQueryResultIds(queryId, 0, 100));
    verifyNoInteractions(dataSource);
    verify(readerDataSource, atLeastOnce()).getConnection();
  }

  @Test
  @DirtiesContext
  void shouldUseWriterForWriteOperations() throws SQLException {
    UUID queryId = UUID.randomUUID();
    List<String[]> resultIds = List.of(
      new String[] {"id1"},
      new String[] {"id2"}
    );
    assertThrows(Exception.class, () -> queryResultsRepository.saveQueryResults(queryId, resultIds));
    verifyNoInteractions(readerDataSource);
    verify(dataSource, atLeastOnce()).getConnection();
  }
}
