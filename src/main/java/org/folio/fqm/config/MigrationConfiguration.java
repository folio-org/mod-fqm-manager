package org.folio.fqm.config;

import java.util.UUID;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MigrationConfiguration {

  public static final String VERSION_KEY = "_version";
  public static final UUID REMOVED_ENTITY_TYPE_ID = UUID.fromString("deadbeef-dead-dead-dead-deaddeadbeef");

  private static final String CURRENT_VERSION = "23";
  // TODO: replace this with current version in the future?
  private static final String DEFAULT_VERSION = "0";

  public String getCurrentVersion() {
    return CURRENT_VERSION;
  }

  public String getDefaultVersion() {
    return DEFAULT_VERSION;
  }
}
