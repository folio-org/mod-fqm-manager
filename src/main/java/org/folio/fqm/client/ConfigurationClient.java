package org.folio.fqm.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

@Service
@FeignClient(name = "configurations")
public interface ConfigurationClient {
  @GetMapping("/entries?query=(module==ORG and configName==localeSettings)")
  String getLocaleSettings();
}
