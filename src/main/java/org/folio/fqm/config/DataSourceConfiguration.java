package org.folio.fqm.config;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.config.DataSourceFolioWrapper;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@Log4j2
public class DataSourceConfiguration {
  @Value("${DB_HOST_READER:}")
  private String dbHostReader;

  @Bean
  @ConfigurationProperties("spring.datasource.writer")
  public DataSourceProperties writerDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean
  @ConfigurationProperties("spring.datasource.reader")
  public DataSourceProperties readerDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Primary
  @Bean("dataSource")
  @ConfigurationProperties("spring.datasource.hikari")
  public DataSource writerDataSource() {
    return writerDataSourceProperties()
      .initializeDataSourceBuilder()
      .build();
  }

  @Bean("readerDataSource")
  @ConfigurationProperties("spring.datasource.hikari")
  public DataSource readerDataSource(FolioExecutionContext context) {
    DataSourceProperties dataSourceProperties = readerDataSourceProperties();
    if (ObjectUtils.isEmpty(dbHostReader)) {
      log.warn("Writer DB is used since reader DB is not available");
      dataSourceProperties = writerDataSourceProperties();
    } else {
      log.info("Connecting to reader DB");
    }
    DataSource readerDataSource = dataSourceProperties
      .initializeDataSourceBuilder()
      .build();
    return new DataSourceFolioWrapper(readerDataSource, context);
  }

  @Primary
  @Bean("jdbcTemplate")
  public JdbcTemplate writerJdbcTemplate(DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  @Bean("readerJdbcTemplate")
  public JdbcTemplate readerJdbcTemplate(@Qualifier("readerDataSource") DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  @Primary
  @Bean("dslContext")
  public DSLContext writerJooqContext(DataSource dataSource) {
    return DSL.using(dataSource, SQLDialect.POSTGRES);
  }

  @Bean("readerJooqContext")
  public DSLContext readerJooqContext(@Qualifier("readerDataSource") DataSource dataSource) {
    return DSL.using(dataSource, SQLDialect.POSTGRES);
  }
}
