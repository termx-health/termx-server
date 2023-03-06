package com.kodality.termserver.project.server;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.auth.auth.UserPermissionService;
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

  public QueryResult<TerminologyServer> query(TerminologyServerQueryParams params) {
    return repository.query(params);
  }

  private void validate(TerminologyServer server) {
    if (server.isCurrentInstallation()) {
      TerminologyServerQueryParams params = new TerminologyServerQueryParams();
      params.setCurrentInstallation(true);
      params.setLimit(1);
      query(params).findFirst().ifPresent(current -> {
        if (!current.getId().equals(server.getId())) {
          throw ApiError.TE901.toApiException();
        }
      });
    }
  }

}
