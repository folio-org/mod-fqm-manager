package org.folio.fqm.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.folio.fqm.config.CrossTenantFeignConfig;
import org.folio.spring.integration.XOkapiHeaders;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@FeignClient(name = "permissions", configuration = CrossTenantFeignConfig.class)
public interface ModRolesKeycloakClient {
  @GetMapping(value = "/users/{id}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
  UserPermissions getPermissionsUser(@RequestHeader(XOkapiHeaders.TENANT) String tenant, @PathVariable UUID id);

  record UserPermissions(@JsonProperty("permissions") List<String> permissionNames,
                         @JsonProperty("userId") UUID userId) {
    public Set<String> getPermissionNames() {
      return new HashSet<>(permissionNames);
    }
  }
}
