package com.kodality.termx.core.sys.server;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.ApiError;
import com.kodality.termx.core.sys.server.httpclient.ServerHttpClientProvider;
import com.kodality.termx.sys.server.TerminologyServer;
import com.kodality.termx.sys.server.TerminologyServerQueryParams;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class TerminologyServerService {
  private final TerminologyServerRepository repository;
  private final List<ServerHttpClientProvider> httpClientServices;

  public List<String> getKinds() {
    return httpClientServices.stream().map(ServerHttpClientProvider::getKind).distinct().toList();
  }

  @Transactional
  public TerminologyServer save(TerminologyServer server) {
    prepare(server);
    validate(server);

    repository.save(server);
    httpClientServices.forEach(hc -> hc.afterServerSave(server.getId()));
    return server;
  }

  public TerminologyServer load(Long id) {
    return repository.load(id);
  }

  public TerminologyServer load(String code) {
    return repository.load(code);
  }

  public TerminologyServer loadCurrentInstallation() {
    return repository.loadCurrentInstallation();
  }

  public QueryResult<TerminologyServer> query(TerminologyServerQueryParams params) {
    return repository.query(params);
  }

  private void prepare(TerminologyServer server) {
    TerminologyServer persisted = load(server.getId());

    if (persisted != null) {
      if (persisted.getAuthConfig() != null && server.getAuthConfig() != null && server.getAuthConfig().getClientSecret() == null) {
        server.getAuthConfig().setClientSecret(persisted.getAuthConfig().getClientSecret());
      }
      if (persisted.getHeaders() != null && server.getHeaders() != null) {
        var unmodifiedAuthHeader = server.getHeaders().stream().filter(h -> h.getKey().equals("Authorization") && h.getValue() == null).findFirst();
        var persistedAuthHeader = persisted.getHeaders().stream().filter(h -> h.getKey().equals("Authorization")).findFirst();

        if (unmodifiedAuthHeader.isPresent() && persistedAuthHeader.isPresent()) {
          unmodifiedAuthHeader.get().setValue(persistedAuthHeader.get().getValue());
        }
      }
    }
  }

  private void validate(TerminologyServer server) {
    if (server.isCurrentInstallation()) {
      TerminologyServer currentInstallation = loadCurrentInstallation();
      if (currentInstallation != null && !currentInstallation.getId().equals(server.getId())) {
        throw ApiError.TC101.toApiException();
      }
    }
    if (server.getHeaders() != null) {
      if (server.getHeaders().stream().filter(h -> h.getKey().equals("Authorization")).count() > 1) {
        throw ApiError.TC109.toApiException(Map.of("name", "Authorization"));
      }
    }
  }
}
