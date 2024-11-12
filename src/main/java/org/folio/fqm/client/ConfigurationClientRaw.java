package org.folio.fqm.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Provides raw access to the mod-configurations API. You probably want {@link ConfigurationClient} instead.
 */
@Service
@FeignClient(name = "configurations")
public interface ConfigurationClientRaw {
  @GetMapping("/entries?query=(module==ORG and configName==localeSettings)")
  public abstract String getLocaleSettings();
}
