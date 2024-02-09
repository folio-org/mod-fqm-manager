package org.folio.fqm.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Service
@FeignClient(name = "simple-http-client", url = ".")
public interface SimpleHttpClient {
  /**
   * Retrieve arbitrary data from a FOLIO API endpoint.
   * @return the body of the response (JSON)
   */
  @GetMapping(value = "/{path}", produces = MediaType.APPLICATION_JSON_VALUE)
  String get(@PathVariable String path);
}
