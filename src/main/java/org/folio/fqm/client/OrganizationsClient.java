package org.folio.fqm.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

@Service
@FeignClient(name = "organizations/organizations")
// Optional dep in MD as this is only used in migrations which will only run on entity types
// that require locations, so we can safely assume locations will exist if this is invoked.
public interface OrganizationsClient {
  @GetMapping(params = "limit=1000")
  public OrganizationsResponse getOrganizations();

  public record OrganizationsResponse(List<Organization> organizations) {}

  public record Organization(String id, String code, String name) {}
}
