package org.folio.fqm.service;

import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.spring.service.TenantService;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Primary
public class FqmTenantService extends TenantService {
  public FqmTenantService(JdbcTemplate jdbcTemplate, FolioExecutionContext context, FolioSpringLiquibase folioSpringLiquibase) {
    super(jdbcTemplate, context, folioSpringLiquibase);
  }

  @Override
  public synchronized void createOrUpdateTenant(TenantAttributes tenantAttributes) {
    this.folioSpringLiquibase.setChangeLogParameters(Map.of("tenant_id", this.context.getTenantId()));
    super.createOrUpdateTenant(tenantAttributes);
  }
}
