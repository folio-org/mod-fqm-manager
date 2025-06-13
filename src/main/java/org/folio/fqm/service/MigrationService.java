package org.folio.fqm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;
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

    if (!onlyVersionChanged(migratableQueryInformation, migratedQueryInformation)) {
      throw new MigrationQueryChangedException(migratedQueryInformation);
    }
  }

  /**
   * Checks if only the version has changed between the original and migrated query information.
   *
   * @param original the original query information
   * @param migrated the migrated query information
   * @return true if only the version has changed, false otherwise
   */
  private boolean onlyVersionChanged(MigratableQueryInformation original, MigratableQueryInformation migrated) {
    // Check if basic fields are equal
    if (!Objects.equals(original.entityTypeId(), migrated.entityTypeId()) ||
        !Objects.equals(original.fields(), migrated.fields()) ||
        !Objects.equals(original.warnings(), migrated.warnings())) {
      return false;
    }

    // If FQL queries are null or one of them is null, we've already checked all fields
    if (original.fqlQuery() == null && migrated.fqlQuery() == null) {
      return true;
    }

    if (original.fqlQuery() == null || migrated.fqlQuery() == null) {
      return false;
    }



    // Compare FQL queries without version
    try {
      ObjectNode originalNode = (ObjectNode) objectMapper.readTree(original.fqlQuery());
      ObjectNode migratedNode = (ObjectNode) objectMapper.readTree(migrated.fqlQuery());

      // Remove version for comparison
      originalNode.remove(MigrationConfiguration.VERSION_KEY);
      migratedNode.remove(MigrationConfiguration.VERSION_KEY);

      return originalNode.equals(migratedNode);
    } catch (JsonProcessingException e) {
      // If we can't parse the JSON, assume something changed
      return false;
    }
  }
}
