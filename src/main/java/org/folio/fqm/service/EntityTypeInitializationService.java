package org.folio.fqm.service;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import lombok.extern.log4j.Log4j2;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class EntityTypeInitializationService {

  private final EntityTypeRepository entityTypeRepository;
  private final EntityTypeService entityTypeService;

  private final FolioExecutionContext folioExecutionContext;

  private final ObjectMapper objectMapper;
  private final ResourcePatternResolver resourceResolver;
  private final CrossTenantQueryService crossTenantQueryService;

  @Autowired
  public EntityTypeInitializationService(
    EntityTypeRepository entityTypeRepository,
    FolioExecutionContext folioExecutionContext,
    ResourcePatternResolver resourceResolver,
    CrossTenantQueryService crossTenantQueryService,
    EntityTypeService entityTypeService) {
    this.entityTypeRepository = entityTypeRepository;
    this.folioExecutionContext = folioExecutionContext;
    this.resourceResolver = resourceResolver;
    this.crossTenantQueryService = crossTenantQueryService;
    this.entityTypeService = entityTypeService;

    // this enables all JSON5 features, except for numeric ones (hex, starting/trailing
    // decimal points, use of NaN, etc), as those are not relevant for our use
    // see: https://stackoverflow.com/questions/68312227/can-the-jackson-parser-be-used-to-parse-json5
    // full list: https://fasterxml.github.io/jackson-core/javadoc/2.14/com/fasterxml/jackson/core/json/JsonReadFeature.html
    this.objectMapper =
      JsonMapper
        .builder()
        // allows use of Java/C++ style comments (both '/'+'*' and '//' varieties) within parsed content.
        .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
        // some SQL statements may be cleaner this way around
        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
        // left side of { foo: bar }, cleaner/easier to read. JS style
        .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
        // nicer diffs/etc
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        // allows "escaping" newlines, giving proper linebreaks
        .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
              .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
        .build();
  }

  // called as part of tenant install/upgrade (see FqmTenantService)
  public void initializeEntityTypes(String centralTenantId) throws IOException {
    log.info("Initializing entity types");
    if (centralTenantId == null) {
      centralTenantId = crossTenantQueryService.getCentralTenantId();
    }
    if (centralTenantId != null) {
      log.info("ECS central tenant ID: {}", centralTenantId);
    }
    else {
      log.info("ECS is not enabled for tenant {}", folioExecutionContext.getTenantId());
      centralTenantId = "${central_tenant_id}";
    }
    String finalCentralTenantId = centralTenantId; // Make centralTenantId effectively final, for the lambda below
    List<EntityType> desiredEntityTypes = Stream
      .concat(
        Arrays.stream(resourceResolver.getResources("classpath:/entity-types/**/*.json")),
        Arrays.stream(resourceResolver.getResources("classpath:/entity-types/**/*.json5"))
      )
      .filter(Resource::isReadable)
      .map(resource -> {
        try {
          return objectMapper.readValue(
            resource
              .getContentAsString(StandardCharsets.UTF_8)
              .replace("${tenant_id}", folioExecutionContext.getTenantId())
              .replace("${central_tenant_id}", finalCentralTenantId),
            EntityType.class
          );
        } catch (IOException e) {
          log.error("Unable to read entity type from resource: {}", resource.getDescription(), e);
          throw new UncheckedIOException(e);
        }
      })
      .toList();

    List<String> entityTypeIds = desiredEntityTypes
      .stream()
      .map(EntityType::getId)
      .toList();

    for (EntityType entityType : desiredEntityTypes) {
      log.debug("Checking entity type: {} ({})", entityType.getName(), entityType.getId());
      entityTypeService.validateEntityType(
        UUID.fromString(entityType.getId()),
        entityType,
        entityTypeIds
      );
    }

    // lambdas ensure we don't do the stream/map/etc. unless logging is enabled
    log.info(
      "Found {} entity types in package: {}",
      () -> desiredEntityTypes.size(),
      () -> desiredEntityTypes.stream().map(et -> "%s(%s)".formatted(et.getName(), et.getId())).toList()
    );

    entityTypeRepository.replaceEntityTypeDefinitions(desiredEntityTypes);
  }
}
