package org.folio.fqm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.fqm.exception.FqmException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.utils.JSON5ObjectMapperFactory;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.EntityTypeSourceEntityType;
import org.folio.spring.FolioExecutionContext;
import org.jooq.exception.DataAccessException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class EntityTypeInitializationService {

  private final CrossTenantQueryService crossTenantQueryService;
  private final EntityTypeRepository entityTypeRepository;
  private final EntityTypeValidationService entityTypeValidationService;
  private final MigrationService migrationService;
  private final SourceViewService sourceViewService;

  private final FolioExecutionContext folioExecutionContext;

  private final ObjectMapper objectMapper;
  private final ResourcePatternResolver resourceResolver;

  @Autowired
  public EntityTypeInitializationService(
    CrossTenantQueryService crossTenantQueryService,
    EntityTypeRepository entityTypeRepository,
    EntityTypeValidationService entityTypeValidationService,
    MigrationService migrationService,
    SourceViewService sourceViewService,
    FolioExecutionContext folioExecutionContext,
    ResourcePatternResolver resourceResolver
  ) {
    this.crossTenantQueryService = crossTenantQueryService;
    this.entityTypeRepository = entityTypeRepository;
    this.entityTypeValidationService = entityTypeValidationService;
    this.migrationService = migrationService;
    this.sourceViewService = sourceViewService;
    this.folioExecutionContext = folioExecutionContext;
    this.resourceResolver = resourceResolver;

    this.objectMapper = JSON5ObjectMapperFactory.create();
  }

  // called as part of tenant install/upgrade (see FqmTenantService) or on POST /entity-types/install
  public void initializeEntityTypes(String providedCentralTenantId) throws IOException {
    log.info("Initializing entity types");

    Pair<String, String> centralTenantInfo = this.getCentralTenantIdSafely(providedCentralTenantId);
    String safeCentralTenantId = centralTenantInfo.getLeft();
    String centralTenantId = centralTenantInfo.getRight();

    List<EntityType> availableEntityTypes = this.getAvailableEntityTypes(centralTenantId, safeCentralTenantId);

    validateEntityTypesAndFillUsedBy(availableEntityTypes);

    log.info(
      "Found {} available entity types in package: {}",
      () -> availableEntityTypes.size(),
      () -> availableEntityTypes.stream().map(et -> "%s(%s)".formatted(et.getName(), et.getId())).toList()
    );

    entityTypeRepository.replaceEntityTypeDefinitions(availableEntityTypes);
    migrationService.migrateCustomEntityTypes();
  }

  protected Pair<String, String> getCentralTenantIdSafely(String centralTenantId) {
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

    return Pair.of(safeCentralTenantId, centralTenantId);
  }

  protected List<EntityType> getAvailableEntityTypes(String centralTenantId, String safeCentralTenantId)
    throws IOException {
    sourceViewService.verifyAll(safeCentralTenantId);

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
              .replace("${central_tenant_id}", centralTenantId)
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

    // populates entityTypeAvailabilityCache
    allEntityTypes
      .keySet()
      .forEach(entityTypeId ->
        checkEntityTypeIsAvailableWithCache(
          entityTypeId,
          allEntityTypes,
          entityTypeAvailabilityCache,
          sourceViewAvailabilityCache
        )
      );

    return allEntityTypes
      .values()
      .stream()
      .filter(et -> Boolean.TRUE.equals(entityTypeAvailabilityCache.get(UUID.fromString(et.getId()))))
      .toList();
  }

  protected Map<String, Boolean> prefillSourceViewAvailabilityCache() {
    Set<String> existingViews = sourceViewService.getInstalledSourceViews();

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
    Map<String, Boolean> sourceViewAvailabilityCache
  ) {
    if (entityTypeAvailabilityCache.containsKey(entityTypeId)) {
      return entityTypeAvailabilityCache.get(entityTypeId);
    }

    EntityType entityType = allEntityTypes.get(entityTypeId);
    if (entityType == null) {
      log.warn("X Source entity type with ID {} not found among available entity types", entityTypeId);
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
              sourceViewAvailabilityCache
            );
            case EntityTypeSourceDatabase sourceDb -> checkSourceViewIsAvailable(
              sourceDb.getTarget(),
              sourceViewAvailabilityCache
            );
            default -> {
              log.warn(
                "X Unknown source type {} in entity type {} ({})",
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
        "X Entity type {} ({}) is not available due to unavailable sources",
        entityType.getName(),
        entityType.getId()
      );
      entityTypeAvailabilityCache.put(entityTypeId, false);
      return false;
    }
  }

  protected boolean checkSourceViewIsAvailable(String view, Map<String, Boolean> sourceViewAvailabilityCache) {
    return sourceViewAvailabilityCache.computeIfAbsent(view, sourceViewService::doesSourceViewExist);
  }

  protected void validateEntityTypesAndFillUsedBy(List<EntityType> entityTypes) {
    List<UUID> entityTypeIds = entityTypes.stream().map(EntityType::getId).map(UUID::fromString).toList();
    Map<String, List<String>> usedByMap = entityTypeRepository
      .getEntityTypeDefinitions(entityTypeIds, folioExecutionContext.getTenantId())
      .collect(Collectors.toMap(EntityType::getId, EntityType::getUsedBy));

    for (EntityType entityType : entityTypes) {
      List<String> existingUsedBy = usedByMap.getOrDefault(entityType.getId(), Collections.emptyList());
      entityType.setUsedBy(existingUsedBy);

      try {
        log.debug("Checking entity type: {} ({})", entityType.getName(), entityType.getId());
        entityTypeValidationService.validateEntityType(UUID.fromString(entityType.getId()), entityType, entityTypeIds);
      } catch (FqmException e) {
        log.error("Entity type {} ({}) is invalid: {}", entityType.getName(), entityType.getId(), e.getMessage());
        throw log.throwing(e);
      }
    }
  }

  /**
   * Attempt to recreate the source views for an entity type. If this is not possible, the entity type
   * should no longer exist in the DB after this method has finished executing.
   */
  public void attemptToHealEntityType(EntityType flattenedEntityType) {
    log.warn(
      "Attempting to heal entity type {} ({}) by repairing all of its database sources",
      flattenedEntityType.getName(),
      flattenedEntityType.getId()
    );

    String safeCentralTenantId = getCentralTenantIdSafely(null).getLeft();

    flattenedEntityType
      .getSources()
      .stream()
      .filter(EntityTypeSourceDatabase.class::isInstance)
      .map(EntityTypeSourceDatabase.class::cast)
      .map(EntityTypeSourceDatabase::getTarget)
      .forEach(viewName -> sourceViewService.attemptToHealSourceView(viewName, safeCentralTenantId));

    try {
      // will cleanup unavailable ones if the source view was not able to be created.
      // this ensures other affected ones will also be removed (e.g. if simple_user_details no longer works,
      // all entity types depending on it should also be removed)
      this.initializeEntityTypes(null);
    } catch (IOException e) {
      log.info("Could not re-install entity types after healing entity type:", e);
    }
  }

  public <T> T runWithRecovery(EntityType flattenedEntityType, Supplier<T> runnable) {
    try {
      return runnable.get();
    } catch (DataAccessException dae) {
      PSQLException pgException = dae.getCause(PSQLException.class);
      if (pgException != null && PSQLState.UNDEFINED_TABLE.getState().equals(pgException.getSQLState())) {
        log.error(
          "Unable to run query on {} due to an UNDEFINED_TABLE error. Attempting to heal and re-run...",
          flattenedEntityType.getName()
        );
        this.attemptToHealEntityType(flattenedEntityType);
        return runnable.get();
      } else {
        throw log.throwing(dae);
      }
    }
  }
}
