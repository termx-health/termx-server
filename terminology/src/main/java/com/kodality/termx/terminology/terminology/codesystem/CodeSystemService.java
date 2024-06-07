package com.kodality.termx.terminology.terminology.codesystem;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.fhir.BaseFhirMapper;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.terminology.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.terminology.terminology.valueset.ValueSetService;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemTransactionRequest;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleType;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class CodeSystemService {
  private final CodeSystemRepository repository;
  private final ConceptService conceptService;
  private final EntityPropertyService entityPropertyService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final ValueSetService valueSetService;

  @Transactional
  public void save(CodeSystem codeSystem) {
    validate(codeSystem);
    repository.save(codeSystem);
    entityPropertyService.save(codeSystem.getId(), codeSystem.getProperties());
  }

  @Transactional
  public void save(CodeSystemTransactionRequest request) {
    CodeSystem codeSystem = request.getCodeSystem();
    validate(codeSystem);
    repository.save(codeSystem);

    entityPropertyService.save(codeSystem.getId(), request.getProperties());

    CodeSystemVersion version = request.getVersion();
    if (version != null) {
      version.setCodeSystem(codeSystem.getId());
      version.setReleaseDate(version.getReleaseDate() == null ? LocalDate.now() : version.getReleaseDate());
      codeSystemVersionService.save(version);
    }

    if (request.getValueSet() != null) {
      CodeSystemVersion codeSystemVersion = version == null ? codeSystemVersionService.loadLastVersion(codeSystem.getId()) : version;
      request.getValueSet().getValueSet().setTitle(codeSystem.getTitle());
      request.getValueSet().getVersion().setAlgorithm(codeSystemVersion.getAlgorithm());
      request.getValueSet().getVersion().setPreferredLanguage(codeSystemVersion.getPreferredLanguage());
      request.getValueSet().getVersion().setSupportedLanguages(codeSystemVersion.getSupportedLanguages());
      request.getValueSet().getVersion().setRuleSet(new ValueSetVersionRuleSet().setRules(List.of(new ValueSetVersionRule()
          .setCodeSystem(codeSystem.getId())
          .setCodeSystemVersion(codeSystemVersion)
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
    decorateVersions(codeSystem, new CodeSystemQueryParams());
    return codeSystem;
  }

  private void decorateQueryResult(QueryResult<CodeSystem> codeSystems, CodeSystemQueryParams params) {
    codeSystems.getData().forEach(codeSystem -> {
      if (params.isConceptsDecorated()) {
        decorateConcepts(codeSystem, params);
      }
      if (params.isVersionsDecorated()) {
        decorateVersions(codeSystem, params);
      }
    });
  }

  private void decorateConcepts(CodeSystem codeSystem, CodeSystemQueryParams params) {
    ConceptQueryParams conceptParams = new ConceptQueryParams();
    conceptParams.setCodeSystem(codeSystem.getId());
    conceptParams.setCode(params.getConceptCode());
    conceptParams.setCodeSystemVersion(params.getConceptCodeSystemVersion());
    conceptParams.all();
    codeSystem.setConcepts(conceptService.query(conceptParams).getData());
  }

  private void decorateVersions(CodeSystem codeSystem, CodeSystemQueryParams params) {
    CodeSystemVersionQueryParams versionParams = new CodeSystemVersionQueryParams();
    versionParams.setCodeSystem(codeSystem.getId());
    versionParams.setVersion(params.getVersionVersion());
    versionParams.setReleaseDate(params.getVersionReleaseDate());
    versionParams.setReleaseDateGe(params.getVersionReleaseDateGe());
    versionParams.setExpirationDateLe(params.getVersionExpirationDateLe());
    versionParams.all();
    codeSystem.setVersions(codeSystemVersionService.query(versionParams).getData());
  }

  @Transactional
  public void cancel(String codeSystem) {
    List<String> requiredCodeSystems = List.of("codesystem-content-mode", "concept-property-type", "contact-point-system", "contact-point-use",
        "filter-operator", "namingsystem-identifier-type", "namingsystem-type", "publication-status", "publisher", "snomed-ct", "v3-ietf3066");
    if (requiredCodeSystems.contains(codeSystem)) {
      throw ApiError.TE204.toApiException();
    }
    repository.cancel(codeSystem);
  }

  @Transactional
  public void changeId(String currentId, String newId) {
    validateId(newId);
    repository.changeId(currentId, newId);
  }

  private void validateId(String id) {
    if (id.contains(BaseFhirMapper.SEPARATOR)) {
      throw ApiError.TE113.toApiException(Map.of("symbols", BaseFhirMapper.SEPARATOR));
    }
  }

  private void validate(CodeSystem codeSystem) {
    validateId(codeSystem.getId());

    if (CollectionUtils.isNotEmpty(codeSystem.getConfigurationAttributes())) {
      codeSystem.getConfigurationAttributes().forEach(c -> {
        if (StringUtils.isEmpty(c.getValue())) {
          throw ApiError.TE116.toApiException(Map.of("codeSystem", codeSystem.getId()));
        }
      });
    }
  }
}
