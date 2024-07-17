package org.folio.fqm.context;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Map;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.folio.fqm.IntegrationTestBase;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.service.EntityTypeInitializationService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.support.ResourcePatternResolver;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

  @Bean
  @DependsOn("readerDataSource")
  EntityTypeInitializer entityTypeInitializer() {
    return new EntityTypeInitializer();
  }

  static class EntityTypeInitializer {

    @Autowired
    private EntityTypeRepository entityTypeRepository;

    @Autowired
    private ResourcePatternResolver resourceResolver;

    @PostConstruct
    public void populateEntityTypes() throws IOException {
      SimpleHttpClient ecsClient = mock(SimpleHttpClient.class);
      when(ecsClient.get("consortia-configuration", Map.of("limit", String.valueOf(100)))).thenReturn("{\"centralTenantId\": \"tenant_01\"}");
      new EntityTypeInitializationService(
        entityTypeRepository,
        new FolioExecutionContext() {
          @Override
          public String getTenantId() {
            return IntegrationTestBase.TENANT_ID;
          }
        },
        resourceResolver,
        ecsClient
      )
        .initializeEntityTypes();
    }
  }
}
