package com.kodality.commons.permcache;

import com.kodality.commons.cache.CacheManager;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;


public abstract class ResourcePersistedCacheHandler {
  private static final Duration TTL = Duration.ofSeconds(86400);
  private final CacheManager cache = new CacheManager();
  private final String resourceType;
  private final ResourceRepository repository;

  public ResourcePersistedCacheHandler(String resourceType, ResourceRepository repository) {
    this.resourceType = resourceType;
    this.repository = repository;
    cache.initCache("external", 3000, 600);
    cache.initCache("internal", 3000, 600);
  }

  protected abstract Object queryResource(String resourceId);

  public CacheResource load(Long id) {
    return cache.get("internal", id.toString(), () -> repository.load(id));
  }

  public Long getId(String resourceId) {
    return getResource(resourceId).getId();
  }

  public CacheResource getResource(String resourceId) {
    return getResource(resourceId, this::queryResource);
  }

  public CacheResource getResource(String resourceId, Function<String, Object> loader) {
    return cache.getCf("external", resourceId, wrapSupplier(() -> CompletableFuture.supplyAsync(wrapSupplier(() -> {
          CacheResource stored = repository.find(resourceId, resourceType);
          if (stored != null && Duration.between(stored.getLastRefreshed(), OffsetDateTime.now()).compareTo(TTL) < 0) {
            return stored;
          }
          Object content = loader.apply(resourceId);
          return repository.save(resourceId, resourceType, content);
        })))
    ).join();
  }

  private static <T> Supplier<T> wrapSupplier(Supplier<T> supplier) {
    HttpRequest<Object> req = ServerRequestContext.currentRequest().orElse(null);
    if (req == null) {
      return supplier;
    }
    return () -> ServerRequestContext.with(req, supplier);
  }

  public String getIds(String resourceIds) {
    if (StringUtils.isEmpty(resourceIds)) {
      return resourceIds;
    }
    return Arrays.stream(resourceIds.split(","))
        .map(this::getId).filter(Objects::nonNull).map(cr -> cr.toString())
        .collect(Collectors.joining(","));
  }
}
