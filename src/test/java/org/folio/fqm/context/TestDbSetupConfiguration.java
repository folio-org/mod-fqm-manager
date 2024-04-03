package org.folio.fqm.context;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.log4j.Log4j2;

import org.folio.fqm.service.EntityTypeInitializationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * This class is responsible for inserting test data into a PostgreSQL test container database.
 */
@Log4j2
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

  @Bean
  @DependsOn("liquibase")
  EntityTypeInitializer entityTypeInitializer() {
    return new EntityTypeInitializer();
  }

  static class EntityTypeInitializer {

    @Autowired
    private EntityTypeInitializationService entityTypeInitializationService;

    @PostConstruct
    public void populateEntityTypes() throws IOException {
      log.info("======== Entity type initializer @PostConstruct");
      // entityTypeInitializationService.initializeEntityTypes();
    }
  }
}
