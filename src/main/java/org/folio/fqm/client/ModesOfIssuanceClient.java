package org.folio.fqm.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

@Service
@FeignClient(name = "modes-of-issuance")
public interface ModesOfIssuanceClient {
  @GetMapping(params = "limit=1000")
  public ModesOfIssuanceResponse getModesOfIssuance();

  public record ModesOfIssuanceResponse(List<ModeOfIssuance> issuanceModes) {}

  public record ModeOfIssuance(String id, String name) {}
}
