package com.kodality.termserver.terminology.project.server;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.auth.UserPermissionService;
import com.kodality.termserver.ts.project.server.TerminologyServer;
import com.kodality.termserver.ts.project.server.TerminologyServerQueryParams;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class TerminologyServerService {
  private final TerminologyServerRepository repository;
  private final UserPermissionService userPermissionService;

  @Transactional
  public TerminologyServer save(TerminologyServer server) {
    userPermissionService.checkPermitted(server.getCode(), "TerminologyServer", "edit");
    validate(server);
    repository.save(server);
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
        throw ApiError.TE901.toApiException();
      }
    }
  }

}
