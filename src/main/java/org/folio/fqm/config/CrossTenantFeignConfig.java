package org.folio.fqm.config;

import feign.Client;
import okhttp3.OkHttpClient;
import org.folio.spring.FolioExecutionContext;
import org.springframework.context.annotation.Bean;
import org.folio.fqm.client.CrossTenantClient;

public class CrossTenantFeignConfig {

  @Bean
  public Client feignClient(FolioExecutionContext executionContext) {
    return new CrossTenantClient(executionContext, new OkHttpClient());
  }
}

