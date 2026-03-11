package com.kodality.commons.cache;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.ehcache.Cache;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;

import static org.ehcache.config.builders.CacheConfigurationBuilder.newCacheConfigurationBuilder;

public class CacheManager {
  private final org.ehcache.CacheManager cacheManager;

  public CacheManager() {
    cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build();
    cacheManager.init();
  }

  public void initCache(String cacheName, long maxEntries, long ttlSeconds) {
    CacheConfigurationBuilder<String, Object> builder =
        newCacheConfigurationBuilder(String.class, Object.class, ResourcePoolsBuilder.heap(maxEntries))
            .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofSeconds(ttlSeconds)));
    cacheManager.createCache(cacheName, builder.build());
  }

  public Cache<String, Object> getCache(String key) {
    return cacheManager.getCache(key, String.class, Object.class);
  }

  public void clearCache(String cacheName) {
    Cache<String, Object> cache = cacheManager.getCache(cacheName, String.class, Object.class);
    cache.clear();
  }

  @SuppressWarnings("unchecked")
  public <V> V get(String cacheName, String key, Supplier<V> computeFn) {
    Cache<String, Object> cache = cacheManager.getCache(cacheName, String.class, Object.class);
    V value = (V) cache.get(key);
    if (value != null) {
      return value;
    }
    value = computeFn.get();
    if (value != null) {
      cache.put(key, value);
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  public synchronized <V> CompletableFuture<V> getCf(String cacheName, String key, Supplier<CompletableFuture<V>> computeFn) {
    Cache<String, Object> cache = cacheManager.getCache(cacheName, String.class, Object.class);
    CompletableFuture<V> value = (CompletableFuture<V>) cache.get(key);
    if (value == null || value.isCancelled() || value.isCompletedExceptionally()) {
      value = computeFn.get();
      value.thenAccept(v -> cache.put(key, CompletableFuture.completedFuture(v)));
      cache.put(key, value);
    }
    return value.copy();
  }

  public void remove(String cacheName, String key) {
    Cache<String, Object> cache = cacheManager.getCache(cacheName, String.class, Object.class);
    cache.remove(key);
  }
}
