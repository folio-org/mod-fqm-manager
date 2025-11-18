package org.folio.fqm.service;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import liquibase.exception.LiquibaseException;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

  private final DSLContext readerJooqContext;
  private final FolioSpringLiquibase folioSpringLiquibase;

  @Autowired
  public EntityTypeInitializationService(
    EntityTypeRepository entityTypeRepository,
    FolioExecutionContext folioExecutionContext,
    ResourcePatternResolver resourceResolver,
    CrossTenantQueryService crossTenantQueryService,
    EntityTypeService entityTypeService,
    @Qualifier("readerJooqContext") DSLContext readerJooqContext,
    FolioSpringLiquibase folioSpringLiquibase
  ) {
    this.entityTypeRepository = entityTypeRepository;
    this.folioExecutionContext = folioExecutionContext;
    this.resourceResolver = resourceResolver;
    this.crossTenantQueryService = crossTenantQueryService;
    this.entityTypeService = entityTypeService;
    this.readerJooqContext = readerJooqContext;
    this.folioSpringLiquibase = folioSpringLiquibase;

    // this enables all JSON5 features, except for numeric ones (hex, starting/trailing
    // decimal points, use of NaN, etc), as those are not relevant for our use
    // see: https://stackoverflow.com/questions/68312227/can-the-jackson-parser-be-used-to-parse-json5
    // full list:
    // https://fasterxml.github.io/jackson-core/javadoc/2.14/com/fasterxml/jackson/core/json/JsonReadFeature.html
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
  public void initializeEntityTypes(String centralTenantId) throws IOException {
    log.info("Initializing entity types");
    if (centralTenantId == null) {
      centralTenantId = crossTenantQueryService.getCentralTenantId();
    }
    // Central tenant ID, or current tenant ID if ECS is not enabled - Use this in cases where we
    // want things to still work, even in non-ECS environments
    final String safeCentralTenantId;
    if (centralTenantId != null) {
      log.info("ECS central tenant ID: {}", centralTenantId);
      safeCentralTenantId = centralTenantId;
    } else {
      log.info("ECS is not enabled for tenant {}", folioExecutionContext.getTenantId());
      centralTenantId = "${central_tenant_id}";
      safeCentralTenantId = folioExecutionContext.getTenantId();
    }
    String finalCentralTenantId = centralTenantId; // Make centralTenantId effectively final, for the lambda below

    List<EntityType> availableEntityTypes = this.getAvailableEntityTypes(finalCentralTenantId, safeCentralTenantId);

    validateEntityTypesAndFillUsedBy(availableEntityTypes);

    log.info(
      "Found {} available entity types in package: {}",
      () -> availableEntityTypes.size(),
      () -> availableEntityTypes.stream().map(et -> "%s(%s)".formatted(et.getName(), et.getId())).toList()
    );

    entityTypeRepository.replaceEntityTypeDefinitions(availableEntityTypes);
  }

  protected List<EntityType> getAvailableEntityTypes(String finalCentralTenantId, String safeCentralTenantId)
    throws IOException {
    Map<UUID, EntityType> allEntityTypes = Stream
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
              .replace("${central_tenant_id}", finalCentralTenantId)
              .replace("${safe_central_tenant_id}", safeCentralTenantId),
            EntityType.class
          );
        } catch (IOException e) {
          log.error("Unable to read entity type from resource: {}", resource.getDescription(), e);
          throw new UncheckedIOException(e);
        }
      })
      .collect(Collectors.toMap(et -> UUID.fromString(et.getId()), Function.identity()));

    // stores if an entity type/view is available or not, for caching purposes
    Map<UUID, Boolean> entityTypeAvailabilityCache = HashMap.newHashMap(allEntityTypes.size());
    Map<String, Boolean> sourceViewAvailabilityCache = prefillSourceViewAvailabilityCache();
    // TODO [MODFQMMGR-997]: replace AtomicBoolean and global liquibase run with a precise solution that only attempts with one view
    AtomicBoolean hasAttemptedLiquibaseUpdate = new AtomicBoolean(false);

    // populates entityTypeAvailabilityCache
    allEntityTypes
      .keySet()
      .forEach(entityTypeId ->
        checkEntityTypeIsAvailableWithCache(
          entityTypeId,
          allEntityTypes,
          entityTypeAvailabilityCache,
          sourceViewAvailabilityCache,
          hasAttemptedLiquibaseUpdate
        )
      );

    return allEntityTypes
      .values()
      .stream()
      .filter(et -> Boolean.TRUE.equals(entityTypeAvailabilityCache.get(UUID.fromString(et.getId()))))
      .toList();
  }

  protected Map<String, Boolean> prefillSourceViewAvailabilityCache() {
    // prefill with existing views, to avoid repeated DB queries
    List<String> existingViews = readerJooqContext
      .select(field("table_name", String.class))
      .from(table(name("information_schema", "tables")))
      .where(field("table_schema").eq("%s_mod_fqm_manager".formatted(folioExecutionContext.getTenantId())))
      .fetch(field("table_name", String.class));

    Map<String, Boolean> sourceViewAvailabilityCache = HashMap.newHashMap(existingViews.size());

    for (String view : existingViews) {
      sourceViewAvailabilityCache.put(view, true);
    }

    return sourceViewAvailabilityCache;
  }

  protected boolean checkEntityTypeIsAvailableWithCache(
    UUID entityTypeId,
    Map<UUID, EntityType> allEntityTypes,
    Map<UUID, Boolean> entityTypeAvailabilityCache,
    Map<String, Boolean> sourceViewAvailabilityCache,
    AtomicBoolean hasAttemptedLiquibaseUpdate
  ) {
    if (entityTypeAvailabilityCache.containsKey(entityTypeId)) {
      return entityTypeAvailabilityCache.get(entityTypeId);
    }

    EntityType entityType = allEntityTypes.get(entityTypeId);
    if (entityType == null) {
      log.warn("Source entity type with ID {} not found among available entity types", entityTypeId);
      entityTypeAvailabilityCache.put(entityTypeId, false);
      return false;
    }

    if (
      CollectionUtils
        .emptyIfNull(entityType.getSources())
        .stream()
        .allMatch(source ->
          switch (source) {
            case EntityTypeSourceEntityType sourceEt -> checkEntityTypeIsAvailableWithCache(
              sourceEt.getTargetId(),
              allEntityTypes,
              entityTypeAvailabilityCache,
              sourceViewAvailabilityCache,
              hasAttemptedLiquibaseUpdate
            );
            case EntityTypeSourceDatabase sourceDb -> checkSourceViewIsAvailableWithCache(
              sourceDb.getTarget(),
              sourceViewAvailabilityCache,
              hasAttemptedLiquibaseUpdate
            );
            default -> {
              log.warn(
                "Unknown source type {} in entity type {} ({})",
                source.getClass().getName(),
                entityType.getName(),
                entityType.getId()
              );
              yield false;
            }
          }
        )
    ) {
      entityTypeAvailabilityCache.put(entityTypeId, true);
      return true;
    } else {
      log.warn(
        "Entity type {} ({}) is not available due to unavailable sources",
        entityType.getName(),
        entityType.getId()
      );
      entityTypeAvailabilityCache.put(entityTypeId, true);
      return false;
    }
  }

  protected boolean checkSourceViewIsAvailableWithCache(
    String view,
    Map<String, Boolean> sourceViewAvailabilityCache,
    AtomicBoolean hasAttemptedLiquibaseUpdate
  ) {
    return sourceViewAvailabilityCache.computeIfAbsent(
      view,
      v -> checkSourceViewIsAvailable(v, hasAttemptedLiquibaseUpdate)
    );
  }

  protected boolean checkSourceViewIsAvailable(String view, AtomicBoolean hasAttemptedLiquibaseUpdate) {
    try {
      if (view.contains("(")) {
        log.info("Source `{}` is a complex expression and cannot be checked", view);
        return true;
      }

      log.info("Checking if source view {} is available", view);

      Optional
        .ofNullable(
          readerJooqContext
            .selectOne()
            .from(table(name("information_schema", "tables")))
            .where(
              List.of(
                field("table_schema").eq("%s_mod_fqm_manager".formatted(folioExecutionContext.getTenantId())),
                field("table_name").eq(view)
              )
            )
            .fetchOne()
        )
        .orElseThrow();

      log.info("√ Source view {} is available!", view);

      return true;
    } catch (Exception e) {
      log.warn("X Source view {} is not available: {}", view, e.getMessage());
      // attempt to run liquibase update once, in case the view is just not created yet
      if (hasAttemptedLiquibaseUpdate.compareAndSet(false, true)) {
        log.info("Attempting to run liquibase update for tenant {}", folioExecutionContext.getTenantId());

        try {
          folioSpringLiquibase.performLiquibaseUpdate();

          return checkSourceViewIsAvailable(view, hasAttemptedLiquibaseUpdate);
        } catch (LiquibaseException le) {
          log.error(
            "Error during liquibase update attempt for tenant {}. Something is very wrong.",
            folioExecutionContext.getTenantId(),
            le
          );
          throw new IllegalStateException(le);
        }
      }

      return false;
    }
  }

  protected void validateEntityTypesAndFillUsedBy(List<EntityType> entityTypes) {
    List<UUID> entityTypeIds = entityTypes.stream().map(EntityType::getId).map(UUID::fromString).toList();
    Map<String, List<String>> usedByMap = entityTypeRepository
      .getEntityTypeDefinitions(entityTypeIds, folioExecutionContext.getTenantId())
      .collect(Collectors.toMap(EntityType::getId, EntityType::getUsedBy));

    for (EntityType entityType : entityTypes) {
      List<String> existingUsedBy = usedByMap.getOrDefault(entityType.getId(), Collections.emptyList());
      entityType.setUsedBy(existingUsedBy);

      log.debug("Checking entity type: {} ({})", entityType.getName(), entityType.getId());
      entityTypeService.validateEntityType(UUID.fromString(entityType.getId()), entityType, entityTypeIds);
    }
  }
}
