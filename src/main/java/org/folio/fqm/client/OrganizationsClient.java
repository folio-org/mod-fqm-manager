package org.folio.fqm.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

@Service
@FeignClient(name = "organizations/organizations")
public interface OrganizationsClient {
  @GetMapping(params = "limit=1000")
  public OrganizationsResponse getOrganizations();

  public record OrganizationsResponse(List<Organization> organizations) {}

  public record Organization(String id, String code, String name) {}
}
