package com.kodality.termserver.terminology.codesystem;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.UserPermissionService;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.terminology.codesystem.concept.ConceptService;
import com.kodality.termserver.terminology.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termserver.terminology.valueset.ValueSetService;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemTransactionRequest;
import com.kodality.termserver.ts.codesystem.CodeSystemVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termserver.ts.codesystem.ConceptQueryParams;
import com.kodality.termserver.ts.codesystem.EntityPropertyQueryParams;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleType;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import java.util.List;
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
  private final ValueSetService valueSetService;
  private final UserPermissionService userPermissionService;

  @Transactional
  public void save(CodeSystem codeSystem) {
    userPermissionService.checkPermitted(codeSystem.getId(), "CodeSystem", "edit");
    repository.save(codeSystem);
  }

  @Transactional
  public void save(CodeSystemTransactionRequest request) {
    CodeSystem codeSystem = request.getCodeSystem();
    userPermissionService.checkPermitted(codeSystem.getId(), "CodeSystem", "edit");
    repository.save(codeSystem);

    CodeSystemVersion version = request.getVersion();
    version.setCodeSystem(codeSystem.getId());
    version.setReleaseDate(version.getReleaseDate() == null ? LocalDate.now() : version.getReleaseDate());
    codeSystemVersionService.save(version);

    entityPropertyService.save(request.getProperties(), codeSystem.getId());

    if (request.getValueSet() != null) {
      request.getValueSet().getValueSet().setNames(request.getValueSet().getValueSet().getNames() == null ? codeSystem.getNames() : request.getValueSet().getValueSet().getNames());
      request.getValueSet().getVersion().setRuleSet(new ValueSetVersionRuleSet().setRules(List.of(new ValueSetVersionRule()
          .setCodeSystem(codeSystem.getId())
          .setCodeSystemVersionId(version.getId())
          .setType(ValueSetVersionRuleType.include)
      )));
      valueSetService.save(request.getValueSet());
    }
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

  @Transactional
  public void cancel(String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "publish");
    List<String> requiredCodeSystems = List.of("codesystem-content-mode", "concept-property-type", "contact-point-system", "contact-point-use",
        "filter-operator", "namingsystem-identifier-type", "namingsystem-type", "publication-status", "publisher", "snomed-ct", "v3-ietf3066");
    if (requiredCodeSystems.contains(codeSystem)) {
      throw ApiError.TE204.toApiException();
    }
    repository.cancel(codeSystem);
  }
}
