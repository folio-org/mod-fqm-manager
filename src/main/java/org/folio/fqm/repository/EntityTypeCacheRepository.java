package org.folio.fqm.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.folio.querytool.domain.dto.EntityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * Repository for caches of entity types. Provides a basic proxy of raw and flattened
 * entity type caches, enabling other services to clear caches easier.
 */
@Repository
public class EntityTypeCacheRepository {

  private final Cache<String, Map<UUID, EntityType>> rawEntityTypeCache;
  private final Cache<FlattenedEntityTypeCacheKey, EntityType> flattenedEntityTypeCache;

  @Autowired
  public EntityTypeCacheRepository(
    @Value("${mod-fqm-manager.entity-type-cache-timeout-seconds:300}") long cacheDurationSeconds
  ) {
    this.rawEntityTypeCache = Caffeine.newBuilder().expireAfterWrite(cacheDurationSeconds, TimeUnit.SECONDS).build();
    this.flattenedEntityTypeCache =
      Caffeine.newBuilder().expireAfterWrite(cacheDurationSeconds, TimeUnit.SECONDS).build();
  }

  public Map<UUID, EntityType> getRaw(String tenantId, Function<String, Map<UUID, EntityType>> mappingFunction) {
    return rawEntityTypeCache.get(tenantId, mappingFunction);
  }

  public EntityType getFlattened(
    FlattenedEntityTypeCacheKey key,
    Function<FlattenedEntityTypeCacheKey, EntityType> mappingFunction
  ) {
    return flattenedEntityTypeCache.get(key, mappingFunction);
  }

  public void invalidateTenant(String tenantId) {
    if (tenantId == null) {
      return;
    }
    rawEntityTypeCache.invalidate(tenantId);
    flattenedEntityTypeCache.asMap().keySet().removeIf(key -> key.tenantId().equals(tenantId));
  }

  public void invalidateEntityType(String tenantId, String entityTypeId) {
    if (tenantId == null) {
      return;
    }
    rawEntityTypeCache.invalidate(tenantId);
    flattenedEntityTypeCache.asMap().keySet().removeIf(key ->
      key.tenantId().equals(tenantId) && key.entityTypeId().toString().equals(entityTypeId)
    );
  }

  public void invalidateAll() {
    rawEntityTypeCache.invalidateAll();
  }

  // translations are included as part of flattening, so we must cache based on locales, too
  public record FlattenedEntityTypeCacheKey(
    String tenantId,
    UUID entityTypeId,
    List<Locale> locales,
    boolean preserveAllColumns
  ) {
    public FlattenedEntityTypeCacheKey {
      if (tenantId == null) {
        throw new IllegalArgumentException("tenantId cannot be null");
      }
    }
  }
}
