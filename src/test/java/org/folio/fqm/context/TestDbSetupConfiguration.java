package org.folio.fqm.context;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * This class is responsible for inserting test data into a PostgreSQL test container database.
 */
@Configuration
@Profile("db-test")
public class TestDbSetupConfiguration {

  @Bean
  SpringLiquibase springLiquibase(DataSource dataSource) {
    SpringLiquibase liquibase = new SpringLiquibase();
    liquibase.setDataSource(dataSource);
    liquibase.setChangeLog("classpath:test-db/changelog-test.xml");
    liquibase.setShouldRun(true);
    return liquibase;
  }
}
