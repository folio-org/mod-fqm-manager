package org.folio.fqm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableCaching
@EnableFeignClients
public class ModFqmManagerApplication {

  public static void main(String[] args) {
    SpringApplication.run(ModFqmManagerApplication.class, args);
  }


}
