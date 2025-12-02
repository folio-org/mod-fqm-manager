package org.folio.fqm.context;

import static org.folio.fqm.IntegrationTestBase.TENANT_ID;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import org.folio.fqm.client.SimpleHttpClient;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.repository.SourceViewDatabaseObjectRepository;
import org.folio.fqm.repository.SourceViewRecordRepository;
import org.folio.fqm.service.EntityTypeInitializationService;
import org.folio.fqm.service.EntityTypeValidationService;
import org.folio.fqm.service.SourceViewService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

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
    liquibase.setChangeLogParameters(Map.of("tenant_id", TENANT_ID));
    liquibase.setShouldRun(true);

    // Create tenant-prefixed entity type definition table, required to not break initialization during integration tests
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute(String.format("CREATE SCHEMA IF NOT EXISTS %s_mod_fqm_manager", TENANT_ID));
    jdbcTemplate.execute(
      String.format(
        """
        CREATE TABLE IF NOT EXISTS %s_mod_fqm_manager.entity_type_definition (
          id UUID PRIMARY KEY,
          definition JSONB
        )
        """,
        TENANT_ID
      )
    );
    jdbcTemplate.execute(
      String.format(
        """
        CREATE OR REPLACE FUNCTION %s_mod_fqm_manager.get_tenant_data()
          RETURNS TABLE(id TEXT, name TEXT) AS $$
        BEGIN
          RETURN;
        END;
        $$ language plpgsql;
        """,
        TENANT_ID
      )
    );
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
    private EntityTypeValidationService entityTypeValidationService;

    @Autowired
    private ResourcePatternResolver resourceResolver;

    @Autowired
    private FolioSpringLiquibase liquibase;

    @Autowired
    private SourceViewDatabaseObjectRepository sourceViewDatabaseObjectRepository;

    @Autowired
    private SourceViewRecordRepository sourceViewRecordRepository;

    @PostConstruct
    public void populateEntityTypes() throws IOException, LiquibaseException {
      SimpleHttpClient ecsClient = mock(SimpleHttpClient.class);

      when(ecsClient.get(eq("user-tenants"), anyMap()))
        .thenReturn(
          """
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
        """
        );
      FolioExecutionContext executionContext = mock(FolioExecutionContext.class);
      when(executionContext.getUserId()).thenReturn(UUID.randomUUID());
      when(executionContext.getTenantId()).thenReturn(TENANT_ID);

      liquibase.setChangeLogParameters(Map.of("tenant_id", TENANT_ID));
      liquibase.performLiquibaseUpdate();

      new EntityTypeInitializationService(
        null,
        entityTypeRepository,
        entityTypeValidationService,
        new SourceViewService(
          sourceViewDatabaseObjectRepository,
          sourceViewRecordRepository,
          executionContext,
          resourceResolver
        ),
        new FolioExecutionContext() {
          @Override
          public String getTenantId() {
            return TENANT_ID;
          }
        },
        resourceResolver
      )
        .initializeEntityTypes("tenant_01");
    }
  }
}
