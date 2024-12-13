package org.folio.fqm.client;

import feign.Client;
import feign.Request;
import feign.Response;
import feign.okhttp.OkHttpClient;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.integration.XOkapiHeaders;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Log4j2
public class CrossTenantClient implements Client {

  private final OkHttpClient delegate;
  private final FolioExecutionContext executionContext;

  public CrossTenantClient(FolioExecutionContext executionContext, okhttp3.OkHttpClient okHttpClient) {
    this.delegate = new OkHttpClient(okHttpClient);
    this.executionContext = executionContext;
  }

  public Response execute(Request request, Request.Options options) throws IOException {
    String url = prepareUrl(request.url(), this.executionContext);
    Map<String, Collection<String>> allHeaders = prepareHeaders(request, this.executionContext);
    Request requestWithUrl = Request.create(request.httpMethod(), url, allHeaders, request.body(), request.charset(), request.requestTemplate());
    log.debug("FolioExecutionContext: {};\nPrepared the Feign Client Request: {} with headers {};\nCurrent thread: {}", this.executionContext, requestWithUrl, allHeaders, Thread.currentThread().getName());
    return this.delegate.execute(requestWithUrl, options);
  }

  static String prepareUrl(String requestUrl, FolioExecutionContext context) {
    String okapiUrl = context.getOkapiUrl();
    if (okapiUrl == null) {
      return requestUrl;
    } else {
      okapiUrl = StringUtils.appendIfMissing(okapiUrl, "/");
      return requestUrl.replace("http://", okapiUrl);
    }
  }

  /**
   * Prepare headers. Copies all okapi headers from FolioExecutionContext excluding x-okapi-tenant. The exclusion of
   * x-okapi-tenant allows for manual specification of the desired tenant, enabling cross-tenant requests.
   *
   * @param request Feign request
   * @param context Folio execution context
   * @return Map of headers to be included in the request
   */
  static Map<String, Collection<String>> prepareHeaders(Request request, FolioExecutionContext context) {
    Map<String, Collection<String>> allHeaders = new HashMap<>(request.headers());
    var okapiHeaders = new HashMap<>(context.getOkapiHeaders());
    okapiHeaders.remove(XOkapiHeaders.TENANT);
    allHeaders.putAll(okapiHeaders);
    context.getAllHeaders()
      .keySet()
      .stream()
      .filter("Accept-Language"::equalsIgnoreCase)
      .findFirst()
      .map(key -> context.getAllHeaders().get(key)).ifPresent(values -> allHeaders.put("Accept-Language", values));
    return allHeaders;
  }
}
