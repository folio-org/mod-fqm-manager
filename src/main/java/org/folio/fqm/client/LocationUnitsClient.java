package org.folio.fqm.client;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Client for retrieving location-unit reference data used by migration strategies.
 *
 * <p>This is an optional dependency in the module descriptor.
 *
 * <p>This client is only used by migrations that require location units, so it is safe to call
 * only when the related module is present.
 */
@HttpExchange(url = "location-units", accept = MediaType.APPLICATION_JSON_VALUE)
public interface LocationUnitsClient {

  @GetExchange("/libraries?limit=1000")
  LibraryLocationsResponse getLibraries();

  public record LibraryLocationsResponse(List<LibraryLocation> loclibs) {}

  public record LibraryLocation(String id, String name, String code) {}
}
