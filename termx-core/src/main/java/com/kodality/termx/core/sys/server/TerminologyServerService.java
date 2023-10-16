package com.kodality.termx.core.sys.server;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.ApiError;
import com.kodality.termx.core.sys.server.httpclient.ServerHttpClientProvider;
import com.kodality.termx.sys.server.TerminologyServer;
import com.kodality.termx.sys.server.TerminologyServerQueryParams;
import java.util.List;
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

  private void validate(TerminologyServer server) {
    if (server.isCurrentInstallation()) {
      TerminologyServer currentInstallation = loadCurrentInstallation();
      if (currentInstallation != null && !currentInstallation.getId().equals(server.getId())) {
        throw ApiError.TC101.toApiException();
      }
    }
  }
}
