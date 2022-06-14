package com.kodality.termserver.ts.codesystem;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.ts.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.entityproperty.EntityPropertyService;
import jakarta.inject.Singleton;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class CodeSystemService {
  private final CodeSystemRepository repository;
  private final ConceptService conceptService;
  private final EntityPropertyService entityPropertyService;
  private final CodeSystemVersionService codeSystemVersionService;

  public Optional<CodeSystem> get(String codeSystem) {
    Optional<CodeSystem> cs = Optional.ofNullable(repository.load(codeSystem));
    cs.ifPresent(this::decorate);
    return cs;
  }

  @Transactional
  public void save(CodeSystem codeSystem) {
    repository.save(codeSystem);
    entityPropertyService.save(codeSystem.getProperties(), codeSystem.getId());
  }

  public QueryResult<CodeSystem> query(CodeSystemQueryParams params) {
    QueryResult<CodeSystem> codeSystems = repository.query(params);
    decorateQueryResult(codeSystems, params);
    return codeSystems;
  }

  private void decorateQueryResult(QueryResult<CodeSystem> codeSystems, CodeSystemQueryParams params) {
    codeSystems.getData().forEach(codeSystem -> {
      if (params.isConceptsDecorated()) {
        ConceptQueryParams conceptParams = new ConceptQueryParams();
        conceptParams.setCodeSystem(codeSystem.getId());
        conceptParams.setCode(params.getConceptCode());
        conceptParams.setCodeSystemVersion(params.getConceptCodeSystemVersion());
        conceptParams.all();
        codeSystem.setConcepts(conceptService.query(conceptParams).getData());
      }
      if (params.isVersionsDecorated()) {
        CodeSystemVersionQueryParams versionParams = new CodeSystemVersionQueryParams();
        versionParams.setCodeSystem(codeSystem.getId());
        versionParams.setVersion(params.getVersionVersion());
        versionParams.setReleaseDateGe(params.getVersionReleaseDateGe());
        versionParams.setExpirationDateLe(params.getVersionExpirationDateLe());
        versionParams.all();
        codeSystem.setVersions(codeSystemVersionService.query(versionParams).getData());
      }
      if (params.isPropertiesDecorated()) {
        codeSystem.setProperties(entityPropertyService.getProperties(codeSystem.getId()));
      }
    });
  }

  private void decorate(CodeSystem codeSystem) {
    codeSystem.setProperties(entityPropertyService.getProperties(codeSystem.getId()));
  }
}
