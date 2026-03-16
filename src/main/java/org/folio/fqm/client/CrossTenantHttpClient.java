package org.folio.fqm.client;

import java.net.URI;
import java.util.Map;
import org.folio.spring.integration.XOkapiHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.JsonNode;

/**
 * Wrapper client for GET requests to arbitrary FOLIO API paths in a target tenant.
 *
 * @implNote This class intentionally wraps an internal {@code @HttpExchange} interface so the
 *           public API stays focused on the module's string-based contract while HTTP client
 *           details remain internal.
 */
@Component
public class CrossTenantHttpClient {

  private final CrossTenantHttpServiceClient delegate;

  @Autowired
  public CrossTenantHttpClient(
    @Qualifier("crossTenantHttpServiceProxyFactory") HttpServiceProxyFactory crossTenantHttpServiceProxyFactory
  ) {
    this(crossTenantHttpServiceProxyFactory.createClient(CrossTenantHttpServiceClient.class));
  }

  CrossTenantHttpClient(CrossTenantHttpServiceClient delegate) {
    this.delegate = delegate;
  }

  /**
   * Retrieve arbitrary JSON data from a FOLIO API endpoint for a specific tenant.
   *
   * @param path path of the API endpoint, relative to Okapi URL
   * @param queryParams query parameters to send
   * @param tenant target FOLIO tenant id
   * @return JSON response body serialized as a string
   */
  public String get(String path, Map<String, String> queryParams, String tenant) {
    return delegate.fetch(URI.create(path), queryParams, tenant).toString();
  }

  @HttpExchange(url = ".", accept = MediaType.APPLICATION_JSON_VALUE)
  interface CrossTenantHttpServiceClient {

    @GetExchange
    JsonNode fetch(
      URI uri,
      @RequestParam Map<String, String> queryParams,
      @RequestHeader(XOkapiHeaders.TENANT) String tenant
    );
  }
}
