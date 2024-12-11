package org.folio.fqm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import javax.annotation.CheckForNull;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.service.FqlService;
import org.folio.fqm.config.MigrationConfiguration;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationStrategyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@AllArgsConstructor(onConstructor_ = @Autowired)
public class MigrationService {

  private final FqlService fqlService;
  private final MigrationConfiguration migrationConfiguration;
  private final MigrationStrategyRepository migrationStrategyRepository;
  private final ObjectMapper objectMapper;

  public String getLatestVersion() {
    return migrationConfiguration.getCurrentVersion();
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
      return migrationConfiguration.getDefaultVersion();
    }
    try {
      return Optional
        .ofNullable(((ObjectNode) objectMapper.readTree(fqlQuery)).get(MigrationConfiguration.VERSION_KEY))
        .map(JsonNode::asText)
        .orElse(migrationConfiguration.getDefaultVersion());
    } catch (JsonProcessingException e) {
      return migrationConfiguration.getDefaultVersion();
    }
  }
}
