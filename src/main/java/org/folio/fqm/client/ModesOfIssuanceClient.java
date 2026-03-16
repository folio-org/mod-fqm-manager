package org.folio.fqm.client;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Client for retrieving mode-of-issuance reference data used by migration strategies.
 *
 * <p>This is an optional dependency in the module descriptor.
 *
 * <p>This client is only used by migrations that require modes of issuance, so it is safe to call
 * only when the inventory module is present.
 */
@HttpExchange(url = "modes-of-issuance", accept = MediaType.APPLICATION_JSON_VALUE)
public interface ModesOfIssuanceClient {

  @GetExchange("?limit=1000")
  ModesOfIssuanceResponse getModesOfIssuance();

  public record ModesOfIssuanceResponse(List<ModeOfIssuance> issuanceModes) {}

  public record ModeOfIssuance(String id, String name) {}
}
