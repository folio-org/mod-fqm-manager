package org.folio.fqm.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.domain.dto.Parameter;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a migration changes something other than the version in a MigratableQueryInformation object.
 */
@Getter
@Log4j2
public class MigrationQueryChangedException extends FqmException {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final MigratableQueryInformation migratedQueryInformation;

  public MigrationQueryChangedException(MigratableQueryInformation migratedQueryInformation) {
    super("Migration changed more than just the version");
    this.migratedQueryInformation = migratedQueryInformation;
  }

  @Override
  public Error getError() {
    Error error = new Error()
      .message("Migration changed more than just the version")
      .code("migration.query.changed");

    // Add the entire migrated query information as a single parameter
    if (migratedQueryInformation != null) {
      try {
        String migratedQueryInfoJson = OBJECT_MAPPER.writeValueAsString(migratedQueryInformation);
        error.addParametersItem(new Parameter().key("migratedQueryInformation").value(migratedQueryInfoJson));
      } catch (JsonProcessingException e) {
        log.error("Failed to serialize migrated query information", e);
        // If serialization fails, fall back to MigratableQueryInformation.toString(); the same information should be
        // present, but it won't be as easily parsed as the JSON version.
        error.addParametersItem(new Parameter().key("serializationError").value("Failed to serialize migrated query information"));
        error.addParametersItem(new Parameter().key("migratedQueryInformationText").value(migratedQueryInformation.toString()));
      }
    }

    return error;
  }
}
