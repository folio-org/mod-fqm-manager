package org.folio.fqm.context;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.service.EntityTypeInitializationService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * This class is responsible for inserting test data into a PostgreSQL test container database.
 */
@Configuration
@Profile("db-test")
public class TestDbSetupConfiguration {

  @Autowired
  EntityTypeRepository entityTypeRepository;

  @Autowired
  ResourcePatternResolver resourceResolver;

  @Bean
  SpringLiquibase springLiquibase(DataSource dataSource) {
    SpringLiquibase liquibase = new SpringLiquibase();
    liquibase.setDataSource(dataSource);
    liquibase.setChangeLog("classpath:test-db/changelog-test.xml");
    liquibase.setShouldRun(true);
    return liquibase;
  }

  @Bean
  @DependsOn("readerDataSource")
  EntityTypeInitializer entityTypeInitializer() {
    return new EntityTypeInitializer(entityTypeRepository, resourceResolver);
  }

  static class EntityTypeInitializer {

    private EntityTypeInitializationService entityTypeInitializationService;

    public EntityTypeInitializer(EntityTypeRepository entityTypeRepository, ResourcePatternResolver resourceResolver) {
      // I didn't want to provide a bean of FolioExecutionContext, in case it'd conflict with other tests
      this.entityTypeInitializationService =
        new EntityTypeInitializationService(
          entityTypeRepository,
          new FolioExecutionContext() {
            public String getTenantId() {
              return "beeuni";
            }
          },
          resourceResolver
        );
    }

    @PostConstruct
    public void populateEntityTypes() throws IOException {
      entityTypeInitializationService.initializeEntityTypes();
    }
  }
}
