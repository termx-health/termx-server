package com.kodality.termserver.ts.codesystem.concept;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.CodeSystemEntityType;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityService;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
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
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  public QueryResult<Concept> query(ConceptQueryParams params) {
    QueryResult<Concept> concepts = repository.query(params);
    concepts.getData().forEach(c -> decorate(c, params.getCodeSystemVersion()));
    return concepts;
  }

  public Optional<Concept> get(Long id) {
    return Optional.ofNullable(repository.load(id)).map(c -> decorate(c, null));
  }

  public Optional<Concept> get(String codeSystem, String code) {
    return Optional.ofNullable(repository.load(codeSystem, code)).map(c -> decorate(c, null));
  }

  public Optional<Concept> get(String codeSystem, String codeSystemVersion, String code) {
    return query(new ConceptQueryParams()
        .setCodeSystem(codeSystem)
        .setCodeSystemVersion(codeSystemVersion)
        .setCode(code)).findFirst().map(c -> decorate(c, codeSystemVersion));
  }

  private Concept decorate(Concept concept, String codeSystemVersion) {
    List<CodeSystemEntityVersion> versions = codeSystemEntityVersionService.query(new CodeSystemEntityVersionQueryParams()
        .setCodeSystemEntityId(concept.getId())
        .setCodeSystemVersion(codeSystemVersion)
        .setCodeSystem(concept.getCodeSystem())).getData();
    concept.setVersions(versions);
    return concept;
  }


  @Transactional
  public Concept save(Concept concept, String codeSystem) {
    concept.setType(CodeSystemEntityType.concept);
    concept.setCodeSystem(codeSystem);

    Optional<Concept> existingConcept = get(codeSystem, concept.getCode());
    if (existingConcept.isPresent()) {
      concept.setId(existingConcept.get().getId());
    } else {
      codeSystemEntityService.save(concept);
      repository.save(concept);
    }
    return concept;
  }

}
