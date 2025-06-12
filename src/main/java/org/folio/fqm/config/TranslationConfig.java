package org.folio.fqm.config;

import java.util.List;
import java.util.Locale;
import org.folio.spring.i18n.config.TranslationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TranslationConfig {

  @Bean
  public TranslationConfiguration translationConfiguration() {
    return new TranslationConfiguration(List.of("/translations/", "/external-translations/"), Locale.ENGLISH);
  }
}
