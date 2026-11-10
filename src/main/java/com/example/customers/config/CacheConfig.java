package com.example.customers.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/** Redis is an optimisation, not a dependency: if it fails, calls go straight to the score provider. */
@Configuration
@EnableCaching(order = 0)   // outermost advisor: a cache hit skips retry and circuit breaker
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override public void handleCacheGetError(RuntimeException e, Cache c, Object k) { log.warn("Cache GET failed: {}", e.getMessage()); }
            @Override public void handleCachePutError(RuntimeException e, Cache c, Object k, Object v) { log.warn("Cache PUT failed: {}", e.getMessage()); }
            @Override public void handleCacheEvictError(RuntimeException e, Cache c, Object k) { log.warn("Cache EVICT failed: {}", e.getMessage()); }
            @Override public void handleCacheClearError(RuntimeException e, Cache c) { log.warn("Cache CLEAR failed: {}", e.getMessage()); }
        };
    }
}
