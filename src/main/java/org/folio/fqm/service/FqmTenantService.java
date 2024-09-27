package org.folio.fqm.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.spring.service.TenantService;
import org.folio.tenant.domain.dto.Parameter;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Primary
public class FqmTenantService extends TenantService {

  private final EntityTypeInitializationService entityTypeInitializationService;

  @Autowired
  public FqmTenantService(
    JdbcTemplate jdbcTemplate,
    FolioExecutionContext context,
    FolioSpringLiquibase folioSpringLiquibase,
    EntityTypeInitializationService entityTypeInitializationService
  ) {
    super(jdbcTemplate, context, folioSpringLiquibase);
    this.entityTypeInitializationService = entityTypeInitializationService;
  }

  @Override
  public synchronized void createOrUpdateTenant(TenantAttributes tenantAttributes) {
    this.folioSpringLiquibase.setChangeLogParameters(Map.of("tenant_id", this.context.getTenantId()));

    // Run all of the regular DB migration scripts (those that aren't marked as "slow")
    this.folioSpringLiquibase.setContexts("!slow");
    super.createOrUpdateTenant(tenantAttributes);

    // Run all of the slow DB migration scripts in a separate thread
    this.folioSpringLiquibase.setContexts("slow");
    Thread slowRun = new Thread(() -> super.createOrUpdateTenant(tenantAttributes));
    slowRun.setDaemon(true);
    slowRun.start();
  }

  @Override
  protected void afterLiquibaseUpdate(TenantAttributes tenantAttributes) {
    List<Parameter> params = tenantAttributes.getParameters();
    String centralTenantId = params == null ? null : tenantAttributes.getParameters()
      .stream()
      .filter(param -> "centralTenantId".equals(param.getKey()))
      .map(Parameter::getValue)
      .findFirst()
      .orElse(null);
    try {
      entityTypeInitializationService.initializeEntityTypes(centralTenantId);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to initialize entity types", e);
    }
  }
}
