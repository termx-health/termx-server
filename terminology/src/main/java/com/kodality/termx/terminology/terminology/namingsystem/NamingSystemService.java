package com.kodality.termx.terminology.terminology.namingsystem;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.namingsystem.NamingSystem;
import com.kodality.termx.ts.namingsystem.NamingSystemQueryParams;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class NamingSystemService {
  private final NamingSystemRepository repository;

  public QueryResult<NamingSystem> query(NamingSystemQueryParams params) {
    return repository.query(params);
  }

  public Optional<NamingSystem> load(String id) {
    return Optional.ofNullable(repository.load(id));
  }

  @Transactional
  public void save(NamingSystem namingSystem) {
    namingSystem.setCreated(namingSystem.getCreated() == null ? OffsetDateTime.now() : namingSystem.getCreated());
    repository.save(namingSystem);
  }

  @Transactional
  public void retire(String id) {
    NamingSystem namingSystem = repository.load(id);
    if (namingSystem == null) {
      throw ApiError.TE501.toApiException(Map.of("namingSystem", id));
    }
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
    if (PublicationStatus.active.equals(namingSystem.getStatus())) {
      log.warn("NamingSystem '{}' is already activated, skipping activation process.", id);
      return;
    }
    repository.activate(id);
  }

  @Transactional
  public void cancel(String id) {
    repository.cancel(id);
  }
}
