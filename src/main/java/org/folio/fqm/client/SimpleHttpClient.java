package org.folio.fqm.client;

import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.JsonNode;

/**
 * Wrapper client for GET requests to arbitrary FOLIO API paths.
 *
 * @implNote This class intentionally wraps an internal {@code @HttpExchange} interface so the
 *           public API stays focused on the module's string-based contract while HTTP client
 *           details remain internal.
 */
@Component
public class SimpleHttpClient {

  private final SimpleHttpServiceClient delegate;

  @Autowired
  public SimpleHttpClient(HttpServiceProxyFactory httpServiceProxyFactory) {
    this(httpServiceProxyFactory.createClient(SimpleHttpServiceClient.class));
  }

  SimpleHttpClient(SimpleHttpServiceClient delegate) {
    this.delegate = delegate;
  }

  /**
   * Retrieve arbitrary JSON data from a FOLIO API endpoint.
   *
   * @param path path of the API endpoint, relative to Okapi URL
   * @param queryParams query parameters to send
   * @return JSON response body serialized as a string
   */
  public String get(String path, Map<String, String> queryParams) {
    return delegate.fetch(URI.create(path), queryParams).toString();
  }

  @HttpExchange(url = ".", accept = MediaType.APPLICATION_JSON_VALUE)
  interface SimpleHttpServiceClient {

    @GetExchange
    JsonNode fetch(URI uri, @RequestParam Map<String, String> queryParams);
  }
}
