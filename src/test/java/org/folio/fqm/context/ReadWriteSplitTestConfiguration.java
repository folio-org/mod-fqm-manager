package org.folio.fqm.context;

import org.folio.fqm.repository.QueryResultsRepository;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

import static org.mockito.Mockito.mock;


@Configuration
@Profile("ReadWriteSplit")
public class ReadWriteSplitTestConfiguration {

  @Primary
  @Bean("dataSource")
  public DataSource dataSource() {
    return mock(DataSource.class);
  }

  @Bean("readerDataSource")
  public DataSource readerDataSource() {
    return mock(DataSource.class);
  }

  @Primary
  @Bean("jooqContext")
  public DSLContext writerJooqContext(DataSource dataSource) {
    return DSL.using(dataSource, SQLDialect.POSTGRES);
  }

  @Bean("readerJooqContext")
  public DSLContext readerJooqContext(@Qualifier("readerDataSource") DataSource dataSource) {
    return DSL.using(dataSource, SQLDialect.POSTGRES);
  }

  @Bean
  public QueryResultsRepository queryResultsRepository(@Qualifier("readerJooqContext") DSLContext readerJooqContext, DSLContext jooqContext) {
    return new QueryResultsRepository(readerJooqContext, jooqContext);
  }
}
