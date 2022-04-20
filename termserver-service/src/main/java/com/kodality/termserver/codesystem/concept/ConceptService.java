package com.kodality.termserver.codesystem.concept;

import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.codesystem.entity.CodeSystemEntityService;
import com.kodality.termserver.codesystem.entity.CodeSystemEntityType;
import com.kodality.termserver.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.commons.model.model.QueryResult;
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
    concepts.getData().forEach(c -> decorate(c, params.getVersion()));
    return concepts;
  }

  public Optional<Concept> get(String codeSystem, String code) {
    return Optional.ofNullable(repository.load(codeSystem, code));
  }

  public Optional<Concept> get(String codeSystem, String version, String code) {
    return Optional.ofNullable(repository.load(codeSystem, code)).map(c -> decorate(c, version));
  }

  private Concept decorate(Concept concept, String version) {
    List<CodeSystemEntityVersion> versions = codeSystemEntityVersionService.query(new CodeSystemEntityVersionQueryParams()
        .setCodeSystemEntityId(concept.getId())
        .setCodeSystemVersion(version == null ? null : String.join("|", List.of(concept.getCodeSystem(), version)))).getData();
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
