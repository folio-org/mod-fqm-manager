package org.folio.fqm.config;

import java.util.List;
import java.util.Locale;
import org.folio.spring.i18n.config.TranslationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class TranslationConfig {

  @Bean
  @Primary
  public TranslationConfiguration translationConfiguration() {
    return new TranslationConfiguration(List.of("/translations/", "/external-translations/"), Locale.ENGLISH);
  }
}
