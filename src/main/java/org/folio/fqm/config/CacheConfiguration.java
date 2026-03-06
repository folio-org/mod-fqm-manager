package org.folio.fqm.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfiguration {

  /**
   * Configures the Caffeine caches used by Spring Cache.
   *
   * <p>The `coffee-boots.cache.spec.*` keys are legacy names retained for backward compatibility,
   * even though this module no longer uses coffee-boots. The preferred keys are
   * `mod-fqm-manager.cache.spec.*`; legacy keys remain supported for at least one major version
   * upgrade so existing deployments keep working.
   */
  @Bean
  public CacheManager cacheManager(
    @Value("${mod-fqm-manager.cache.spec.queryCache:${coffee-boots.cache.spec.queryCache:maximumSize=500,expireAfterWrite=1m}}")
    String queryCacheSpec,
    @Value("${mod-fqm-manager.cache.spec.userTenantCache:${coffee-boots.cache.spec.userTenantCache:maximumSize=100,expireAfterWrite=30s}}")
    String userTenantCacheSpec
  ) {
    var cacheManager = new CaffeineCacheManager();
    cacheManager.setAllowNullValues(true);
    cacheManager.registerCustomCache("queryCache", Caffeine.from(queryCacheSpec).build());
    cacheManager.registerCustomCache("userTenantCache", Caffeine.from(userTenantCacheSpec).build());
    return cacheManager;
  }
}
