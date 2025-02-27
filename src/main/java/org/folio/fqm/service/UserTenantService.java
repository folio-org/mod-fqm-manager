package org.folio.fqm.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.client.SimpleHttpClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Service wrapper for caching responses from user-tenants API.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class UserTenantService {

  private final SimpleHttpClient userTenantsClient;

  @Cacheable(value = "userTenantCache", key = "#tenantId")
  public String getUserTenantsResponse(String tenantId) {
    return "{\"userTenants\": [], \"totalRecords\": 0}";
  }
}
