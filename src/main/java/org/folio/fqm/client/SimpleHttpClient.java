package org.folio.fqm.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@Service
@FeignClient(name = "simple-http-client", url = ".")
public interface SimpleHttpClient {
  /**
   * Retrieve arbitrary data from a FOLIO API endpoint.
   *
   * @param path        - the path of the API endpoint
   * @param queryParams - a map of query parameters to pass to the API endpoint
   * @return the body of the response (JSON)
   */
  @GetMapping(value = "/{path}", produces = MediaType.APPLICATION_JSON_VALUE)
  String get(@PathVariable String path, @SpringQueryMap Map<String, String> queryParams);

  /**
   * Retrieve arbitrary data from a FOLIO API endpoint for the specified tenant.
   *
   * @param path        - the path of the API endpoint
   * @param queryParams - a map of query parameters to pass to the API endpoint
   * @param tenant      - FOLIO tenant from which to retrieve data
   * @return the body of the response (JSON)
   */
  @GetMapping(value = "/{path}", produces = MediaType.APPLICATION_JSON_VALUE)
  String get(@PathVariable String path, @SpringQueryMap Map<String, String> queryParams, @RequestHeader("X-Okapi-Tenant") String tenant);
}
