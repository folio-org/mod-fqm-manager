package org.folio.fqm.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.time.DateTimeException;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Convenience methods wrapping {@link SettingsClientRaw}.
 */
// this must be a separate class as feign clients must be interfaces,
// disallowing any injection (ObjectMapper) or fields (our logger)
@Log4j2
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class SettingsClient {

  private final ObjectMapper objectMapper;
  private final SettingsClientRaw underlyingClient;

  public ZoneId getTenantTimezone() {
    try {
      String localeSettingsResponse = underlyingClient.getLocaleSettings();
      JsonNode localeSettingsNode = objectMapper.readTree(localeSettingsResponse);
      String timezone = localeSettingsNode
        .path("items")
        .get(0)
        .path("value")
        .get("timezone")
        .asText();
      return ZoneId.of(timezone);
    } catch (JsonProcessingException | FeignException.Unauthorized | NullPointerException | DateTimeException e) {
      log.error("Failed to retrieve timezone information from mod-settings. Defaulting to UTC.", e);
      return ZoneId.of("UTC");
    }
  }
}
