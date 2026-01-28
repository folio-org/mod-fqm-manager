package org.folio.fqm.repository;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.exception.UncheckedException;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.exception.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository
@Log4j2
public class CustomEntityTypeMigrationMappingRepository {

  private static final String MAPPING_TABLE = "custom_entity_type_migration_mapping";

  private final DSLContext jooqContext;
  private final DSLContext readerJooqContext;
  private final ObjectMapper objectMapper;

  @Autowired
  public CustomEntityTypeMigrationMappingRepository(
    DSLContext jooqContext,
    @Qualifier("readerJooqContext") DSLContext readerJooqContext,
    ObjectMapper objectMapper
  ) {
    this.jooqContext = jooqContext;
    this.readerJooqContext = readerJooqContext;
    this.objectMapper = objectMapper;
  }

  private void initializeTableIfNecessary() {
    if (jooqContext.fetchCount(table(MAPPING_TABLE)) == 0) {
      jooqContext.insertInto(table(MAPPING_TABLE)).set(field("mapping"), JSONB.jsonb("{}")).execute();
    }
  }

  public Map<UUID, Map<String, UUID>> getMappings() {
    initializeTableIfNecessary();
    try {
      return objectMapper.readValue(
        readerJooqContext
          .select(field("mapping"))
          .from(table(MAPPING_TABLE))
          .fetchOne()
          .get(field("mapping"), String.class),
        new TypeReference<>() {}
      );
    } catch (JsonProcessingException | IllegalArgumentException | DataAccessException e) {
      log.error("Error retrieving custom entity type migration mappings", e);
      return Map.of();
    }
  }

  public void saveMappings(Map<UUID, Map<String, UUID>> mappings) {
    initializeTableIfNecessary();
    try {
      jooqContext
        .update(table(MAPPING_TABLE))
        .set(field("mapping"), JSONB.jsonb(objectMapper.writeValueAsString(mappings)))
        .execute();
    } catch (JsonProcessingException | DataAccessException e) {
      throw log.throwing(new UncheckedException(e));
    }
  }
}
