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
class ConfigurationClientTest {

  private static final String UTC_MINUS_THREE_LOCALE =
    """
    {
      "configs": [
        {
           "id":"2a132a01-623b-4d3a-9d9a-2feb777665c2",
           "module":"ORG",
           "configName":"localeSettings",
           "enabled":true,
           "value":"{\\"locale\\":\\"en-US\\",\\"timezone\\":\\"America/Montevideo\\",\\"currency\\":\\"USD\\"}","metadata":{"createdDate":"2024-03-25T17:37:22.309+00:00","createdByUserId":"db760bf8-e05a-4a5d-a4c3-8d49dc0d4e48"}
        }
      ],
      "totalRecords": 1,
      "resultInfo": {"totalRecords":1,"facets":[],"diagnostics":[]}
    }
    """;

  private static final String EMPTY_LOCALE_JSON =
    """
    {
      "configs": [],
      "totalRecords": 0,
      "resultInfo": {"totalRecords":1,"facets":[],"diagnostics":[]}
    }
    """;

  @InjectMocks
  private ConfigurationClient configurationClient;

  @Mock
  private ConfigurationClientRaw underlyingClient;

  @Spy // lazy way to make it inject into ConfigurationClient
  private ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void testTenantTimezoneWhenPresent() {
    when(underlyingClient.getLocaleSettings()).thenReturn(UTC_MINUS_THREE_LOCALE);

    assertThat(configurationClient.getTenantTimezone(), is(ZoneId.of("America/Montevideo")));
  }

  @Test
  void testTenantTimezoneWhenNonePresent() {
    when(underlyingClient.getLocaleSettings()).thenReturn(EMPTY_LOCALE_JSON);

    assertThat(configurationClient.getTenantTimezone(), is(ZoneId.of("UTC")));
  }

  @Test
  void testHandlesException() {
    when(underlyingClient.getLocaleSettings())
      .thenThrow(new FeignException.Unauthorized("", mock(feign.Request.class), null, null));

    assertThat(configurationClient.getTenantTimezone(), is(ZoneId.of("UTC")));
  }
}
