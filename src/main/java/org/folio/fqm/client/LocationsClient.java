package org.folio.fqm.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

@Service
@FeignClient(name = "locations")
public interface LocationsClient {
  @GetMapping(params = "limit=1000")
  public LocationsResponse getLocations();

  public record LocationsResponse(List<Location> locations) {}

  public record Location(String id, String name) {}
}
