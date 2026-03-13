package org.folio.fqm.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
 * Client for retrieving user permissions from mod-roles-keycloak.
 *
 * @implNote This wrapper primarily exists to wire the client through the
 *           {@code crossTenantHttpServiceProxyFactory}, which preserves caller-provided
 *           {@code X-Okapi-Tenant} headers. Using a direct interface is possible, but would
 *           require explicit {@code @Bean} registration instead of component-scanned construction.
 */
@Component
public class ModRolesKeycloakClient {

  private final ModRolesKeycloakHttpServiceClient delegate;

  @Autowired
  public ModRolesKeycloakClient(
    @Qualifier("crossTenantHttpServiceProxyFactory") HttpServiceProxyFactory crossTenantHttpServiceProxyFactory
  ) {
    this(crossTenantHttpServiceProxyFactory.createClient(ModRolesKeycloakHttpServiceClient.class));
  }

  ModRolesKeycloakClient(ModRolesKeycloakHttpServiceClient delegate) {
    this.delegate = delegate;
  }

  public UserPermissions getPermissionsUser(String tenant, UUID id) {
    return delegate.fetchPermissionsUser(tenant, id);
  }

  @HttpExchange(url = "permissions", accept = MediaType.APPLICATION_JSON_VALUE)
  interface ModRolesKeycloakHttpServiceClient {

    @GetExchange("/users/{id}")
    UserPermissions fetchPermissionsUser(@RequestHeader(XOkapiHeaders.TENANT) String tenant, @PathVariable UUID id);
  }

  public record UserPermissions(@JsonProperty("permissions") List<String> permissionNames,
                                @JsonProperty("userId") UUID userId) {
    public Set<String> getPermissionNames() {
      return new HashSet<>(permissionNames);
    }
  }
}
