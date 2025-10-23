package org.folio.fqm.config;

import com.zaxxer.hikari.HikariDataSource;
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
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
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
  public DataSource writerDataSource(
    @Qualifier("writerDataSourceProperties") DataSourceProperties writerProps,
    Environment env
  ) {
    HikariDataSource dataSource = writerProps
      .initializeDataSourceBuilder()
      .type(HikariDataSource.class)
      .build();

    Binder.get(env).bind("spring.datasource.hikari", HikariDataSource.class)
      .ifBound(props -> copyHikariProps(props, dataSource));

    log.info("ZZY Writer datasource max pool size: {}", dataSource.getMaximumPoolSize());
    return dataSource;
  }

  @Bean("readerDataSource")
  public DataSource readerDataSource(
    @Qualifier("readerDataSourceProperties") DataSourceProperties readerProps,
    @Qualifier("writerDataSourceProperties") DataSourceProperties writerProps,
    Environment env,
    FolioExecutionContext context
  ) {
    DataSourceProperties selectedProps = readerProps;

    if (ObjectUtils.isEmpty(dbHostReader)) {
      log.warn("Writer DB is used since reader DB is not available");
      selectedProps = writerProps;
    } else {
      log.info("Connecting to reader DB");
    }

    HikariDataSource dataSource = selectedProps
      .initializeDataSourceBuilder()
      .type(HikariDataSource.class)
      .build();

    Binder.get(env).bind("spring.datasource.hikari", HikariDataSource.class)
      .ifBound(props -> copyHikariProps(props, dataSource));

    log.info("ZZY Reader datasource max pool size: {}", dataSource.getMaximumPoolSize());
    return new DataSourceFolioWrapper(dataSource, context);
  }

  // Any properties specified in the hikari config of application.yml should be set here
  private static void copyHikariProps(HikariDataSource src, HikariDataSource dest) {
    dest.setMaximumPoolSize(src.getMaximumPoolSize());
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
