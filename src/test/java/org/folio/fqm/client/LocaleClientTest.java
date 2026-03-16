package org.folio.fqm.client;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.service.invoker.HttpExchangeAdapter;
import org.springframework.web.service.invoker.HttpRequestValues;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class LocaleClientTest {

  private static final String LOCALE_JSON =
    """
    {
      "locale": "en-US",
      "currency": "USD",
      "timezone": "America/New_York",
      "numberingSystem": "latn"
    }
    """;

  private LocaleClient localeClient;

  @Mock
  private LocaleClient.LocaleHttpServiceClient underlyingClient;

  private ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    localeClient = new LocaleClient(objectMapper, underlyingClient);
  }

  @Test
  void testGetLocaleSettingsWhenPresent() {
    when(underlyingClient.fetchLocaleSettings()).thenReturn(jsonNode(LOCALE_JSON));

    LocaleClient.LocaleSettings expected = new LocaleClient.LocaleSettings("en-US", "USD", "America/New_York", "latn");
    assertThat(localeClient.getLocaleSettings(), is(expected));
  }

  @Test
  void testGetLocaleSettingsHandlesException() {
    when(underlyingClient.fetchLocaleSettings())
      .thenThrow(new RestClientException("Unauthorized"));

    LocaleClient.LocaleSettings expectedDefault = new LocaleClient.LocaleSettings("en-US", "USD", "UTC", "latn");
    assertThat(localeClient.getLocaleSettings(), is(expectedDefault));
  }

  @Test
  void testGetLocaleSettingsHandlesInvalidPayload() {
    when(underlyingClient.fetchLocaleSettings()).thenReturn(jsonNode("\"invalid\""));

    LocaleClient.LocaleSettings expectedDefault = new LocaleClient.LocaleSettings("en-US", "USD", "UTC", "latn");
    assertThat(localeClient.getLocaleSettings(), is(expectedDefault));
  }

  @Test
  void testGetLocaleSettingsWithSpringProxyClient() {
    var factory = HttpServiceProxyFactory.builderFor(new JsonNodeExchangeAdapter(jsonNode(LOCALE_JSON))).build();
    var proxy = factory.createClient(LocaleClient.LocaleHttpServiceClient.class);

    LocaleClient localeClient = new LocaleClient(objectMapper, proxy);

    LocaleClient.LocaleSettings expected = new LocaleClient.LocaleSettings("en-US", "USD", "America/New_York", "latn");
    assertThat(localeClient.getLocaleSettings(), is(expected));
  }

  private JsonNode jsonNode(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid test JSON", e);
    }
  }

  private static final class JsonNodeExchangeAdapter implements HttpExchangeAdapter {
    private final JsonNode response;

    private JsonNodeExchangeAdapter(JsonNode response) {
      this.response = response;
    }

    @Override
    public boolean supportsRequestAttributes() {
      return false;
    }

    @Override
    public void exchange(HttpRequestValues requestValues) {
      // no-op
    }

    @Override
    public HttpHeaders exchangeForHeaders(HttpRequestValues requestValues) {
      return new HttpHeaders();
    }

    @Override
    public <T> T exchangeForBody(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
      @SuppressWarnings("unchecked")
      T typed = (T) response;
      return typed;
    }

    @Override
    public ResponseEntity<Void> exchangeForBodilessEntity(HttpRequestValues requestValues) {
      return ResponseEntity.ok().build();
    }

    @Override
    public <T> ResponseEntity<T> exchangeForEntity(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
      return ResponseEntity.ok(exchangeForBody(requestValues, bodyType));
    }
  }
}
