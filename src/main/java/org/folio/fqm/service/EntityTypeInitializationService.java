package org.folio.fqm.service;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.EntityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class EntityTypeInitializationService {

  private final EntityTypeRepository entityTypeRepository;

  private final ObjectMapper objectMapper;
  private final ResourcePatternResolver resourceResolver;

  @Autowired
  public EntityTypeInitializationService(
    EntityTypeRepository entityTypeRepository,
    ResourcePatternResolver resourceResolver
  ) {
    this.entityTypeRepository = entityTypeRepository;
    this.resourceResolver = resourceResolver;

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
        .build();
  }

  // called as part of tenant install/upgrade (see FqmTenantService)
  public void initializeEntityTypes() throws IOException {
    log.info("Initializing entity types");

    List<EntityType> desiredEntityTypes = Stream
      .concat(
        Arrays.stream(resourceResolver.getResources("classpath*:/entity-types/**/*.json")),
        Arrays.stream(resourceResolver.getResources("classpath*:/entity-types/**/*.json5"))
      )
      .filter(Resource::isReadable)
      .map(resource -> {
        try {
          return objectMapper.readValue(resource.getInputStream(), EntityType.class);
        } catch (IOException e) {
          log.error("Unable to read entity type from resource: {}", resource.getDescription(), e);
          throw new UncheckedIOException(e);
        }
      })
      .collect(Collectors.toList());

    // lambdas ensure we don't do the stream/map/etc. unless logging is enabled
    log.info(
      "Found {} entity types in package: {}",
      () -> desiredEntityTypes.size(),
      () ->
        desiredEntityTypes.stream().map(et -> "%s(%s)".formatted(et.getName(), et.getId())).collect(Collectors.toList())
    );

    entityTypeRepository.replaceEntityTypeDefinitions(desiredEntityTypes);
  }
}
