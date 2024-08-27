package org.folio.fqm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.CheckForNull;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.service.FqlService;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationStrategyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@AllArgsConstructor(onConstructor_ = @Autowired)
public class MigrationService {

  public static final String VERSION_KEY = "_version";

  public static final UUID REMOVED_ENTITY_TYPE_ID = UUID.fromString("deadbeef-dead-dead-dead-deaddeadbeef");

  protected static final String CURRENT_VERSION = "2";

  // TODO: replace this with current version in the future?
  protected static final String DEFAULT_VERSION = "0";

  private final FqlService fqlService;
  private final MigrationStrategyRepository migrationStrategyRepository;
  private final ObjectMapper objectMapper;

  public String getLatestVersion() {
    return CURRENT_VERSION;
  }

  public boolean isMigrationNeeded(@CheckForNull String fqlQuery) {
    return !this.getLatestVersion().equals(getVersion(fqlQuery));
  }

  public boolean isMigrationNeeded(MigratableQueryInformation migratableQueryInformation) {
    return isMigrationNeeded(migratableQueryInformation.fqlQuery());
  }

  public MigratableQueryInformation migrate(MigratableQueryInformation migratableQueryInformation) {
    while (isMigrationNeeded(migratableQueryInformation)) {
      for (MigrationStrategy strategy : migrationStrategyRepository.getMigrationStrategies()) {
        if (strategy.applies(getVersion(migratableQueryInformation.fqlQuery()))) {
          log.info("Applying {} to {}", strategy.getLabel(), migratableQueryInformation);
          migratableQueryInformation = strategy.apply(fqlService, migratableQueryInformation);
        }
      }
    }

    return migratableQueryInformation;
  }

  public String getVersion(@CheckForNull String fqlQuery) {
    if (fqlQuery == null) {
      return DEFAULT_VERSION;
    }
    try {
      return Optional
        .ofNullable(((ObjectNode) objectMapper.readTree(fqlQuery)).get(VERSION_KEY))
        .map(JsonNode::asText)
        .orElse(DEFAULT_VERSION);
    } catch (JsonProcessingException e) {
      return DEFAULT_VERSION;
    }
  }
}
