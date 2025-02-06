package org.folio.fqm.context;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.folio.fqm.IntegrationTestBase;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.service.EntityTypeInitializationService;
import org.folio.fqm.service.PermissionsService;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.support.ResourcePatternResolver;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class is responsible for inserting test data into a PostgreSQL test container database.
 */
@Disabled
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

    @Autowired
    private PermissionsService permissionsService;

    @PostConstruct
    public void populateEntityTypes() throws IOException {
      SimpleHttpClient ecsClient = mock(SimpleHttpClient.class);
      when(ecsClient.get(eq("user-tenants"), anyMap())).thenReturn("""
      {
          "userTenants": [
              {
                  "id": "06192681-0df7-4f33-a38f-48e017648d69",
                  "userId": "a5e7895f-503c-4335-8828-f507bc8d1c45",
                  "tenantId": "tenant_01",
                  "centralTenantId": "tenant_01"
              }
          ],
          "totalRecords": 1
      }
      """);
      FolioExecutionContext executionContext = mock(FolioExecutionContext.class);
      when(executionContext.getUserId()).thenReturn(UUID.randomUUID());
      new EntityTypeInitializationService(
        entityTypeRepository,
        new FolioExecutionContext() {
          @Override
          public String getTenantId() {
            return IntegrationTestBase.TENANT_ID;
          }
        },
        resourceResolver,
        null
      )
        .initializeEntityTypes("tenant_01");
    }
  }
}
