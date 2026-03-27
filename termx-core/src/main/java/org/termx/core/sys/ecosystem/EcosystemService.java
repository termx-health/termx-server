package org.termx.core.sys.ecosystem;

import com.kodality.commons.model.QueryResult;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.termx.core.ApiError;
import org.termx.sys.ecosystem.Ecosystem;
import org.termx.sys.ecosystem.EcosystemQueryParams;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class EcosystemService {
  private final EcosystemRepository repository;

  @Transactional
  public Ecosystem save(Ecosystem ecosystem) {
    validate(ecosystem);
    prepare(ecosystem);
    repository.save(ecosystem);
    repository.saveServers(ecosystem.getId(), ecosystem.getServerIds());
    return ecosystem;
  }

  private void prepare(Ecosystem ecosystem) {
    if (ecosystem.getServerIds() == null) {
      ecosystem.setServerIds(new ArrayList<>());
    }
    if (ecosystem.getFormatVersion() == null) {
      ecosystem.setFormatVersion("1");
    }
  }

  private void validate(Ecosystem ecosystem) {
    Ecosystem existing = repository.load(ecosystem.getCode());
    if (existing != null && !existing.getId().equals(ecosystem.getId())) {
      throw ApiError.TC120.toApiException(Map.of("code", ecosystem.getCode()));
    }
  }

  public Ecosystem load(Long id) {
    return repository.load(id);
  }

  public Ecosystem load(String code) {
    return repository.load(code);
  }

  public QueryResult<Ecosystem> query(EcosystemQueryParams params) {
    return repository.query(params);
  }
}
