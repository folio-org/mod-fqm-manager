package org.folio.fqm.client;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Client for retrieving location reference data used by migration strategies.
 *
 * <p>This is an optional dependency in the module descriptor.
 *
 * <p>This client is only used by migrations that require locations, so it is safe to call
 * only when the locations module is present.
 */
@HttpExchange(url = "locations", accept = MediaType.APPLICATION_JSON_VALUE)
public interface LocationsClient {

  @GetExchange("?limit=1000")
  LocationsResponse getLocations();

  public record LocationsResponse(List<Location> locations) {}

  public record Location(String id, String name) {}
}
