package com.kodality.termserver.codesystem;

import com.kodality.termserver.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termserver.commons.model.model.QueryResult;
import jakarta.inject.Singleton;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class CodeSystemService {
  private final CodeSystemRepository repository;
  private final EntityPropertyService entityPropertyService;
  private final CodeSystemVersionService codeSystemVersionService;

  public Optional<CodeSystem> get(String codeSystem) {
    return Optional.ofNullable(repository.load(codeSystem));
  }

  @Transactional
  public void save(CodeSystem codeSystem) {
    repository.create(codeSystem);
  }

  public QueryResult<CodeSystem> query(CodeSystemQueryParams params) {
    QueryResult<CodeSystem> codeSystems = repository.query(params);
    if (params.isDecorated()) {
      codeSystems.getData().forEach(this::decorate);
    }
    return codeSystems;
  }

  private void decorate(CodeSystem codeSystem) {
    codeSystem.setVersions(codeSystemVersionService.getVersions(codeSystem.getId()));
    codeSystem.setProperties(entityPropertyService.getProperties(codeSystem.getId()));
  }
}
