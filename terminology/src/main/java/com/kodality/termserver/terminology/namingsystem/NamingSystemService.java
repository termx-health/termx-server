package com.kodality.termserver.terminology.namingsystem;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.auth.UserPermissionService;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.namingsystem.NamingSystem;
import com.kodality.termserver.ts.namingsystem.NamingSystemQueryParams;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class NamingSystemService {
  private final NamingSystemRepository repository;
  private final UserPermissionService userPermissionService;

  public QueryResult<NamingSystem> query(NamingSystemQueryParams params) {
    return repository.query(params);
  }

  public Optional<NamingSystem> load(String id) {
    return Optional.ofNullable(repository.load(id));
  }

  @Transactional
  public void save(NamingSystem namingSystem) {
    userPermissionService.checkPermitted(namingSystem.getId(), "NamingSystem", "edit");

    namingSystem.setCreated(namingSystem.getCreated() == null ? OffsetDateTime.now() : namingSystem.getCreated());
    repository.save(namingSystem);
  }

  @Transactional
  public void retire(String id) {
    NamingSystem namingSystem = repository.load(id);
    if (namingSystem == null) {
      throw ApiError.TE501.toApiException(Map.of("namingSystem", id));
    }
    userPermissionService.checkPermitted(namingSystem.getId(), "NamingSystem", "publish");
    if (PublicationStatus.retired.equals(namingSystem.getStatus())) {
      log.warn("NamingSystem '{}' is already retired, skipping retirement process.", id);
      return;

    }
    repository.retire(id);
  }

  @Transactional
  public void activate(String id) {
    NamingSystem namingSystem = repository.load(id);
    if (namingSystem == null) {
      throw ApiError.TE501.toApiException(Map.of("namingSystem", id));
    }
    userPermissionService.checkPermitted(namingSystem.getId(), "NamingSystem", "publish");
    if (PublicationStatus.active.equals(namingSystem.getStatus())) {
      log.warn("NamingSystem '{}' is already activated, skipping activation process.", id);
      return;
    }
    repository.activate(id);
  }

  @Transactional
  public void cancel(String id) {
    userPermissionService.checkPermitted(id, "NamingSystem", "publish");
    repository.cancel(id);
  }
}
