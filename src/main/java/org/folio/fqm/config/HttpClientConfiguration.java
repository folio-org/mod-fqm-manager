package org.folio.fqm.config;

import org.folio.fqm.client.CrossTenantEnrichUrlAndHeadersInterceptor;
import org.folio.fqm.client.CrossTenantHttpClient;
import org.folio.fqm.client.LanguageClient;
import org.folio.fqm.client.LocationUnitsClient;
import org.folio.fqm.client.LocationsClient;
import org.folio.fqm.client.ModesOfIssuanceClient;
import org.folio.fqm.client.OrganizationsClient;
import org.folio.fqm.client.PatronGroupsClient;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.NotFoundRestClientAdapterDecorator;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.service.registry.ImportHttpServices;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registers HTTP clients and cross-tenant HTTP infrastructure.
 *
 * <p>{@link ImportHttpServices} registers simple {@code @HttpExchange} interfaces as beans.
 * Cross-tenant wrappers use the dedicated proxy factory defined in this configuration.
 */
@Configuration
@ImportHttpServices({
  LocationsClient.class,
  LocationUnitsClient.class,
  OrganizationsClient.class,
  PatronGroupsClient.class,
  ModesOfIssuanceClient.class
})
public class HttpClientConfiguration {

  @Bean
  public ClientHttpRequestInterceptor crossTenantEnrichUrlAndHeadersInterceptor(FolioExecutionContext executionContext) {
    return new CrossTenantEnrichUrlAndHeadersInterceptor(executionContext);
  }

  /**
   * Builds a RestClient.Builder for cross-tenant requests.
   *
   * <p>This builder uses the regular URL/header enrichment, but preserves any explicit
   * X-Okapi-Tenant header supplied by callers so wrappers like {@link CrossTenantHttpClient}
   * and {@link LanguageClient} can target tenant-specific APIs. It also wires the exchange
   * JSON mapper and optional exchange logging interceptor when present.
   *
   * @implNote This almost exactly duplicates the main RestClient.Builder configuration in
   *           {@link org.folio.spring.config.HttpServiceClientConfiguration} except for the tenant header handling,
   *           so if additional customizations are added there consider whether they should be duplicated here or
   *           extracted to a shared method. It's gross, but we need both builders to be separate beans so the
   *           cross-tenant one can be injected into the cross-tenant client wrappers without affecting regular clients.
   */
  @Bean
  public RestClient.Builder crossTenantRestClientBuilder(
    JsonMapper jsonMapper,
    @Qualifier("crossTenantEnrichUrlAndHeadersInterceptor") ClientHttpRequestInterceptor crossTenantEnrichUrlAndHeadersInterceptor,
    @Qualifier("loggingInterceptor") @Autowired(required = false) ClientHttpRequestInterceptor loggingInterceptor,
    @Qualifier("exchangeJsonMapper") @Autowired(required = false) JsonMapper exchangeJsonMapper
  ) {
    var builder = RestClient.builder()
      .requestInterceptor(crossTenantEnrichUrlAndHeadersInterceptor)
      .configureMessageConverters(configurer ->
        configurer.addCustomConverter(new JacksonJsonHttpMessageConverter(exchangeJsonMapper != null ? exchangeJsonMapper : jsonMapper)));

    if (loggingInterceptor != null) {
      builder.bufferContent((uri, httpMethod) -> true).requestInterceptor(loggingInterceptor);
    }

    return builder;
  }

  /**
   * Creates a dedicated HTTP proxy factory for cross-tenant service clients.
   *
   * <p>The {@link NotFoundRestClientAdapterDecorator} applies consistent handling
   * for HTTP 404 responses across clients created from this factory.
   */
  @Bean
  public HttpServiceProxyFactory crossTenantHttpServiceProxyFactory(
    @Qualifier("crossTenantRestClientBuilder") RestClient.Builder crossTenantRestClientBuilder
  ) {
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(crossTenantRestClientBuilder.build()))
      .exchangeAdapterDecorator(NotFoundRestClientAdapterDecorator::new)
      .build();
  }

}
