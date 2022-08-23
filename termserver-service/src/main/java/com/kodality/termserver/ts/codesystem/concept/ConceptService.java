package com.kodality.termserver.ts.codesystem.concept;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.codesystem.CodeSystemEntityType;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityService;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.valueset.ValueSetVersionRepository;
import com.kodality.termserver.valueset.ValueSetVersion;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ConceptService {
  private final ConceptRepository repository;
  private final CodeSystemEntityService codeSystemEntityService;
  private final ValueSetVersionRepository valueSetVersionRepository;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  private final UserPermissionService userPermissionService;

  @Transactional
  public Concept save(Concept concept, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    concept.setType(CodeSystemEntityType.concept);
    concept.setCodeSystem(codeSystem);

    Optional<Concept> existingConcept = load(codeSystem, concept.getCode());
    existingConcept.ifPresent(value -> {
      concept.setId(value.getId());
      concept.setCodeSystem(value.getCodeSystem());
    });
    codeSystemEntityService.save(concept);
    repository.save(concept);
    return concept;
  }

  @Transactional
  public Concept saveWithVersions(Concept concept, String codeSystem) {
    save(concept, codeSystem);
    if (CollectionUtils.isNotEmpty(concept.getVersions())) {
      concept.getVersions().stream().filter(v -> PublicationStatus.draft.equals(v.getStatus())).forEach(version -> {
        version.setCodeSystem(codeSystem);
        codeSystemEntityVersionService.save(version, concept.getId());
      });
    }
    return concept;
  }

  public QueryResult<Concept> query(ConceptQueryParams params) {
    prepareParams(params);
    QueryResult<Concept> concepts = repository.query(params);
    concepts.getData().forEach(c -> decorate(c, params.getCodeSystem(), params.getCodeSystemVersion()));
    return concepts;
  }

  public Optional<Concept> load(Long id) {
    return Optional.ofNullable(repository.load(id)).map(c -> decorate(c, null,null));
  }

  public Optional<Concept> load(String codeSystem, String code) {
    return Optional.ofNullable(repository.load(codeSystem, code)).map(c -> decorate(c, codeSystem, null));
  }

  public Optional<Concept> load(String codeSystem, String codeSystemVersion, String code) {
    return query(new ConceptQueryParams()
        .setCodeSystem(codeSystem)
        .setCodeSystemVersion(codeSystemVersion)
        .setCode(code)).findFirst().map(c -> decorate(c, codeSystem, codeSystemVersion));
  }

  private Concept decorate(Concept concept, String codeSystem, String codeSystemVersion) {
    List<CodeSystemEntityVersion> versions = codeSystemEntityVersionService.query(new CodeSystemEntityVersionQueryParams()
        .setCodeSystemEntityId(concept.getId())
        .setCodeSystemVersion(codeSystemVersion)
        .setCodeSystem(codeSystem)).getData();
    concept.setVersions(versions);
    return concept;
  }

  private void prepareParams(ConceptQueryParams params) {
    if (params.getValueSet() != null && params.getValueSetVersion() == null) {
      ValueSetVersion valueSetVersion = valueSetVersionRepository.loadLastVersion(params.getValueSet(), PublicationStatus.active);
      params.setValueSetVersionId(valueSetVersion == null ? null : valueSetVersion.getId());
    }
    if (params.getValueSet() != null && params.getValueSetVersion() != null) {
      params.setValueSetVersionId(valueSetVersionRepository.load(params.getValueSet(), params.getValueSetVersion()).getId());
    }
  }

}
