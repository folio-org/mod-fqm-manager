package org.folio.fqm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.UncheckedIOException;
import java.util.Optional;
import javax.annotation.CheckForNull;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.service.FqlService;
import org.folio.fqm.config.MigrationConfiguration;
import org.folio.fqm.exception.MigrationQueryChangedException;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.folio.fqm.migration.MigrationStrategyRepository;
import org.folio.fqm.migration.MigrationUtils;
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
    if (migratableQueryInformation.version() == null) {
      migratableQueryInformation = migratableQueryInformation.withVersion(migrationConfiguration.getDefaultVersion());
    }

    boolean hadBreakingChanges = false;
    if (isMigrationNeeded(migratableQueryInformation)) {
      for (MigrationStrategy strategy : migrationStrategyRepository.getMigrationStrategies()) {
        if (MigrationUtils.compareVersions(migratableQueryInformation.version(), strategy.getMaximumApplicableVersion()) <= 0
            && strategy.applies(migratableQueryInformation)
        ) {
          log.info("Applying {} to {}", strategy.getLabel(), migratableQueryInformation);
          migratableQueryInformation = strategy.apply(fqlService, migratableQueryInformation);
          hadBreakingChanges |= migratableQueryInformation.hadBreakingChanges();
        }
      }
    }

    try {
      ObjectNode fql = (ObjectNode) objectMapper.readTree(migratableQueryInformation.fqlQuery());
      fql.set(MigrationConfiguration.VERSION_KEY, objectMapper.valueToTree(migrationConfiguration.getCurrentVersion()));
      migratableQueryInformation = migratableQueryInformation.withFqlQuery(objectMapper.writeValueAsString(fql));
    } catch (JsonProcessingException e) {
      log.error("Unable to process JSON", e);
      throw new UncheckedIOException(e);
    }

    return migratableQueryInformation.withHadBreakingChanges(hadBreakingChanges);
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

  /**
   * Migrates the query information and verifies that only the version has changed.
   * If anything other than the version changes, throws a MigrationQueryChangedException.
   *
   * @param migratableQueryInformation the query information to migrate
   * @throws MigrationQueryChangedException if anything other than the version changes
   */
  public void throwExceptionIfQueryNeedsMigration(MigratableQueryInformation migratableQueryInformation) {
    MigratableQueryInformation migratedQueryInformation = migrate(migratableQueryInformation);

    // If the query doesn't need migration, return early
    if (!isMigrationNeeded(migratableQueryInformation)) {
      return;
    }

    if (migratedQueryInformation.hadBreakingChanges()) {
      throw new MigrationQueryChangedException(migratedQueryInformation);
    }

  }

}
