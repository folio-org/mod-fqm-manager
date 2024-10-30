package org.folio.fqm.service;

import lombok.SneakyThrows;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.config.FolioSpringConfiguration;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.tenant.domain.dto.Parameter;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FqmTenantServiceTest {
  @Mock
  private FolioExecutionContext executionContext;
  @Mock
  private FolioSpringLiquibase folioSpringLiquibase;
  @Mock
  private EntityTypeInitializationService entityTypeInitializationService;
  @InjectMocks
  private FqmTenantService fqmTenantService;

  @BeforeEach
  void setup() {
    when(executionContext.getTenantId()).thenReturn("tenantId");
    when(executionContext.getFolioModuleMetadata()).thenReturn(new FolioSpringConfiguration().folioModuleMetadata("mod-fqm-manager"));
  }

  @Test
  @SneakyThrows
  void createOrUpdateTenant() {

    fqmTenantService.createOrUpdateTenant(new TenantAttributes().parameters(List.of()));
    verify(folioSpringLiquibase, times(1)).setChangeLogParameters(anyMap());
    verify(folioSpringLiquibase, times(1)).setContexts("!slow");
    verify(folioSpringLiquibase, times(1)).setContexts("slow");
    Thread.sleep(100); // Let the slow migration thread finish
    verify(entityTypeInitializationService, times(2)).initializeEntityTypes(null);
  }

  @Test
  @SneakyThrows
  void createOrUpdateTenantForEcs() {
    fqmTenantService.createOrUpdateTenant(new TenantAttributes().addParametersItem(new Parameter("centralTenantId").value("central-test-tenant")));
    verify(folioSpringLiquibase, times(1)).setChangeLogParameters(anyMap());
    verify(folioSpringLiquibase, times(1)).setContexts("!slow");
    verify(folioSpringLiquibase, times(1)).setContexts("slow");
    Thread.sleep(100); // Let the slow migration thread finish
    verify(entityTypeInitializationService, times(2)).initializeEntityTypes("central-test-tenant");
  }
}
