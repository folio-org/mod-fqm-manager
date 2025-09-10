package org.folio.fqm.client;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettingsClientTest {

  private static final String UTC_MINUS_THREE_LOCALE =
    """
    {
      "items": [
        {
           "id":"2a132a01-623b-4d3a-9d9a-2feb777665c2",
           "scope": "stripes-core.prefs.manage",
           "key":"tenantLocaleSettings",
           "enabled":true,
           "value": {
             "locale": "en-US",
             "currency": "USD",
             "timezone": "America/Montevideo"
           }
        }
      ],
      "totalRecords": 1,
      "resultInfo": {"totalRecords":1,"facets":[],"diagnostics":[]}
    }
    """;

  private static final String EMPTY_LOCALE_JSON =
    """
    {
      "items": [],
      "totalRecords": 0,
      "resultInfo": {"totalRecords":1,"facets":[],"diagnostics":[]}
    }
    """;

  @InjectMocks
  private SettingsClient settingsClient;

  @Mock
  private SettingsClientRaw underlyingClient;

  @Spy // lazy way to make it inject into SettingsClient
  private ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void testTenantTimezoneWhenPresent() {
    when(underlyingClient.getLocaleSettings()).thenReturn(UTC_MINUS_THREE_LOCALE);

    assertThat(settingsClient.getTenantTimezone(), is(ZoneId.of("America/Montevideo")));
  }

  @Test
  void testTenantTimezoneWhenNonePresent() {
    when(underlyingClient.getLocaleSettings()).thenReturn(EMPTY_LOCALE_JSON);

    assertThat(settingsClient.getTenantTimezone(), is(ZoneId.of("UTC")));
  }

  @Test
  void testHandlesException() {
    when(underlyingClient.getLocaleSettings())
      .thenThrow(new FeignException.Unauthorized("", mock(feign.Request.class), null, null));

    assertThat(settingsClient.getTenantTimezone(), is(ZoneId.of("UTC")));
  }
}
