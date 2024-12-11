package org.folio.fqm.client;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.Request;
import feign.Response;
import feign.okhttp.OkHttpClient;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

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
  void testUrlPreparation(String requestUrl, String okapiUrl, String expected) {
    FolioExecutionContext context = mock(FolioExecutionContext.class);

    when(context.getOkapiUrl()).thenReturn(okapiUrl);
    assertThat(CrossTenantClient.prepareUrl(requestUrl, context), is(expected));
  }

  @Test
  void testHeaderPreparationWithNoLanguage() {
    Request request = mock(Request.class);
    when(request.headers())
      .thenReturn(
        Map.of(
          "a", List.of("a-val"),
          "b", List.of("b-val"),
          "c", List.of("c-val")
        )
      );

    FolioExecutionContext context = mock(FolioExecutionContext.class);
    when(context.getOkapiHeaders()).thenReturn(Map.of("z", List.of("z-val")));
    when(context.getAllHeaders()).thenReturn(Map.of("misc-1", List.of("misc-1-val"), "misc-2", List.of("misc-2-val")));

    assertThat(
      CrossTenantClient.prepareHeaders(request, context),
      is(
        allOf(
          hasEntry("a", List.of("a-val")),
          hasEntry("b", List.of("b-val")),
          hasEntry("c", List.of("c-val")),
          hasEntry("z", List.of("z-val")),
          is(aMapWithSize(4))
        )
      )
    );
  }

  @Test
  void testHeaderPreparationWithLanguage() {
    Request request = mock(Request.class);
    when(request.headers())
      .thenReturn(
        Map.of(
          "a", List.of("a-val"),
          "b", List.of("b-val"),
          "c", List.of("c-val"),
          "x-okapi-tenant", List.of("outgoing-tenant")
        )
      );

    FolioExecutionContext context = mock(FolioExecutionContext.class);
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

    assertThat(
      CrossTenantClient.prepareHeaders(request, context),
      is(
        allOf(
          hasEntry("a", List.of("a-val")),
          hasEntry("b", List.of("b-val")),
          hasEntry("c", List.of("c-val")),
          hasEntry("z", List.of("z-val")),
          hasEntry("x-okapi-tenant", List.of("outgoing-tenant")),
          hasEntry("Accept-Language", List.of("en-US,en;q=0.9")),
          is(aMapWithSize(6))
        )
      )
    );
  }

  @Test
  void testRequestExecution() throws IOException {
    final Request request = Request.create(
      Request.HttpMethod.GET,
      "http://test-url",
      Map.of("a", List.of("a-val")),
      Request.Body.create("test-data"),
      null
    );

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

    final Request.Options options = new Request.Options();

    final CrossTenantClient client = new CrossTenantClient(context, null);

    // best way to check for this without having to do a bunch of feign/okhttp logic
    final OkHttpClient mockedDelegate = mock(OkHttpClient.class);
    ReflectionTestUtils.setField(client, "delegate", mockedDelegate);

    when(mockedDelegate.execute(any(Request.class), eq(options)))
      .thenReturn(Response.builder().request(request).status(299).build());

    // ensure response is returned upon execution
    assertThat(client.execute(request, options).status(), is(299));

    // verify that the correct request was sent with the correct options
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    verify(mockedDelegate, times(1)).execute(requestCaptor.capture(), eq(options));

    assertThat(requestCaptor.getValue().httpMethod(), is(Request.HttpMethod.GET));
    assertThat(requestCaptor.getValue().url(), is("http://okapi/test-url"));
    assertThat(
      requestCaptor.getValue().headers(),
      is(
        allOf(
          hasEntry("a", List.of("a-val")),
          hasEntry("z", List.of("z-val")),
          hasEntry("Accept-Language", List.of("en-US,en;q=0.9")),
          is(aMapWithSize(3))
        )
      )
    );
  }
}
