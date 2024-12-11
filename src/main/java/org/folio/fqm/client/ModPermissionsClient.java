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

@Service
@FeignClient(name = "perms", configuration = CrossTenantFeignConfig.class)
public interface ModPermissionsClient {
  @GetMapping(value = "/users/{userId}/permissions?expanded=true&indexField=userId", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
  UserPermissions getPermissionsForUser(@RequestHeader(XOkapiHeaders.TENANT) String tenant, @PathVariable String userId);

  record UserPermissions(@JsonProperty("permissionNames") List<String> permissionNames,
                         @JsonProperty("totalRecords") int totalRecords) {
    public Set<String> getPermissionNames() {
      return new HashSet<>(permissionNames);
    }
  }
}
