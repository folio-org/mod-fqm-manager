package org.folio.fqm.config;

import lombok.extern.log4j.Log4j2;
import org.folio.fqm.service.PermissionsBypassService;
import org.folio.fqm.service.PermissionsRegularService;
import org.folio.fqm.service.PermissionsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@Configuration
public class PermissionsConfiguration {

  @Value("${mod-fqm-manager.bypass-permissions:false}")
  private boolean bypassPermissionChecks;

  @Bean
  public PermissionsService permissionsService(
    // lazy-loading here to avoid instantiation of the unused one
    ApplicationContext applicationContext
  ) {
    if (bypassPermissionChecks) {
      log.warn("********** BYPASSING PERMISSIONS CHECKS **********");
      return applicationContext.getBean(PermissionsBypassService.class);
    } else {
      return applicationContext.getBean(PermissionsRegularService.class);
    }
  }
}
