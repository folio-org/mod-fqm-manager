package org.folio.fqm.client;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * Client for the /locale API.
 *
 * @implNote This wrapper centralizes locale parsing and resilience behavior (default values on
 *           HTTP/JSON errors and safe timezone handling) so callers always receive a usable
 *           {@link LocaleSettings} value.
 */
@Log4j2
@Component
public class LocaleClient {

  private final ObjectMapper objectMapper;
  private final LocaleHttpServiceClient underlyingClient;

  @Autowired
  public LocaleClient(ObjectMapper objectMapper, HttpServiceProxyFactory httpServiceProxyFactory) {
    this(objectMapper, httpServiceProxyFactory.createClient(LocaleHttpServiceClient.class));
  }

  LocaleClient(ObjectMapper objectMapper, LocaleHttpServiceClient underlyingClient) {
    this.objectMapper = objectMapper;
    this.underlyingClient = underlyingClient;
  }

  /**
   * Internal HTTP service client for /locale.
   */
  @HttpExchange(url = "locale")
  interface LocaleHttpServiceClient {
    @GetExchange
    JsonNode fetchLocaleSettings();
  }

  public record LocaleSettings(
    String locale,
    String currency,
    String timezone,
    String numberingSystem
  ) {
    public ZoneId getZoneId() {
      try {
        return ZoneId.of(timezone);
      } catch (DateTimeException e) {
        log.error("Invalid timezone '{}', defaulting to UTC.", timezone, e);
        return ZoneId.of("UTC");
      }
    }
  }

  public LocaleSettings getLocaleSettings() {
    try {
      JsonNode response = underlyingClient.fetchLocaleSettings();
      return objectMapper.treeToValue(response, LocaleSettings.class);
    } catch (JacksonException | RestClientException | NullPointerException e) {
      log.error("Failed to retrieve locale information. Defaulting to en-US, USD, UTC, latn.", e);
      return new LocaleSettings("en-US", "USD", "UTC", "latn");
    }
  }
}
