package org.folio.fqm.client;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

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

  @InjectMocks
  private LocaleClient localeClient;

  @Mock
  private LocaleClient.LocaleClientRaw underlyingClient;

  @Spy
  private ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void testGetLocaleSettingsWhenPresent() {
    when(underlyingClient.getLocaleSettings()).thenReturn(LOCALE_JSON);

    LocaleClient.LocaleSettings expected = new LocaleClient.LocaleSettings("en-US", "USD", "America/New_York", "latn");
    assertThat(localeClient.getLocaleSettings(), is(expected));
  }

  @Test
  void testGetLocaleSettingsHandlesException() {
    when(underlyingClient.getLocaleSettings())
      .thenThrow(new FeignException.Unauthorized("", mock(feign.Request.class), null, null));

    LocaleClient.LocaleSettings expectedDefault = new LocaleClient.LocaleSettings("en-US", "USD", "UTC", "latn");
    assertThat(localeClient.getLocaleSettings(), is(expectedDefault));
  }

  @Test
  void testGetLocaleSettingsHandlesInvalidJson() {
    when(underlyingClient.getLocaleSettings()).thenReturn("invalid json");

    LocaleClient.LocaleSettings expectedDefault = new LocaleClient.LocaleSettings("en-US", "USD", "UTC", "latn");
    assertThat(localeClient.getLocaleSettings(), is(expectedDefault));
  }
}
