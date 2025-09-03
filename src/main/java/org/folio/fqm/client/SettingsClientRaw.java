package org.folio.fqm.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Provides raw access to the mod-settings API. You probably want {@link SettingsClient} instead.
 */
@Service
@FeignClient(name = "settings")
public interface SettingsClientRaw {
  @GetMapping("/entries?query=(scope==stripes-core.prefs.manage and key==tenantLocaleSettings)")
  public abstract String getLocaleSettings();
}
