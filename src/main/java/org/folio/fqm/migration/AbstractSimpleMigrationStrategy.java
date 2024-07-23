package org.folio.fqm.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.fql.model.Fql;
import org.folio.fql.service.FqlService;

@Log4j2
public abstract class AbstractSimpleMigrationStrategy implements MigrationStrategy {

  /** The version migrating FROM */
  public abstract String getSourceVersion();

  /** The version migrating TO */
  public abstract String getTargetVersion();

  /** The entity types that got a new UUID */
  protected abstract Map<UUID, UUID> getEntityTypeChanges();

  /**
   * The fields that were renamed. Keys should use their OLD entity type ID, if applicable.
   *
   * The special key "*" on the field map can be used to apply a template to all fields. This value
   * should be a string with a %s placeholder, which will be filled in with the original field name.
   */
  protected abstract Map<UUID, Map<String, String>> getFieldChanges();

  @Override
  public boolean applies(FqlService fqlService, MigratableQueryInformation src) {
    String srcVersion = "0";
    if (src.fqlQuery() != null) {
      Fql query = fqlService.getFql(src.fqlQuery());
      if (query._version() != null) {
        srcVersion = query._version();
      }
    }

    return this.getSourceVersion().equals(srcVersion);
  }

  @Override
  public MigratableQueryInformation apply(FqlService fqlService, MigratableQueryInformation src) {
    try {
      MigratableQueryInformation result = src;

      if (src.fqlQuery() == null) {
        result =
          result.withFqlQuery(new ObjectMapper().writeValueAsString(Map.of("_version", this.getTargetVersion())));
      }

      if (this.getFieldChanges().containsKey(src.entityTypeId())) {
        Map<String, String> fieldChanges = this.getFieldChanges().get(src.entityTypeId());

        if (fieldChanges.containsKey("*")) {
          String template = fieldChanges.get("*");
          result = result.withFields(src.fields().stream().map(template::formatted).toList());
        } else {
          result = result.withFields(src.fields().stream().map(f -> fieldChanges.getOrDefault(f, f)).toList());
        }
      }

      return result;
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize FQL query", e);
      throw new UncheckedIOException(e);
    }
  }
}
