package org.folio.fqm.client;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Client for retrieving organization reference data used by migration strategies.
 *
 * <p>This is an optional dependency in the module descriptor.
 *
 * <p>This client is only used by migrations that require organizations, so it is safe to call
 * only when the organizations module is present.
 */
@HttpExchange(url = "organizations/organizations", accept = MediaType.APPLICATION_JSON_VALUE)
public interface OrganizationsClient {

  @GetExchange("?limit=1000")
  OrganizationsResponse getOrganizations();

  public record OrganizationsResponse(List<Organization> organizations) {}

  public record Organization(String id, String code, String name) {}
}
