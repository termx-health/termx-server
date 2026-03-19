package org.termx.auth.externalclient;

import com.kodality.commons.cache.CacheManager;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ExternalClientService {
  private final ExternalClientRepository repository;

  private final CacheManager clientCache = new CacheManager();

  {
    clientCache.initCache("clients", 1, 600);
  }

  public ExternalClient find(String credential) {
    return loadAll().get(credential);
  }

  private Map<String, ExternalClient> loadAll() {
    return clientCache.get("clients", "_", () -> {
      List<ExternalClient> clients = repository.loadActive();
      return clients.stream().collect(Collectors.toMap(ExternalClient::getCredential, c -> c));
    });
  }
}
