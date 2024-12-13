package org.folio.fqm.client;

import org.folio.fqm.config.CrossTenantFeignConfig;
import org.folio.spring.integration.XOkapiHeaders;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Service
@FeignClient(name = "language-client", url = "./search/instances/facets?query=id=*&limit=1000&facet=languages", configuration = CrossTenantFeignConfig.class)
public interface LanguageClient {

  /**
   * Retrieve instance languages.
   *
   * @param tenantId FOLIO tenant from which to retrieve data
   * @return the body of the response (JSON)
   */
  @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
  String get(@RequestHeader(XOkapiHeaders.TENANT) String tenantId);
}
