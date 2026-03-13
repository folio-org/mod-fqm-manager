package org.folio.fqm.client;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Client for retrieving patron-group reference data used by migration strategies.
 *
 * <p>This is an optional dependency in the module descriptor.
 *
 * <p>This client is only used by migrations that require patron groups, so it is safe to call
 * only when the users module is present.
 */
@HttpExchange(url = "groups", accept = MediaType.APPLICATION_JSON_VALUE)
public interface PatronGroupsClient {

  @GetExchange("?limit=1000")
  PatronGroupsResponse getGroups();

  public record PatronGroupsResponse(List<PatronGroup> usergroups) {}

  public record PatronGroup(String id, String group) {}
}
