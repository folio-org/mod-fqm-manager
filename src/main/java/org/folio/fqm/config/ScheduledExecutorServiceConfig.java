package org.folio.fqm.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
@Log4j2
public class ScheduledExecutorServiceConfig {
  @Bean
  public ScheduledExecutorService scheduledExecutorService() {
    return Executors.newScheduledThreadPool(1);
  }

  @PreDestroy
  public void shutdownExecutorService(ScheduledExecutorService executorService) {
    log.info("Shutting down ScheduledExecutorService...");
    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
        log.warn("Scheduled executor service did not terminate in time. Forcing shutdown...");
        executorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      log.error("Interrupted while shutting down executorService", e);
      Thread.currentThread().interrupt();
    }
  }
}
