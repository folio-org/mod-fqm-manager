package org.folio.fqm.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

@Service
@FeignClient(name = "location-units")
public interface LocationUnitsClient {
  @GetMapping(value = "/libraries", params = "limit=1000")
  public LibraryLocationsResponse getLibraries();

  public record LibraryLocationsResponse(List<LibraryLocation> loclibs) {}

  public record LibraryLocation(String id, String name, String code) {}
}
