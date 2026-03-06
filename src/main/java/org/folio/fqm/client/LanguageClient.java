package org.folio.fqm.client;

import org.folio.spring.integration.XOkapiHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.JsonNode;

/**
 * Wrapper client for instance language facet data.
 *
 * @implNote This class intentionally wraps an internal {@code @HttpExchange} interface so the
 *           public API stays focused on the module's string-based contract while HTTP client
 *           details remain internal.
 */
@Component
public class LanguageClient {

  private final LanguageHttpServiceClient delegate;

  @Autowired
  public LanguageClient(@Qualifier("crossTenantHttpServiceProxyFactory") HttpServiceProxyFactory crossTenantHttpServiceProxyFactory) {
    this(crossTenantHttpServiceProxyFactory.createClient(LanguageHttpServiceClient.class));
  }

  LanguageClient(LanguageHttpServiceClient delegate) {
    this.delegate = delegate;
  }

  /**
   * Retrieve instance languages in JSON format for the given tenant.
   *
   * @param tenantId target FOLIO tenant id
   * @return JSON response body serialized as a string
   */
  public String get(String tenantId) {
    return delegate.fetch(tenantId).toString();
  }

  @HttpExchange(url = ".", accept = MediaType.APPLICATION_JSON_VALUE)
  interface LanguageHttpServiceClient {

    @GetExchange("/search/instances/facets?query=id=*&limit=1000&facet=languages")
    JsonNode fetch(@RequestHeader(XOkapiHeaders.TENANT) String tenantId);
  }
}
