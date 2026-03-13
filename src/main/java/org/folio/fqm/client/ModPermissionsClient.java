package org.folio.fqm.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.folio.spring.integration.XOkapiHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Client for retrieving expanded permissions from the perms API.
 *
 * @implNote This wrapper primarily exists to wire the client through the
 *           {@code crossTenantHttpServiceProxyFactory}, which preserves caller-provided
 *           {@code X-Okapi-Tenant} headers. Using a direct interface is possible, but would
 *           require explicit {@code @Bean} registration instead of component-scanned construction.
 */
@Component
public class ModPermissionsClient {

  private final ModPermissionsHttpServiceClient delegate;

  @Autowired
  public ModPermissionsClient(
    @Qualifier("crossTenantHttpServiceProxyFactory") HttpServiceProxyFactory crossTenantHttpServiceProxyFactory
  ) {
    this(crossTenantHttpServiceProxyFactory.createClient(ModPermissionsHttpServiceClient.class));
  }

  ModPermissionsClient(ModPermissionsHttpServiceClient delegate) {
    this.delegate = delegate;
  }

  public UserPermissions getPermissionsForUser(String tenant, String userId) {
    return delegate.fetchPermissionsForUser(tenant, userId);
  }

  @HttpExchange(url = "perms", accept = MediaType.APPLICATION_JSON_VALUE)
  interface ModPermissionsHttpServiceClient {

    @GetExchange("/users/{userId}/permissions?expanded=true&indexField=userId")
    UserPermissions fetchPermissionsForUser(@RequestHeader(XOkapiHeaders.TENANT) String tenant, @PathVariable String userId);
  }

  public record UserPermissions(@JsonProperty("permissionNames") List<String> permissionNames,
                                @JsonProperty("totalRecords") int totalRecords) {
    public Set<String> getPermissionNames() {
      return new HashSet<>(permissionNames);
    }
  }
}
