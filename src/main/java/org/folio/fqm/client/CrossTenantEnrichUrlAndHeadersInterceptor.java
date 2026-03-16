package org.folio.fqm.client;

import static org.folio.spring.integration.XOkapiHeaders.TENANT;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.client.EnrichUrlAndHeadersInterceptor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Request enrichment for cross-tenant clients that preserves caller-provided X-Okapi-Tenant.
 */
public class CrossTenantEnrichUrlAndHeadersInterceptor implements ClientHttpRequestInterceptor {

  private final EnrichUrlAndHeadersInterceptor delegate;

  public CrossTenantEnrichUrlAndHeadersInterceptor(FolioExecutionContext executionContext) {
    this.delegate = new EnrichUrlAndHeadersInterceptor(new CrossTenantFolioExecutionContext(executionContext));
  }

  @Override
  public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    return delegate.intercept(request, body, execution);
  }

  /**
   * Wrapper execution context that keeps all default enrichment behavior except replacing
   * x-okapi-tenant from context headers so callers can set the target tenant explicitly.
   */
  private static final class CrossTenantFolioExecutionContext implements FolioExecutionContext {

    private final FolioExecutionContext delegate;

    private CrossTenantFolioExecutionContext(FolioExecutionContext delegate) {
      this.delegate = delegate;
    }

    @Override
    public String getTenantId() {
      // This is unused by the interceptor, but let's throw something in case that changes, to be safe
      throw new IllegalStateException("getTenantId() called unexpectedly");
    }

    @Override
    public String getOkapiUrl() {
      return delegate.getOkapiUrl();
    }

    @Override
    public String getToken() {
      return delegate.getToken();
    }

    @Override
    public java.util.UUID getUserId() {
      return delegate.getUserId();
    }

    @Override
    public String getRequestId() {
      return delegate.getRequestId();
    }

    @Override
    public Map<String, Collection<String>> getAllHeaders() {
      return delegate.getAllHeaders();
    }

    @Override
    public Map<String, Collection<String>> getOkapiHeaders() {
      Map<String, Collection<String>> okapiHeaders = delegate.getOkapiHeaders();
      if (okapiHeaders == null) {
        return Map.of();
      }

      Map<String, Collection<String>> filteredHeaders = new HashMap<>(okapiHeaders);
      filteredHeaders.keySet().removeIf(TENANT::equalsIgnoreCase);
      return filteredHeaders;
    }

    @Override
    public FolioModuleMetadata getFolioModuleMetadata() {
      return delegate.getFolioModuleMetadata();
    }

    @Override
    public Object getInstance() {
      return delegate.getInstance();
    }

    @Override
    public <T> T execute(Callable<T> callable) {
      return delegate.execute(callable);
    }
  }
}
