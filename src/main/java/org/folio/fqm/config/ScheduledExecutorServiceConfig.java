package org.folio.fqm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class ScheduledExecutorServiceConfig {
  @Bean(destroyMethod = "shutdown")
  public ScheduledExecutorService scheduledExecutorService() {
    return Executors.newScheduledThreadPool(1);
  }
}
