package org.folio.fqm.client;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;

@UnitTest
class CrossTenantClientTest {

  static List<Arguments> urlPreparationCases() {
    return List.of(
      arguments("http://test-url", null, "http://test-url"),
      arguments("http://test-url", "http://okapi", "http://okapi/test-url"),
      arguments("http://test-url", "http://okapi/", "http://okapi/test-url")
    );
  }

  @ParameterizedTest
  @MethodSource("urlPreparationCases")
  void testUrlPreparation(String requestUrl, String okapiUrl, String expected) throws IOException {
    // Use a concrete request so enrichment runs against real request metadata.
    HttpRequest request = createRequest(requestUrl, new HttpHeaders());

    FolioExecutionContext context = mock(FolioExecutionContext.class);
    when(context.getOkapiUrl()).thenReturn(okapiUrl);
    when(context.getOkapiHeaders()).thenReturn(Map.of());
    when(context.getAllHeaders()).thenReturn(Map.of());

    ClientHttpResponse response = mock(ClientHttpResponse.class);
    RecordingExecution execution = spy(new RecordingExecution(response));
    byte[] body = "test-data".getBytes();

    CrossTenantEnrichUrlAndHeadersInterceptor interceptor = new CrossTenantEnrichUrlAndHeadersInterceptor(context);
    interceptor.intercept(request, body, execution);

    // Verify delegation happened once and that URL enrichment produced the expected URI.
    verify(execution, times(1)).execute(any(HttpRequest.class), eq(body));
    assertThat(execution.request().getURI().toString(), is(expected));
  }

  @Test
  void testHeaderPreparationWithNoLanguage() throws IOException {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.put("a", List.of("a-val"));
    requestHeaders.put("b", List.of("b-val"));
    requestHeaders.put("c", List.of("c-val"));
    HttpRequest request = createRequest("http://test-url", requestHeaders);

    FolioExecutionContext context = mock(FolioExecutionContext.class);
    when(context.getOkapiUrl()).thenReturn("http://okapi");
    when(context.getOkapiHeaders()).thenReturn(Map.of("z", List.of("z-val")));
    when(context.getAllHeaders()).thenReturn(Map.of("misc-1", List.of("misc-1-val"), "misc-2", List.of("misc-2-val")));

    ClientHttpResponse response = mock(ClientHttpResponse.class);
    RecordingExecution execution = spy(new RecordingExecution(response));
    byte[] body = "test-data".getBytes();

    CrossTenantEnrichUrlAndHeadersInterceptor interceptor = new CrossTenantEnrichUrlAndHeadersInterceptor(context);
    interceptor.intercept(request, body, execution);

    // Incoming Okapi headers are merged, but unrelated context headers are not propagated.
    verify(execution, times(1)).execute(any(HttpRequest.class), eq(body));

    var headers = execution.request().getHeaders();
    assertThat(headers.get("a"), is(List.of("a-val")));
    assertThat(headers.get("b"), is(List.of("b-val")));
    assertThat(headers.get("c"), is(List.of("c-val")));
    assertThat(headers.get("z"), is(List.of("z-val")));
    assertThat(headers.size(), is(4));
  }

  @Test
  void testHeaderPreparationDoesNotPropagateContextTenantHeader() throws IOException {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.put("a", List.of("a-val"));
    HttpRequest request = createRequest("http://test-url", requestHeaders);

    FolioExecutionContext context = mock(FolioExecutionContext.class);
    when(context.getOkapiUrl()).thenReturn("http://okapi");
    when(context.getOkapiHeaders())
      .thenReturn(
        Map.of(
          "X-Okapi-Tenant", List.of("incoming-tenant"),
          "z", List.of("z-val")
        )
      );
    when(context.getAllHeaders()).thenReturn(Map.of());

    ClientHttpResponse response = mock(ClientHttpResponse.class);
    RecordingExecution execution = spy(new RecordingExecution(response));

    CrossTenantEnrichUrlAndHeadersInterceptor interceptor = new CrossTenantEnrichUrlAndHeadersInterceptor(context);
    interceptor.intercept(request, "test-data".getBytes(), execution);

    // The context tenant must be filtered out so only explicit caller tenant is ever sent.
    var headers = execution.request().getHeaders();
    assertThat(headers.get("a"), is(List.of("a-val")));
    assertThat(headers.get("z"), is(List.of("z-val")));
    assertThat(headers.getFirst("x-okapi-tenant"), nullValue());
    assertThat(headers.size(), is(2));
  }

  @Test
  void testHeaderPreparationWithLanguage() throws IOException {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.put("a", List.of("a-val"));
    requestHeaders.put("b", List.of("b-val"));
    requestHeaders.put("c", List.of("c-val"));
    requestHeaders.put("x-okapi-tenant", List.of("outgoing-tenant"));
    HttpRequest request = createRequest("http://test-url", requestHeaders);

    FolioExecutionContext context = mock(FolioExecutionContext.class);
    when(context.getOkapiUrl()).thenReturn("http://okapi");
    when(context.getOkapiHeaders())
      .thenReturn(
        Map.of(
          "z", List.of("z-val"),
          "x-okapi-tenant", List.of("incoming-tenant")
        )
      );
    when(context.getAllHeaders())
      .thenReturn(
        Map.of(
          "misc-1", List.of("misc-1-val"),
          "accept-language", List.of("en-US,en;q=0.9"),
          "misc-2", List.of("misc-2-val")
        )
      );

    ClientHttpResponse response = mock(ClientHttpResponse.class);
    RecordingExecution execution = spy(new RecordingExecution(response));
    byte[] body = "test-data".getBytes();

    CrossTenantEnrichUrlAndHeadersInterceptor interceptor = new CrossTenantEnrichUrlAndHeadersInterceptor(context);
    interceptor.intercept(request, body, execution);

    // Caller-provided x-okapi-tenant should be preserved, while Accept-Language is copied from context.
    verify(execution, times(1)).execute(any(HttpRequest.class), eq(body));

    var headers = execution.request().getHeaders();
    assertThat(headers.get("a"), is(List.of("a-val")));
    assertThat(headers.get("b"), is(List.of("b-val")));
    assertThat(headers.get("c"), is(List.of("c-val")));
    assertThat(headers.get("z"), is(List.of("z-val")));
    assertThat(headers.get("x-okapi-tenant"), is(List.of("outgoing-tenant")));
    assertThat(headers.get("Accept-Language"), is(List.of("en-US,en;q=0.9")));
    assertThat(headers.size(), is(6));
  }

  @Test
  void testHeaderPreparationWithNullOkapiHeaders() throws IOException {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.put("x-okapi-tenant", List.of("outgoing-tenant"));
    HttpRequest request = createRequest("http://test-url", requestHeaders);

    FolioExecutionContext context = mock(FolioExecutionContext.class);
    when(context.getOkapiUrl()).thenReturn("http://okapi");
    when(context.getOkapiHeaders()).thenReturn(null);
    when(context.getAllHeaders()).thenReturn(Map.of("accept-language", List.of("en-US,en;q=0.9")));

    ClientHttpResponse response = mock(ClientHttpResponse.class);
    RecordingExecution execution = spy(new RecordingExecution(response));

    CrossTenantEnrichUrlAndHeadersInterceptor interceptor = new CrossTenantEnrichUrlAndHeadersInterceptor(context);
    interceptor.intercept(request, "test-data".getBytes(), execution);

    // Null context headers are treated as empty; explicit tenant and language are still applied.
    var headers = execution.request().getHeaders();
    assertThat(headers.get("x-okapi-tenant"), is(List.of("outgoing-tenant")));
    assertThat(headers.get("Accept-Language"), is(List.of("en-US,en;q=0.9")));
    assertThat(headers.size(), is(2));
  }

  @Test
  void testRequestExecution() throws IOException {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.put("a", List.of("a-val"));
    HttpRequest request = createRequest("http://test-url", requestHeaders);

    final FolioExecutionContext context = mock(FolioExecutionContext.class);
    when(context.getOkapiUrl()).thenReturn("http://okapi");
    when(context.getOkapiHeaders()).thenReturn(Map.of("z", List.of("z-val")));
    when(context.getAllHeaders())
      .thenReturn(
        Map.of(
          "misc-1", List.of("misc-1-val"),
          "accept-language", List.of("en-US,en;q=0.9"),
          "misc-2", List.of("misc-2-val")
        )
      );

    ClientHttpResponse response = mock(ClientHttpResponse.class);
    RecordingExecution execution = spy(new RecordingExecution(response));
    byte[] body = "test-data".getBytes();

    // ensure response is returned upon execution
    CrossTenantEnrichUrlAndHeadersInterceptor interceptor = new CrossTenantEnrichUrlAndHeadersInterceptor(context);
    assertThat(interceptor.intercept(request, body, execution), is(response));

    // Verify the interceptor delegates exactly once with an enriched request.
    verify(execution, times(1)).execute(any(HttpRequest.class), eq(body));

    assertThat(execution.request().getMethod(), is(HttpMethod.GET));
    assertThat(execution.request().getURI().toString(), is("http://okapi/test-url"));
    var headers = execution.request().getHeaders();
    assertThat(headers.get("a"), is(List.of("a-val")));
    assertThat(headers.get("z"), is(List.of("z-val")));
    assertThat(headers.get("Accept-Language"), is(List.of("en-US,en;q=0.9")));
    assertThat(headers.size(), is(3));
  }

  private static HttpRequest createRequest(String requestUrl, HttpHeaders headers) {
    MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create(requestUrl));
    request.getHeaders().putAll(headers);
    return request;
  }

  /**
   * Captures the request passed to {@link ClientHttpRequestExecution#execute(HttpRequest, byte[])}
   * so tests can assert on the final enriched request without ArgumentCaptor boilerplate.
   */
  private static final class RecordingExecution implements ClientHttpRequestExecution {

    private final ClientHttpResponse response;
    private HttpRequest request;

    private RecordingExecution(ClientHttpResponse response) {
      this.response = response;
    }

    @Override
    public ClientHttpResponse execute(HttpRequest request, byte[] body) {
      this.request = request;
      return response;
    }

    private HttpRequest request() {
      return request;
    }
  }
}
