package org.folio.fqm.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@Log4j2
public class ScheduledExecutorServiceConfig {
  @Bean(destroyMethod = "shutdown")
  public ScheduledExecutorService scheduledExecutorService() {
    return Executors.newScheduledThreadPool(1);
//    return new ScheduledThreadPoolExecutor(1) {
//      @Override
//      public void shutdown() {
//        log.info("ScheduledExecutorService is shutting down...");
//        super.shutdown();
//        log.info("ScheduledExecutorService shutdown completed.");
//      }
//    };
  }
}
