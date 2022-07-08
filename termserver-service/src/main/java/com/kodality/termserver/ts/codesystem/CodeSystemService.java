package com.kodality.termserver.ts.codesystem;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.codesystem.EntityPropertyQueryParams;
import com.kodality.termserver.ts.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.entityproperty.EntityPropertyService;
import jakarta.inject.Singleton;
import java.time.LocalDate;
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

  @Transactional
  public void save(CodeSystem codeSystem) {
    repository.save(codeSystem);
  }

  public Optional<CodeSystem> load(String codeSystem) {
    return load(codeSystem, false);
  }

  public Optional<CodeSystem> load(String codeSystem, boolean decorate) {
    return Optional.ofNullable(repository.load(codeSystem)).map(cs -> decorate ? decorate(cs) : cs);
  }

  public QueryResult<CodeSystem> query(CodeSystemQueryParams params) {
    QueryResult<CodeSystem> codeSystems = repository.query(params);
    decorateQueryResult(codeSystems, params);
    return codeSystems;
  }

  private CodeSystem decorate(CodeSystem codeSystem) {
    decorateConcepts(codeSystem, null, null);
    decorateVersions(codeSystem, null, null, null);
    decorateProperties(codeSystem);
    return codeSystem;
  }

  private void decorateQueryResult(QueryResult<CodeSystem> codeSystems, CodeSystemQueryParams params) {
    codeSystems.getData().forEach(codeSystem -> {
      if (params.isConceptsDecorated()) {
        decorateConcepts(codeSystem, params.getConceptCode(), params.getConceptCodeSystemVersion());
      }
      if (params.isVersionsDecorated()) {
        decorateVersions(codeSystem, params.getVersionVersion(), params.getVersionReleaseDateGe(), params.getVersionExpirationDateLe());
      }
      if (params.isPropertiesDecorated()) {
        decorateProperties(codeSystem);
      }
    });
  }

  private void decorateConcepts(CodeSystem codeSystem, String conceptCode, String conceptCodeSystemVersion) {
    ConceptQueryParams conceptParams = new ConceptQueryParams();
    conceptParams.setCodeSystem(codeSystem.getId());
    conceptParams.setCode(conceptCode);
    conceptParams.setCodeSystemVersion(conceptCodeSystemVersion);
    conceptParams.all();
    codeSystem.setConcepts(conceptService.query(conceptParams).getData());
  }

  private void decorateVersions(CodeSystem codeSystem, String versionVersion, LocalDate versionReleaseDateGe, LocalDate versionExpirationDateLe) {
    CodeSystemVersionQueryParams versionParams = new CodeSystemVersionQueryParams();
    versionParams.setCodeSystem(codeSystem.getId());
    versionParams.setVersion(versionVersion);
    versionParams.setReleaseDateGe(versionReleaseDateGe);
    versionParams.setExpirationDateLe(versionExpirationDateLe);
    versionParams.all();
    codeSystem.setVersions(codeSystemVersionService.query(versionParams).getData());
  }

  private void decorateProperties(CodeSystem codeSystem) {
    EntityPropertyQueryParams propertyParams = new EntityPropertyQueryParams();
    propertyParams.setCodeSystem(codeSystem.getId());
    propertyParams.all();
    codeSystem.setProperties(entityPropertyService.query(propertyParams).getData());
  }
}
